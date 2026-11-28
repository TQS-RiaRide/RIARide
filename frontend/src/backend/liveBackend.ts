import type { Backend } from './Backend';
import { requestJson, type TokenSource } from './http';
import {
  list,
  parseHub,
  parseHubVehicle,
  parsePass,
  parseStay,
  parseTicket,
  parseTrip,
  parseVehicle,
} from './parse';
import { upsertBy, type RegistryStore } from './registry';
import type { FleetVehicle, Hub, PassPrice, Trip } from './types';

// The base prices seeded by the backend (V4 migration, BR3). There is no endpoint to read them
// yet (RIA-121); the price actually charged comes back in the purchase response.
export const BASE_PASS_PRICES: readonly PassPrice[] = [
  { passType: 'ONE_DAY', priceCents: 1000, depositCents: 5000, currency: 'EUR' },
  { passType: 'THREE_DAY', priceCents: 2500, depositCents: 5000, currency: 'EUR' },
  { passType: 'STAY', priceCents: 4000, depositCents: 5000, currency: 'EUR' },
];

const ACTIVE_TRIP = new Set(['STARTING', 'IN_USE']);

export interface LiveBackendOptions {
  getToken: TokenSource;
  registry: RegistryStore;
  fetchImpl?: typeof fetch;
}

export function createLiveBackend({ getToken, registry, fetchImpl }: LiveBackendOptions): Backend {
  const call = (path: string, options?: Parameters<typeof requestJson>[2]) =>
    requestJson(getToken, path, options, fetchImpl);

  const hubVehicles = async (hubId: string) =>
    list(
      await call(`/api/v1/hubs/${encodeURIComponent(hubId)}/vehicles`),
      parseHubVehicle,
      'vehicles',
    );

  // Occupancy from the bikes each hub lists: docked bikes take a slot, bikes on a trip do not.
  const withOccupancy = async (hub: Hub): Promise<Hub> => {
    try {
      const vehicles = await hubVehicles(hub.id);
      const docked = vehicles.filter((v) => v.status !== 'IN_USE' && v.status !== 'RETIRED').length;
      return { ...hub, dockedBikes: docked, freeSlots: Math.max(hub.capacity - docked, 0) };
    } catch {
      return hub;
    }
  };

  const trip = async (id: string): Promise<Trip> =>
    parseTrip(await call(`/api/v1/trips/${encodeURIComponent(id)}`));

  return {
    mode: 'live',

    async signUpGuest(request) {
      await call('/api/v1/guests', { method: 'POST', body: request, anonymous: true });
    },

    async passPrices() {
      return [...BASE_PASS_PRICES];
    },

    async currentPass() {
      return registry.read().pass;
    },

    async buyPass(passType, paymentMethod, idempotencyKey) {
      const pass = parsePass(
        await call('/api/v1/passes', {
          method: 'POST',
          body: { passType, paymentMethod },
          headers: { 'Idempotency-Key': idempotencyKey },
        }),
      );
      registry.update((current) => ({ ...current, pass }));
      return pass;
    },

    async hubs() {
      return Promise.all(registry.read().hubs.map(withOccupancy));
    },

    hubVehicles,

    async startTrip(vehicleCode) {
      const started = parseTrip(
        await call('/api/v1/trips', { method: 'POST', body: { vehicleCode } }),
      );
      registry.update((current) => ({
        ...current,
        tripIds: [started.id, ...current.tripIds.filter((id) => id !== started.id)],
      }));
      return started;
    },

    trip,

    async endTrip(id, hubId) {
      return parseTrip(
        await call(`/api/v1/trips/${encodeURIComponent(id)}/end`, {
          method: 'POST',
          body: { hubId },
        }),
      );
    },

    async myTrips() {
      const results = await Promise.allSettled(registry.read().tripIds.map(trip));
      return results.flatMap((result) => (result.status === 'fulfilled' ? [result.value] : []));
    },

    async fleet() {
      const { hubs, vehicles: known } = registry.read();
      const perHub = await Promise.all(
        hubs.map(async (hub) => {
          try {
            return (await hubVehicles(hub.id)).map((vehicle): FleetVehicle => ({
              ...vehicle,
              id: known.find((k) => k.code === vehicle.code)?.id ?? null,
              hubId: hub.id,
              hubName: hub.name,
            }));
          } catch {
            return [];
          }
        }),
      );
      return perHub.flat();
    },

    async registerHub(request) {
      const hub = parseHub(await call('/api/v1/hubs', { method: 'POST', body: request }));
      registry.update((current) => ({
        ...current,
        hubs: upsertBy(current.hubs, hub, (a, b) => a.id === b.id),
      }));
      return hub;
    },

    async registerVehicle(hubId, code) {
      const vehicle = parseVehicle(
        await call(`/api/v1/hubs/${encodeURIComponent(hubId)}/vehicles`, {
          method: 'POST',
          body: { code, type: 'E_BIKE' },
        }),
      );
      registry.update((current) => ({
        ...current,
        vehicles: upsertBy(
          current.vehicles,
          { id: vehicle.id, code: vehicle.code, hubId: vehicle.hubId },
          (a, b) => a.id === b.id,
        ),
      }));
      return vehicle;
    },

    async changeVehicleStatus(vehicleId, status) {
      return parseVehicle(
        await call(`/api/v1/vehicles/${encodeURIComponent(vehicleId)}/status`, {
          method: 'PUT',
          body: { status },
        }),
      );
    },

    async tickets() {
      return registry.read().tickets;
    },

    async openTicket(request) {
      const ticket = parseTicket(await call('/api/v1/tickets', { method: 'POST', body: request }));
      registry.update((current) => ({
        ...current,
        tickets: upsertBy(current.tickets, ticket, (a, b) => a.id === b.id),
      }));
      return ticket;
    },

    async stays() {
      return registry.read().stays;
    },

    async registerStay(request) {
      const stay = parseStay(await call('/api/v1/stays', { method: 'POST', body: request }));
      registry.update((current) => ({
        ...current,
        stays: upsertBy(current.stays, stay, (a, b) => a.id === b.id),
      }));
      return stay;
    },

    async kpis() {
      return null;
    },
  };
}

export function isActiveTrip(trip: Trip): boolean {
  return ACTIVE_TRIP.has(trip.status);
}
