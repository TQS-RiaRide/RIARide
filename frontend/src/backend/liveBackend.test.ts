import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from './Backend';
import { BASE_PASS_PRICES, createLiveBackend, isActiveTrip } from './liveBackend';
import { InvalidResponseError } from './parse';
import { NetworkError, ProblemError } from './problem';
import { createRegistryStore, type RegistryStore } from './registry';
import type { Hub, Trip } from './types';

type Route = (init: RequestInit) => Response;

const HUB: Hub = {
  id: 'h-farol',
  name: 'Farol da Barra',
  kind: 'TOURIST_SPOT',
  hotel: null,
  latitude: 40.64,
  longitude: -8.74,
  capacity: 3,
  dockedBikes: 0,
  freeSlots: 3,
};

const PASS = {
  id: 'p-1',
  passType: 'ONE_DAY',
  status: 'INACTIVE',
  priceCents: 1000,
  depositCents: 5000,
  currency: 'EUR',
};

function trip(id: string, status: Trip['status'] = 'STARTING') {
  return { id, status, vehicleCode: 'EB-101', requestedAt: '2026-11-25T10:00:00Z' };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function problem(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

let routes: Map<string, Route>;
let fetchImpl: ReturnType<typeof vi.fn<typeof fetch>>;
let registry: RegistryStore;
let backend: Backend;
const getToken = vi.fn(() => Promise.resolve('guest-token'));

function route(method: string, path: string, answer: Route | Response) {
  routes.set(`${method} ${path}`, typeof answer === 'function' ? answer : () => answer.clone());
}

function requestTo(method: string, path: string): RequestInit {
  const call = fetchImpl.mock.calls.find(
    ([url, init]) => url === path && (init?.method ?? 'GET') === method,
  );
  if (!call?.[1]) throw new Error(`No ${method} ${path} was sent`);
  return call[1];
}

beforeEach(() => {
  routes = new Map();
  fetchImpl = vi.fn<typeof fetch>((input, init) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    const answer = routes.get(key);
    return Promise.resolve(
      answer ? answer(init ?? {}) : problem(404, { title: `No route for ${key}` }),
    );
  });
  registry = createRegistryStore(() => 'guest-a');
  backend = createLiveBackend({ getToken, registry, fetchImpl });
});

afterEach(() => {
  window.localStorage.clear();
  getToken.mockClear();
});

describe('live backend: requests', () => {
  it('sends the bearer token on authenticated calls', async () => {
    route('GET', '/api/v1/trips/trip-1', json(trip('trip-1')));

    await backend.trip('trip-1');

    expect(requestTo('GET', '/api/v1/trips/trip-1').headers).toMatchObject({
      Authorization: 'Bearer guest-token',
    });
  });

  it('signs up a guest anonymously', async () => {
    route('POST', '/api/v1/guests', new Response(null, { status: 201 }));

    await backend.signUpGuest({
      email: 'ana@example.test',
      password: 'synthetic-only',
      stayCode: 'PRAIA-7K4Q',
      locale: 'pt',
    });

    expect(getToken).not.toHaveBeenCalled();
    expect(requestTo('POST', '/api/v1/guests').headers).not.toHaveProperty('Authorization');
  });

  it('encodes ids in the path', async () => {
    route('GET', '/api/v1/trips/a%2Fb', json(trip('a/b')));

    expect((await backend.trip('a/b')).id).toBe('a/b');
  });

  it('reports the mode and has no KPIs yet', async () => {
    expect(backend.mode).toBe('live');
    expect(await backend.kpis()).toBeNull();
  });

  it('lists the seeded base prices as a copy', async () => {
    const prices = await backend.passPrices();

    expect(prices).toEqual(BASE_PASS_PRICES);
    expect(prices).not.toBe(BASE_PASS_PRICES);
  });
});

describe('live backend: passes', () => {
  it('buys a pass with an Idempotency-Key header and remembers it', async () => {
    route('POST', '/api/v1/passes', json(PASS, 201));

    const pass = await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-123');

    const request = requestTo('POST', '/api/v1/passes');
    expect(request.headers).toMatchObject({ 'Idempotency-Key': 'key-123' });
    expect(JSON.parse(String(request.body))).toEqual({
      passType: 'ONE_DAY',
      paymentMethod: 'pm_card_visa',
    });
    expect(pass).toEqual(PASS);
    expect(await backend.currentPass()).toEqual(PASS);
  });

  it('has no current pass before buying one', async () => {
    expect(await backend.currentPass()).toBeNull();
  });

  it('turns a refused payment into a ProblemError with its fields', async () => {
    route(
      'POST',
      '/api/v1/passes',
      problem(402, {
        title: 'Payment refused',
        detail: 'The card was declined.',
        'invalid-params': [{ name: 'paymentMethod', reason: 'declined' }],
      }),
    );

    const error = await backend.buyPass('ONE_DAY', 'pm_declined', 'k').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ProblemError);
    expect(error).toMatchObject({
      status: 402,
      title: 'Payment refused',
      detail: 'The card was declined.',
      invalidParams: [{ name: 'paymentMethod', reason: 'declined' }],
      fields: ['paymentMethod'],
    });
    expect(await backend.currentPass()).toBeNull();
  });

  it('rejects a malformed pass and remembers nothing', async () => {
    route('POST', '/api/v1/passes', json({ ...PASS, status: 'WHATEVER' }, 201));

    await expect(backend.buyPass('ONE_DAY', 'pm', 'k')).rejects.toBeInstanceOf(
      InvalidResponseError,
    );
    expect(await backend.currentPass()).toBeNull();
  });

  it('turns an unreachable backend into a NetworkError', async () => {
    fetchImpl.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(backend.buyPass('ONE_DAY', 'pm', 'k')).rejects.toBeInstanceOf(NetworkError);
  });
});

describe('live backend: trips', () => {
  it('starts a trip and keeps its id first in the trip list without duplicates', async () => {
    registry.update((current) => ({ ...current, tripIds: ['trip-old', 'trip-new'] }));
    route('POST', '/api/v1/trips', json(trip('trip-new'), 201));

    const started = await backend.startTrip('EB-101');

    expect(started.status).toBe('STARTING');
    expect(JSON.parse(String(requestTo('POST', '/api/v1/trips').body))).toEqual({
      vehicleCode: 'EB-101',
    });
    expect(registry.read().tripIds).toEqual(['trip-new', 'trip-old']);
  });

  it('ends a trip at a hub', async () => {
    route('POST', '/api/v1/trips/trip-1/end', json({ ...trip('trip-1', 'ENDED'), endHubId: 'h' }));

    const ended = await backend.endTrip('trip-1', 'h');

    expect(ended).toMatchObject({ status: 'ENDED', endHubId: 'h' });
    expect(JSON.parse(String(requestTo('POST', '/api/v1/trips/trip-1/end').body))).toEqual({
      hubId: 'h',
    });
  });

  it('lists the remembered trips and skips the ones that cannot be read', async () => {
    registry.update((current) => ({ ...current, tripIds: ['trip-1', 'trip-gone', 'trip-2'] }));
    route('GET', '/api/v1/trips/trip-1', json(trip('trip-1', 'IN_USE')));
    route('GET', '/api/v1/trips/trip-2', json(trip('trip-2', 'ENDED')));

    const trips = await backend.myTrips();

    expect(trips.map((t) => t.id)).toEqual(['trip-1', 'trip-2']);
  });
});

describe('live backend: hubs and fleet', () => {
  it('registers a hub and lists it with occupancy from its docked bikes', async () => {
    route('POST', '/api/v1/hubs', json(HUB, 201));
    route(
      'GET',
      '/api/v1/hubs/h-farol/vehicles',
      json([
        { code: 'EB-1', type: 'E_BIKE', status: 'AVAILABLE', batteryPct: 90, bookable: true },
        { code: 'EB-2', type: 'E_BIKE', status: 'MAINTENANCE', batteryPct: 40 },
        { code: 'EB-3', type: 'E_BIKE', status: 'IN_USE', batteryPct: 70 },
        { code: 'EB-4', type: 'E_BIKE', status: 'RETIRED', batteryPct: null },
      ]),
    );

    await backend.registerHub({
      name: HUB.name,
      kind: HUB.kind,
      hotel: null,
      latitude: HUB.latitude,
      longitude: HUB.longitude,
      capacity: HUB.capacity,
    });
    const hubs = await backend.hubs();

    expect(hubs).toEqual([{ ...HUB, dockedBikes: 2, freeSlots: 1 }]);
  });

  it('never reports negative free slots', async () => {
    registry.update((current) => ({ ...current, hubs: [{ ...HUB, capacity: 1 }] }));
    const bike = { type: 'E_BIKE', status: 'AVAILABLE' };
    route(
      'GET',
      '/api/v1/hubs/h-farol/vehicles',
      json([
        { ...bike, code: 'EB-1' },
        { ...bike, code: 'EB-2' },
      ]),
    );

    const [hub] = await backend.hubs();

    expect(hub?.freeSlots).toBe(0);
  });

  it('keeps a hub as remembered when its bikes cannot be read', async () => {
    registry.update((current) => ({ ...current, hubs: [HUB] }));
    route('GET', '/api/v1/hubs/h-farol/vehicles', json({ not: 'a list' }));

    expect(await backend.hubs()).toEqual([HUB]);
  });

  it('rejects a hub vehicle list that is not an array', async () => {
    route('GET', '/api/v1/hubs/h-farol/vehicles', json({ not: 'a list' }));

    await expect(backend.hubVehicles('h-farol')).rejects.toThrow(
      'The backend returned an invalid vehicles',
    );
  });

  it('registers a vehicle and names it in the fleet with its known id', async () => {
    registry.update((current) => ({
      ...current,
      hubs: [HUB, { ...HUB, id: 'h-broken', name: 'Broken' }],
    }));
    route(
      'POST',
      '/api/v1/hubs/h-farol/vehicles',
      json(
        {
          id: 'v-9',
          code: 'EB-9',
          type: 'E_BIKE',
          status: 'AVAILABLE',
          hubId: 'h-farol',
          batteryPct: 100,
        },
        201,
      ),
    );
    route(
      'GET',
      '/api/v1/hubs/h-farol/vehicles',
      json([
        { code: 'EB-9', type: 'E_BIKE', status: 'AVAILABLE', batteryPct: 100, bookable: true },
        { code: 'EB-X', type: 'E_BIKE', status: 'AVAILABLE', batteryPct: 50, bookable: true },
      ]),
    );
    route('GET', '/api/v1/hubs/h-broken/vehicles', problem(500, { title: 'Boom' }));

    const vehicle = await backend.registerVehicle('h-farol', 'EB-9');
    const fleet = await backend.fleet();

    expect(JSON.parse(String(requestTo('POST', '/api/v1/hubs/h-farol/vehicles').body))).toEqual({
      code: 'EB-9',
      type: 'E_BIKE',
    });
    expect(vehicle.id).toBe('v-9');
    expect(registry.read().vehicles).toEqual([{ id: 'v-9', code: 'EB-9', hubId: 'h-farol' }]);
    expect(fleet).toEqual([
      expect.objectContaining({ code: 'EB-9', id: 'v-9', hubId: 'h-farol', hubName: HUB.name }),
      expect.objectContaining({ code: 'EB-X', id: null, hubId: 'h-farol' }),
    ]);
  });

  it('changes a vehicle status with a PUT', async () => {
    route(
      'PUT',
      '/api/v1/vehicles/v-9/status',
      json({ id: 'v-9', code: 'EB-9', type: 'E_BIKE', status: 'MAINTENANCE', hubId: 'h-farol' }),
    );

    const vehicle = await backend.changeVehicleStatus('v-9', 'MAINTENANCE');

    expect(vehicle.status).toBe('MAINTENANCE');
    expect(JSON.parse(String(requestTo('PUT', '/api/v1/vehicles/v-9/status').body))).toEqual({
      status: 'MAINTENANCE',
    });
  });
});

describe('live backend: tickets and stays', () => {
  it('opens a ticket and lists it', async () => {
    const ticket = {
      id: 't-1',
      vehicleCode: 'EB-9',
      status: 'OPEN',
      description: 'Flat tyre.',
      openedAt: '2026-11-25T10:00:00Z',
      slaDueAt: '2026-11-26T10:00:00Z',
      vehicleStatus: 'MAINTENANCE',
      maintenancePending: false,
    };
    route('POST', '/api/v1/tickets', json(ticket, 201));

    await backend.openTicket({ vehicleCode: 'EB-9', description: 'Flat tyre.' });

    expect(await backend.tickets()).toEqual([ticket]);
  });

  it('registers a stay and lists it', async () => {
    const stay = {
      id: 's-1',
      hotelId: 'hotel-1',
      guestName: 'Ana Ficticia',
      stayCode: 'PRAIA-7K4Q',
      checkIn: '2026-11-24',
      checkOut: '2026-11-27',
      status: 'ACTIVE',
    };
    route('POST', '/api/v1/stays', json(stay, 201));

    await backend.registerStay({
      hotelId: 'hotel-1',
      guestName: 'Ana Ficticia',
      checkIn: '2026-11-24',
      checkOut: '2026-11-27',
    });

    expect(await backend.stays()).toEqual([stay]);
  });

  it('rejects a stay the backend refuses, naming the field', async () => {
    route(
      'POST',
      '/api/v1/stays',
      problem(422, {
        title: 'Invalid stay dates',
        'invalid-params': [{ name: 'checkOut', reason: 'before check-in' }],
      }),
    );

    const error = await backend
      .registerStay({
        hotelId: 'hotel-1',
        guestName: 'Ana Ficticia',
        checkIn: '2026-11-27',
        checkOut: '2026-11-24',
      })
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ProblemError);
    expect((error as ProblemError).fields).toEqual(['checkOut']);
    expect(await backend.stays()).toEqual([]);
  });
});

describe('live backend: registry', () => {
  it('replaces a remembered entry with the same id instead of duplicating it', async () => {
    const ticket = {
      id: 't-1',
      vehicleCode: 'EB-9',
      status: 'OPEN',
      description: 'Flat tyre.',
      openedAt: '2026-11-25T10:00:00Z',
      slaDueAt: '2026-11-26T10:00:00Z',
      vehicleStatus: 'MAINTENANCE' as const,
      maintenancePending: false,
    };
    const stay = {
      id: 's-1',
      hotelId: 'hotel-1',
      guestName: 'Ana Ficticia',
      stayCode: 'PRAIA-7K4Q',
      checkIn: '2026-11-24',
      checkOut: '2026-11-27',
      status: 'ACTIVE' as const,
    };
    registry.update((current) => ({
      ...current,
      hubs: [{ ...HUB, name: 'Old name' }],
      vehicles: [{ id: 'v-9', code: 'EB-9', hubId: null }],
      tickets: [{ ...ticket, status: 'OLD' }],
      stays: [{ ...stay, guestName: 'Old name' }],
    }));
    route('POST', '/api/v1/hubs', json(HUB, 201));
    route(
      'POST',
      '/api/v1/hubs/h-farol/vehicles',
      json({ id: 'v-9', code: 'EB-9', type: 'E_BIKE', status: 'AVAILABLE', hubId: 'h-farol' }),
    );
    route('POST', '/api/v1/tickets', json(ticket, 201));
    route('POST', '/api/v1/stays', json(stay, 201));

    await backend.registerHub({ ...HUB, hotel: null });
    await backend.registerVehicle('h-farol', 'EB-9');
    await backend.openTicket({ vehicleCode: 'EB-9', description: 'Flat tyre.' });
    await backend.registerStay({
      hotelId: 'hotel-1',
      guestName: 'Ana Ficticia',
      checkIn: '2026-11-24',
      checkOut: '2026-11-27',
    });

    const remembered = registry.read();
    expect(remembered.hubs).toEqual([HUB]);
    expect(remembered.vehicles).toEqual([{ id: 'v-9', code: 'EB-9', hubId: 'h-farol' }]);
    expect(remembered.tickets).toEqual([ticket]);
    expect(remembered.stays).toEqual([stay]);
  });
});

describe('isActiveTrip', () => {
  it.each([
    ['STARTING', true],
    ['IN_USE', true],
    ['FAILED', false],
    ['ENDED', false],
  ] as const)('says a %s trip is active: %s', (status, expected) => {
    const value: Trip = {
      ...trip('t', status),
      startedAt: null,
      endedAt: null,
      endHubId: null,
    };

    expect(isActiveTrip(value)).toBe(expected);
  });
});
