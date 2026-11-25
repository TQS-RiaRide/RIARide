import type { Backend } from './Backend';
import {
  DEMO_HUBS,
  DEMO_KPIS,
  HOTELS,
  demoStays,
  demoTickets,
  demoTrips,
  demoVehicles,
} from './demoData';
import { BASE_PASS_PRICES } from './liveBackend';
import { ProblemError } from './problem';
import {
  MIN_BOOKABLE_BATTERY,
  type FleetVehicle,
  type Hub,
  type HubVehicle,
  type Pass,
  type Stay,
  type Ticket,
  type Trip,
  type Vehicle,
} from './types';

// An in-memory Vila Ria that follows the business rules closely enough to try every screen with
// no backend: BR6 one bike at a time, BR10 free slot to end, BR12 20 % battery, idempotent purchase.

const UNLOCK_SECONDS = 2;
const SLA_HOURS = 24;

export interface DemoBackendOptions {
  /** Delay before each answer, so loading states can be seen (0 in tests). */
  latencyMs?: number;
  now?: () => number;
}

interface DemoState {
  hubs: Hub[];
  vehicles: Vehicle[];
  pass: Pass | null;
  trips: Trip[];
  tickets: Ticket[];
  stays: Stay[];
  purchases: Map<string, Pass>;
}

function problem(
  status: number,
  title: string,
  detail: string,
  fields: string[] = [],
): ProblemError {
  return new ProblemError(
    status,
    title,
    detail,
    fields.map((name) => ({ name, reason: null })),
  );
}

function stayCode(hotelName: string, random: () => number): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const tail = Array.from(
    { length: 4 },
    () => alphabet[Math.floor(random() * alphabet.length)],
  ).join('');
  const prefix = hotelName
    .replace(/^Marés\s+/u, '')
    .toUpperCase()
    .normalize('NFD')
    .replace(/[^A-Z]/g, '');
  return `${prefix}-${tail}`;
}

// Stripe's test card that is always declined; the demo answers like Stripe test mode would.
const DECLINED_TEST_CARD = 'pm_card_chargeDeclined';

export function createDemoBackend({
  latencyMs = 380,
  now = Date.now,
}: DemoBackendOptions = {}): Backend {
  const state: DemoState = {
    hubs: DEMO_HUBS.map((hub) => ({ ...hub })),
    vehicles: demoVehicles(),
    pass: null,
    trips: demoTrips(now()),
    tickets: demoTickets(now()),
    stays: demoStays(now()),
    purchases: new Map(),
  };

  const settle = <T>(value: () => T): Promise<T> =>
    new Promise((resolve, reject) => {
      const run = () => {
        try {
          resolve(value());
        } catch (error) {
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      };
      if (latencyMs <= 0) run();
      else setTimeout(run, latencyMs * (0.6 + Math.random() * 0.8));
    });

  const docked = (hubId: string) =>
    state.vehicles.filter(
      (v) => v.hubId === hubId && v.status !== 'IN_USE' && v.status !== 'RETIRED',
    );

  const hubWithOccupancy = (hub: Hub): Hub => {
    const count = docked(hub.id).length;
    return { ...hub, dockedBikes: count, freeSlots: Math.max(hub.capacity - count, 0) };
  };

  const bookable = (vehicle: Vehicle) =>
    vehicle.status === 'AVAILABLE' && (vehicle.batteryPct ?? 0) >= MIN_BOOKABLE_BATTERY;

  const toHubVehicle = (vehicle: Vehicle): HubVehicle => ({
    code: vehicle.code,
    type: vehicle.type,
    status: vehicle.status,
    batteryPct: vehicle.batteryPct,
    bookable: bookable(vehicle),
  });

  const replaceVehicle = (next: Vehicle) => {
    state.vehicles = state.vehicles.map((v) => (v.id === next.id ? next : v));
  };

  // A trip is confirmed by telemetry a moment after it is requested.
  const withTelemetry = (trip: Trip): Trip => {
    if (trip.status !== 'STARTING') return trip;
    const elapsed = (now() - Date.parse(trip.requestedAt)) / 1000;
    if (elapsed < UNLOCK_SECONDS) return trip;
    const started: Trip = {
      ...trip,
      status: 'IN_USE',
      startedAt: new Date(Date.parse(trip.requestedAt) + UNLOCK_SECONDS * 1000).toISOString(),
    };
    state.trips = state.trips.map((t) => (t.id === trip.id ? started : t));
    if (state.pass?.status === 'INACTIVE') state.pass = { ...state.pass, status: 'ACTIVE' };
    return started;
  };

  const findTrip = (id: string) => {
    const trip = state.trips.find((t) => t.id === id);
    if (!trip) throw problem(404, 'Trip not found', 'No trip has this id.');
    return withTelemetry(trip);
  };

  return {
    mode: 'demo',

    signUpGuest(request) {
      return settle(() => {
        const known = state.stays.some((s) => s.stayCode === request.stayCode.trim().toUpperCase());
        if (!known)
          throw problem(422, 'Invalid stay code', 'No active stay has this code.', ['stayCode']);
      });
    },

    passPrices: () => settle(() => [...BASE_PASS_PRICES]),

    currentPass: () => settle(() => state.pass),

    buyPass(passType, paymentMethod, idempotencyKey) {
      return settle(() => {
        const earlier = state.purchases.get(idempotencyKey);
        if (earlier) return earlier;
        if (paymentMethod === DECLINED_TEST_CARD) {
          throw problem(402, 'Payment declined', 'The card was declined. Nothing was charged.');
        }
        if (state.pass && state.pass.status !== 'CLOSED') {
          throw problem(409, 'Pass already open', 'You already have a pass that has not ended.');
        }
        const price = BASE_PASS_PRICES.find((p) => p.passType === passType) ?? BASE_PASS_PRICES[0];
        const pass: Pass = {
          id: crypto.randomUUID(),
          passType,
          status: 'INACTIVE',
          priceCents: price.priceCents,
          depositCents: price.depositCents,
          currency: price.currency,
        };
        state.purchases.set(idempotencyKey, pass);
        state.pass = pass;
        return pass;
      });
    },

    hubs: () => settle(() => state.hubs.map(hubWithOccupancy)),

    hubVehicles(hubId) {
      return settle(() => {
        if (!state.hubs.some((h) => h.id === hubId))
          throw problem(404, 'Hub not found', 'No hub has this id.');
        return state.vehicles
          .filter((v) => v.hubId === hubId && v.status !== 'RETIRED')
          .map(toHubVehicle)
          .sort(
            (a, b) =>
              Number(b.bookable) - Number(a.bookable) || (b.batteryPct ?? 0) - (a.batteryPct ?? 0),
          );
      });
    },

    startTrip(vehicleCode) {
      return settle(() => {
        if (!state.pass || state.pass.status === 'CLOSED') {
          throw problem(403, 'No valid pass', 'Buy a pass before starting a trip.');
        }
        if (
          state.trips.some((t) => {
            const status = withTelemetry(t).status;
            return status === 'STARTING' || status === 'IN_USE';
          })
        ) {
          throw problem(409, 'Trip already in progress', 'Your pass already has a bike in use.');
        }
        const vehicle = state.vehicles.find((v) => v.code === vehicleCode.trim().toUpperCase());
        if (!vehicle) throw problem(404, 'Bike not found', 'No bike has this code.');
        if (!bookable(vehicle))
          throw problem(409, 'Bike not available', 'This bike cannot be used now.');
        replaceVehicle({ ...vehicle, status: 'IN_USE', hubId: null });
        const trip: Trip = {
          id: crypto.randomUUID(),
          status: 'STARTING',
          vehicleCode: vehicle.code,
          requestedAt: new Date(now()).toISOString(),
          startedAt: null,
          endedAt: null,
          endHubId: null,
        };
        state.trips = [trip, ...state.trips];
        return trip;
      });
    },

    trip: (id) => settle(() => findTrip(id)),

    endTrip(id, hubId) {
      return settle(() => {
        const trip = findTrip(id);
        if (trip.status !== 'IN_USE')
          throw problem(409, 'Trip not in progress', 'This trip is not in progress.');
        const hub = state.hubs.find((h) => h.id === hubId);
        if (!hub) throw problem(404, 'Hub not found', 'No hub has this id.');
        if (hubWithOccupancy(hub).freeSlots === 0) {
          throw problem(409, 'Hub full', 'This hub has no free slot. Try one of the nearest hubs.');
        }
        const vehicle = state.vehicles.find((v) => v.code === trip.vehicleCode);
        if (vehicle) replaceVehicle({ ...vehicle, status: 'AVAILABLE', hubId });
        const ended: Trip = {
          ...trip,
          status: 'ENDED',
          endedAt: new Date(now()).toISOString(),
          endHubId: hubId,
        };
        state.trips = state.trips.map((t) => (t.id === id ? ended : t));
        return ended;
      });
    },

    myTrips: () => settle(() => state.trips.map(withTelemetry)),

    fleet() {
      return settle(() =>
        state.vehicles.map((v): FleetVehicle => ({
          ...toHubVehicle(v),
          id: v.id,
          hubId: v.hubId,
          hubName: state.hubs.find((h) => h.id === v.hubId)?.name ?? null,
        })),
      );
    },

    registerHub(request) {
      return settle(() => {
        const name = request.name.trim();
        if (!name) throw problem(400, 'Invalid hub', 'The hub needs a name.', ['name']);
        if (!Number.isInteger(request.capacity) || request.capacity < 1) {
          throw problem(400, 'Invalid hub', 'The capacity must be at least 1.', ['capacity']);
        }
        if (state.hubs.some((h) => h.name.toLowerCase() === name.toLowerCase())) {
          throw problem(409, 'Hub name already in use', 'Another hub has this name.');
        }
        const hub: Hub = {
          ...request,
          name,
          id: crypto.randomUUID(),
          dockedBikes: 0,
          freeSlots: request.capacity,
        };
        state.hubs = [...state.hubs, hub];
        return hub;
      });
    },

    registerVehicle(hubId, code) {
      return settle(() => {
        const normalized = code.trim().toUpperCase();
        if (!/^[A-Z0-9-]{2,32}$/.test(normalized)) {
          throw problem(400, 'Invalid vehicle', 'The vehicle details are not valid.', ['code']);
        }
        const hub = state.hubs.find((h) => h.id === hubId);
        if (!hub) throw problem(404, 'Hub not found', 'No hub has this id.');
        if (state.vehicles.some((v) => v.code === normalized)) {
          throw problem(409, 'Vehicle code already in use', 'Another bike has this code.', [
            'code',
          ]);
        }
        if (hubWithOccupancy(hub).freeSlots === 0)
          throw problem(409, 'Hub full', 'This hub has no free slot.');
        const vehicle: Vehicle = {
          id: crypto.randomUUID(),
          code: normalized,
          type: 'E_BIKE',
          status: 'AVAILABLE',
          hubId,
          batteryPct: 100,
        };
        state.vehicles = [...state.vehicles, vehicle];
        return vehicle;
      });
    },

    changeVehicleStatus(vehicleId, status) {
      return settle(() => {
        const vehicle = state.vehicles.find((v) => v.id === vehicleId);
        if (!vehicle) throw problem(404, 'Vehicle not found', 'No bike has this id.');
        if (vehicle.status === 'RETIRED' || (vehicle.status === 'IN_USE' && status === 'RETIRED')) {
          throw problem(409, 'Status change refused', 'The lifecycle does not allow this change.');
        }
        const next: Vehicle = {
          ...vehicle,
          status,
          hubId: status === 'RETIRED' ? null : vehicle.hubId,
        };
        replaceVehicle(next);
        return next;
      });
    },

    tickets: () =>
      settle(() => [...state.tickets].sort((a, b) => a.slaDueAt.localeCompare(b.slaDueAt))),

    openTicket(request) {
      return settle(() => {
        const fields = [
          ...(request.vehicleCode.trim() ? [] : ['vehicleCode']),
          ...(request.description.trim() && request.description.length <= 500
            ? []
            : ['description']),
        ];
        if (fields.length > 0)
          throw problem(400, 'Invalid ticket', 'The ticket has an invalid field.', fields);
        const vehicle = state.vehicles.find(
          (v) => v.code === request.vehicleCode.trim().toUpperCase(),
        );
        if (!vehicle) throw problem(404, 'Vehicle not found', 'No bike has this code.');
        if (vehicle.status === 'RETIRED')
          throw problem(409, 'Ticket refused', 'This bike is retired.');
        const pending = vehicle.status === 'IN_USE';
        if (!pending) replaceVehicle({ ...vehicle, status: 'MAINTENANCE' });
        const opened = now();
        const ticket: Ticket = {
          id: crypto.randomUUID(),
          vehicleCode: vehicle.code,
          status: 'OPEN',
          description: request.description.trim(),
          openedAt: new Date(opened).toISOString(),
          slaDueAt: new Date(opened + SLA_HOURS * 3_600_000).toISOString(),
          vehicleStatus: pending ? 'IN_USE' : 'MAINTENANCE',
          maintenancePending: pending,
        };
        state.tickets = [...state.tickets, ticket];
        return ticket;
      });
    },

    stays: () => settle(() => [...state.stays]),

    registerStay(request) {
      return settle(() => {
        const hotel = HOTELS.find((h) => h.id === request.hotelId);
        if (!hotel) throw problem(404, 'Hotel not found', 'No hotel of the group has this id.');
        if (!request.guestName.trim())
          throw problem(400, 'Invalid stay', 'The guest name is required.', ['guestName']);
        if (request.checkOut <= request.checkIn) {
          throw problem(422, 'Invalid stay dates', 'The check-out must be after the check-in.', [
            'checkOut',
          ]);
        }
        const stay: Stay = {
          id: crypto.randomUUID(),
          hotelId: hotel.id,
          guestName: request.guestName.trim(),
          stayCode: stayCode(hotel.name, Math.random),
          checkIn: request.checkIn,
          checkOut: request.checkOut,
          status: 'ACTIVE',
        };
        state.stays = [stay, ...state.stays];
        return stay;
      });
    },

    kpis: () => settle(() => DEMO_KPIS),
  };
}
