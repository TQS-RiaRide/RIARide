import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useBackend } from './BackendContext';
import type {
  FleetVehicle,
  OpenTicketRequest,
  PassType,
  RegisterHubRequest,
  RegisterStayRequest,
  Trip,
  VehicleStatus,
} from './types';

// Server state lives in React Query only: stale-while-revalidate (cached data shows at once and
// refreshes in the background), and mutations update or invalidate the queries they touch.

export const keys = {
  prices: ['passes', 'prices'] as const,
  pass: ['passes', 'current'] as const,
  hubs: ['hubs'] as const,
  hubVehicles: (hubId: string) => ['hubs', hubId, 'vehicles'] as const,
  trips: ['trips'] as const,
  trip: (id: string) => ['trips', id] as const,
  fleet: ['fleet'] as const,
  tickets: ['tickets'] as const,
  stays: ['stays'] as const,
  kpis: ['kpis'] as const,
};

/** Telemetry changes every few seconds; lists that show it refresh on this beat while visible. */
export const LIVE_REFRESH_MS = 15_000;
const TRIP_POLL_MS = 1_000;

export function usePassPrices() {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.prices,
    queryFn: () => backend.passPrices(),
    staleTime: Infinity,
  });
}

export function useCurrentPass() {
  const backend = useBackend();
  return useQuery({ queryKey: keys.pass, queryFn: () => backend.currentPass() });
}

export function useHubs(enabled = true) {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.hubs,
    queryFn: () => backend.hubs(),
    refetchInterval: LIVE_REFRESH_MS,
    enabled,
  });
}

export function useHubVehicles(hubId: string | undefined) {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.hubVehicles(hubId ?? ''),
    queryFn: () => backend.hubVehicles(hubId ?? ''),
    enabled: hubId !== undefined,
    refetchInterval: LIVE_REFRESH_MS,
  });
}

export function useMyTrips() {
  const backend = useBackend();
  return useQuery({ queryKey: keys.trips, queryFn: () => backend.myTrips() });
}

/** Polls every second until telemetry confirms the unlock (BR13), then while the trip runs. */
export function useTrip(id: string | undefined) {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.trip(id ?? ''),
    queryFn: () => backend.trip(id ?? ''),
    enabled: id !== undefined,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'STARTING') return TRIP_POLL_MS;
      if (status === 'IN_USE') return LIVE_REFRESH_MS;
      return false;
    },
  });
}

export function useFleet(enabled = true) {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.fleet,
    queryFn: () => backend.fleet(),
    refetchInterval: LIVE_REFRESH_MS,
    enabled,
  });
}

export function useTickets() {
  const backend = useBackend();
  return useQuery({
    queryKey: keys.tickets,
    queryFn: () => backend.tickets(),
    refetchInterval: LIVE_REFRESH_MS,
  });
}

export function useStays() {
  const backend = useBackend();
  return useQuery({ queryKey: keys.stays, queryFn: () => backend.stays() });
}

export function useKpis() {
  const backend = useBackend();
  return useQuery({ queryKey: keys.kpis, queryFn: () => backend.kpis() });
}

export interface BuyPassInput {
  passType: PassType;
  paymentMethod: string;
  idempotencyKey: string;
}

export function useBuyPass() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ passType, paymentMethod, idempotencyKey }: BuyPassInput) =>
      backend.buyPass(passType, paymentMethod, idempotencyKey),
    onSuccess: (pass) => client.setQueryData(keys.pass, pass),
  });
}

export function useStartTrip() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (vehicleCode: string) => backend.startTrip(vehicleCode),
    onSuccess: (trip) => {
      client.setQueryData(keys.trip(trip.id), trip);
      void client.invalidateQueries({ queryKey: keys.trips });
      void client.invalidateQueries({ queryKey: keys.hubs });
      void client.invalidateQueries({ queryKey: keys.pass });
    },
  });
}

export function useEndTrip() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ tripId, hubId }: { tripId: string; hubId: string }) =>
      backend.endTrip(tripId, hubId),
    onSuccess: (trip: Trip) => {
      client.setQueryData(keys.trip(trip.id), trip);
      void client.invalidateQueries({ queryKey: keys.trips });
      void client.invalidateQueries({ queryKey: keys.hubs });
    },
  });
}

export function useRegisterHub() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: RegisterHubRequest) => backend.registerHub(request),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.hubs }),
  });
}

export function useRegisterVehicle() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ hubId, code }: { hubId: string; code: string }) =>
      backend.registerVehicle(hubId, code),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.fleet });
      void client.invalidateQueries({ queryKey: keys.hubs });
    },
  });
}

interface StatusChange {
  vehicleId: string;
  status: VehicleStatus;
}

/**
 * Optimistic: the table shows the new status at once. If the backend refuses, the snapshot is
 * put back and the caller shows why.
 */
export function useChangeVehicleStatus() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ vehicleId, status }: StatusChange) =>
      backend.changeVehicleStatus(vehicleId, status),
    onMutate: async ({ vehicleId, status }) => {
      await client.cancelQueries({ queryKey: keys.fleet });
      const previous = client.getQueryData<FleetVehicle[]>(keys.fleet);
      client.setQueryData<FleetVehicle[]>(keys.fleet, (current) =>
        current?.map((v) => (v.id === vehicleId ? { ...v, status, bookable: false } : v)),
      );
      return { previous };
    },
    onError: (_error, _change, context) => {
      if (context?.previous) client.setQueryData(keys.fleet, context.previous);
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: keys.fleet });
      void client.invalidateQueries({ queryKey: keys.hubs });
    },
  });
}

export function useOpenTicket() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: OpenTicketRequest) => backend.openTicket(request),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.tickets });
      void client.invalidateQueries({ queryKey: keys.fleet });
    },
  });
}

export function useRegisterStay() {
  const backend = useBackend();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: RegisterStayRequest) => backend.registerStay(request),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.stays }),
  });
}
