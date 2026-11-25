import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from './Backend';
import { createDemoBackend } from './demoBackend';
import { DEMO_KPIS, HOTELS } from './demoData';
import { BASE_PASS_PRICES } from './liveBackend';
import { ProblemError } from './problem';
import type { RegisterHubRequest } from './types';

const START = Date.parse('2026-11-25T10:00:00.000Z');
const UNLOCK_MS = 2_000;

let clock: number;
let backend: Backend;

function advance(ms: number) {
  clock += ms;
}

async function problemOf(promise: Promise<unknown>): Promise<ProblemError> {
  const error = await promise.then(
    () => undefined,
    (e: unknown) => e,
  );
  if (!(error instanceof ProblemError)) throw new Error(`Expected a ProblemError, got ${error}`);
  return error;
}

async function withPass() {
  await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-1');
}

async function startedTrip(vehicleCode = 'EB-701') {
  await withPass();
  const trip = await backend.startTrip(vehicleCode);
  advance(UNLOCK_MS);
  return backend.trip(trip.id);
}

const HUB_REQUEST: RegisterHubRequest = {
  name: 'Miradouro Fictício',
  kind: 'TOURIST_SPOT',
  hotel: null,
  latitude: 40.64,
  longitude: -8.65,
  capacity: 1,
};

beforeEach(() => {
  clock = START;
  backend = createDemoBackend({ latencyMs: 0, now: () => clock });
});

describe('demo backend: catalogue', () => {
  it('runs in demo mode with the base prices and the demo KPIs', async () => {
    expect(backend.mode).toBe('demo');
    expect(await backend.passPrices()).toEqual(BASE_PASS_PRICES);
    expect(await backend.kpis()).toEqual(DEMO_KPIS);
  });

  it('lists hubs with occupancy from their docked bikes', async () => {
    const hubs = await backend.hubs();

    expect(hubs.find((h) => h.id === 'h-centro')).toMatchObject({ dockedBikes: 12, freeSlots: 0 });
    expect(hubs.find((h) => h.id === 'h-salinas')).toMatchObject({ dockedBikes: 1, freeSlots: 7 });
  });

  it('lists the bikes of a hub, bookable first, then by battery, without retired ones', async () => {
    const bikes = await backend.hubVehicles('h-praia');

    expect(bikes.map((b) => b.code)).toEqual([
      'EB-401',
      'EB-405',
      'EB-388',
      'EB-412',
      'EB-409',
      'EB-118',
      'EB-372',
    ]);
  });

  it('refuses the bikes of an unknown hub', async () => {
    expect((await problemOf(backend.hubVehicles('h-nowhere'))).status).toBe(404);
  });

  it('lists the whole fleet with hub names', async () => {
    const fleet = await backend.fleet();

    expect(fleet).toHaveLength(29);
    expect(fleet.find((v) => v.code === 'EB-701')).toMatchObject({
      id: 'v-27',
      hubName: 'Farol da Barra',
      bookable: true,
    });
    expect(fleet.find((v) => v.code === 'EB-514')).toMatchObject({ hubName: null });
  });

  it('lists the seeded past trips, tickets by SLA deadline, and stays', async () => {
    expect((await backend.myTrips()).map((t) => t.status)).toEqual(['ENDED', 'ENDED']);
    expect((await backend.tickets()).map((t) => t.id)).toEqual(['t-1', 't-2', 't-3']);
    expect(await backend.stays()).toHaveLength(3);
  });
});

describe('demo backend: guest sign-up', () => {
  it('accepts a known stay code regardless of case and spaces', async () => {
    await expect(
      backend.signUpGuest({
        email: 'guest@example.test',
        password: 'synthetic-only',
        stayCode: '  praia-7k4q ',
        locale: 'en',
      }),
    ).resolves.toBeUndefined();
  });

  it('refuses an unknown stay code, naming the field (BR1)', async () => {
    const error = await problemOf(
      backend.signUpGuest({
        email: 'guest@example.test',
        password: 'synthetic-only',
        stayCode: 'NOPE-0000',
        locale: 'pt',
      }),
    );

    expect(error.status).toBe(422);
    expect(error.fields).toEqual(['stayCode']);
  });
});

describe('demo backend: passes', () => {
  it('has no pass at first', async () => {
    expect(await backend.currentPass()).toBeNull();
  });

  it('buys an inactive pass at the base price', async () => {
    const pass = await backend.buyPass('THREE_DAY', 'pm_card_visa', 'key-1');

    expect(pass).toMatchObject({
      passType: 'THREE_DAY',
      status: 'INACTIVE',
      priceCents: 2500,
      depositCents: 5000,
      currency: 'EUR',
    });
    expect(await backend.currentPass()).toEqual(pass);
  });

  it('declines the Stripe test card that is always declined, and leaves no pass', async () => {
    const error = await problemOf(
      backend.buyPass('STAY', 'pm_card_chargeDeclined', 'key-declined'),
    );

    expect(error.status).toBe(402);
    expect(await backend.currentPass()).toBeNull();
  });

  it('returns the same pass and does not charge twice when retried with the same key', async () => {
    const first = await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-1');

    const retry = await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-1');

    expect(retry).toBe(first);
    expect(await backend.currentPass()).toBe(first);
  });

  it('refuses a second purchase with a new key while a pass is open', async () => {
    await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-1');

    expect((await problemOf(backend.buyPass('STAY', 'pm_card_visa', 'key-2'))).status).toBe(409);
  });
});

describe('demo backend: trips', () => {
  it('refuses a trip without a pass', async () => {
    expect((await problemOf(backend.startTrip('EB-701'))).status).toBe(403);
  });

  it('starts a trip as STARTING and takes the bike off its hub', async () => {
    await withPass();

    const trip = await backend.startTrip(' eb-701 ');

    expect(trip).toMatchObject({
      status: 'STARTING',
      vehicleCode: 'EB-701',
      requestedAt: new Date(START).toISOString(),
      startedAt: null,
    });
    const fleet = await backend.fleet();
    expect(fleet.find((v) => v.code === 'EB-701')).toMatchObject({ status: 'IN_USE', hubId: null });
  });

  it('keeps the trip STARTING until telemetry confirms the unlock (BR13)', async () => {
    await withPass();
    const trip = await backend.startTrip('EB-701');

    advance(UNLOCK_MS - 1);
    const waiting = await backend.trip(trip.id);
    advance(1);
    const confirmed = await backend.trip(trip.id);

    expect(waiting.status).toBe('STARTING');
    expect(confirmed).toMatchObject({
      status: 'IN_USE',
      startedAt: new Date(START + UNLOCK_MS).toISOString(),
    });
  });

  it('activates the pass on the first confirmed trip (BR4)', async () => {
    await startedTrip();

    expect((await backend.currentPass())?.status).toBe('ACTIVE');
  });

  it('shows the confirmed trip in the trip history', async () => {
    const trip = await startedTrip();

    expect((await backend.myTrips())[0]).toMatchObject({ id: trip.id, status: 'IN_USE' });
  });

  it('refuses a second bike while the first is still unlocking (BR6)', async () => {
    await withPass();
    await backend.startTrip('EB-701');

    const error = await problemOf(backend.startTrip('EB-702'));

    expect(error).toMatchObject({ status: 409, title: 'Trip already in progress' });
  });

  it('refuses a second bike once the first trip is known to be in use (BR6)', async () => {
    await startedTrip('EB-701');

    expect((await problemOf(backend.startTrip('EB-702'))).status).toBe(409);
  });

  it('refuses a second bike once the first unlock is confirmed, even before anyone reads the trip (BR6)', async () => {
    await withPass();
    await backend.startTrip('EB-701');
    clock += 3_000;

    const error = await problemOf(backend.startTrip('EB-702'));

    expect(error).toMatchObject({ status: 409, title: 'Trip already in progress' });
  });

  it('refuses an unknown bike', async () => {
    await withPass();

    expect((await problemOf(backend.startTrip('EB-000'))).status).toBe(404);
  });

  it('refuses a bike below 20 % battery (BR12)', async () => {
    await withPass();

    const error = await problemOf(backend.startTrip('EB-521'));

    expect(error).toMatchObject({ status: 409, title: 'Bike not available' });
  });

  it('marks a bike below 20 % battery as not bookable and one at 20 % or more as bookable', async () => {
    const bikes = await backend.hubVehicles('h-historico');

    expect(bikes.find((b) => b.code === 'EB-521')).toMatchObject({
      batteryPct: 19,
      bookable: false,
    });
    expect(bikes.find((b) => b.code === 'EB-523')?.bookable).toBe(true);
  });

  it('refuses a reserved bike', async () => {
    await withPass();

    expect((await problemOf(backend.startTrip('EB-107'))).status).toBe(409);
  });

  it('refuses an unknown trip', async () => {
    expect((await problemOf(backend.trip('trip-nowhere'))).status).toBe(404);
  });

  it('ends a trip at a hub with a free slot and docks the bike there (BR10)', async () => {
    const trip = await startedTrip('EB-701');
    advance(600_000);

    const ended = await backend.endTrip(trip.id, 'h-salinas');

    expect(ended).toMatchObject({
      status: 'ENDED',
      endHubId: 'h-salinas',
      endedAt: new Date(START + UNLOCK_MS + 600_000).toISOString(),
    });
    const salinas = (await backend.hubs()).find((h) => h.id === 'h-salinas');
    expect(salinas).toMatchObject({ dockedBikes: 2, freeSlots: 6 });
    const fleet = await backend.fleet();
    expect(fleet.find((v) => v.code === 'EB-701')).toMatchObject({
      status: 'AVAILABLE',
      hubId: 'h-salinas',
    });
  });

  it('refuses to end a trip at a full hub (BR10)', async () => {
    const trip = await startedTrip();

    const error = await problemOf(backend.endTrip(trip.id, 'h-centro'));

    expect(error).toMatchObject({ status: 409, title: 'Hub full' });
    expect((await backend.trip(trip.id)).status).toBe('IN_USE');
  });

  it('refuses to end a trip at an unknown hub', async () => {
    const trip = await startedTrip();

    expect((await problemOf(backend.endTrip(trip.id, 'h-nowhere'))).status).toBe(404);
  });

  it('refuses to end a trip that is still unlocking', async () => {
    await withPass();
    const trip = await backend.startTrip('EB-701');

    const error = await problemOf(backend.endTrip(trip.id, 'h-salinas'));

    expect(error).toMatchObject({ status: 409, title: 'Trip not in progress' });
  });

  it('allows a new trip once the previous one ended (BR6)', async () => {
    const trip = await startedTrip('EB-701');
    await backend.endTrip(trip.id, 'h-salinas');

    expect((await backend.startTrip('EB-702')).status).toBe('STARTING');
  });
});

describe('demo backend: hubs and vehicles', () => {
  it('registers a hub with a trimmed name and empty occupancy', async () => {
    const hub = await backend.registerHub({ ...HUB_REQUEST, name: '  Miradouro Fictício ' });

    expect(hub).toMatchObject({ name: 'Miradouro Fictício', dockedBikes: 0, freeSlots: 1 });
    expect((await backend.hubs()).some((h) => h.id === hub.id)).toBe(true);
  });

  it.each([
    ['a blank name', { name: '   ' }, 'name'],
    ['a zero capacity', { capacity: 0 }, 'capacity'],
    ['a fractional capacity', { capacity: 1.5 }, 'capacity'],
  ])('refuses a hub with %s', async (_label, change, field) => {
    const error = await problemOf(backend.registerHub({ ...HUB_REQUEST, ...change }));

    expect(error.status).toBe(400);
    expect(error.fields).toEqual([field]);
  });

  it('refuses a hub whose name is already in use, ignoring case, like the API (no field list)', async () => {
    const error = await problemOf(backend.registerHub({ ...HUB_REQUEST, name: 'farol da barra' }));

    expect(error).toMatchObject({ status: 409, title: 'Hub name already in use', fields: [] });
  });

  it('registers a vehicle with a normalized code and a full battery', async () => {
    const vehicle = await backend.registerVehicle('h-salinas', ' eb-900 ');

    expect(vehicle).toMatchObject({
      code: 'EB-900',
      type: 'E_BIKE',
      status: 'AVAILABLE',
      hubId: 'h-salinas',
      batteryPct: 100,
    });
    expect((await backend.hubVehicles('h-salinas')).map((b) => b.code)).toContain('EB-900');
  });

  it('refuses a vehicle with an invalid code', async () => {
    const error = await problemOf(backend.registerVehicle('h-salinas', 'x'));

    expect(error).toMatchObject({ status: 400, fields: ['code'] });
  });

  it('refuses a vehicle at an unknown hub', async () => {
    expect((await problemOf(backend.registerVehicle('h-nowhere', 'EB-900'))).status).toBe(404);
  });

  it('refuses a vehicle code already in use', async () => {
    const error = await problemOf(backend.registerVehicle('h-salinas', 'EB-701'));

    expect(error).toMatchObject({ status: 409, fields: ['code'] });
  });

  it('refuses a vehicle at a full hub', async () => {
    const error = await problemOf(backend.registerVehicle('h-centro', 'EB-900'));

    expect(error).toMatchObject({ status: 409, title: 'Hub full' });
  });

  it('changes a vehicle status', async () => {
    const vehicle = await backend.changeVehicleStatus('v-27', 'MAINTENANCE');

    expect(vehicle).toMatchObject({ code: 'EB-701', status: 'MAINTENANCE', hubId: 'h-farol' });
  });

  it('takes a retired vehicle off its hub', async () => {
    const vehicle = await backend.changeVehicleStatus('v-27', 'RETIRED');

    expect(vehicle).toMatchObject({ status: 'RETIRED', hubId: null });
    expect((await backend.hubVehicles('h-farol')).map((b) => b.code)).not.toContain('EB-701');
  });

  it('refuses to change an unknown vehicle', async () => {
    expect((await problemOf(backend.changeVehicleStatus('v-0', 'AVAILABLE'))).status).toBe(404);
  });

  it('refuses to bring a retired vehicle back', async () => {
    const error = await problemOf(backend.changeVehicleStatus('v-29', 'AVAILABLE'));

    expect(error).toMatchObject({ status: 409, title: 'Status change refused' });
  });

  it('refuses to retire a vehicle in use', async () => {
    expect((await problemOf(backend.changeVehicleStatus('v-21', 'RETIRED'))).status).toBe(409);
  });
});

describe('demo backend: maintenance tickets', () => {
  it('opens a ticket with a 24 h SLA and moves the bike to maintenance', async () => {
    const ticket = await backend.openTicket({
      vehicleCode: ' eb-701 ',
      description: '  Chain slips. ',
    });

    expect(ticket).toMatchObject({
      vehicleCode: 'EB-701',
      status: 'OPEN',
      description: 'Chain slips.',
      openedAt: new Date(START).toISOString(),
      slaDueAt: new Date(START + 24 * 3_600_000).toISOString(),
      vehicleStatus: 'MAINTENANCE',
      maintenancePending: false,
    });
    expect((await backend.fleet()).find((v) => v.code === 'EB-701')?.status).toBe('MAINTENANCE');
    expect((await backend.tickets()).map((t) => t.id)).toContain(ticket.id);
  });

  it('leaves a bike in use as it is and marks the maintenance as pending', async () => {
    const ticket = await backend.openTicket({ vehicleCode: 'EB-514', description: 'Noisy.' });

    expect(ticket).toMatchObject({ vehicleStatus: 'IN_USE', maintenancePending: true });
    expect((await backend.fleet()).find((v) => v.code === 'EB-514')?.status).toBe('IN_USE');
  });

  it('names every invalid field of a ticket', async () => {
    const error = await problemOf(backend.openTicket({ vehicleCode: ' ', description: '' }));

    expect(error).toMatchObject({ status: 400, fields: ['vehicleCode', 'description'] });
  });

  it('accepts a 500-character description and refuses a 501-character one', async () => {
    await expect(
      backend.openTicket({ vehicleCode: 'EB-701', description: 'a'.repeat(500) }),
    ).resolves.toBeDefined();

    const error = await problemOf(
      backend.openTicket({ vehicleCode: 'EB-702', description: 'a'.repeat(501) }),
    );
    expect(error.fields).toEqual(['description']);
  });

  it('refuses a ticket for an unknown bike', async () => {
    expect(
      (await problemOf(backend.openTicket({ vehicleCode: 'EB-000', description: 'x' }))).status,
    ).toBe(404);
  });

  it('refuses a ticket for a retired bike', async () => {
    expect(
      (await problemOf(backend.openTicket({ vehicleCode: 'EB-099', description: 'x' }))).status,
    ).toBe(409);
  });
});

describe('demo backend: stays', () => {
  const praia = HOTELS[1];

  it('registers a stay with a code built from the hotel name', async () => {
    const stay = await backend.registerStay({
      hotelId: praia.id,
      guestName: '  Rui Inventado ',
      checkIn: '2026-11-25',
      checkOut: '2026-11-28',
    });

    expect(stay).toMatchObject({
      hotelId: praia.id,
      guestName: 'Rui Inventado',
      status: 'ACTIVE',
    });
    expect(stay.stayCode).toMatch(/^PRAIA-[A-HJ-NP-Z2-9]{4}$/);
    expect((await backend.stays())[0]).toEqual(stay);
  });

  it('lets the guest sign up with the new stay code', async () => {
    const stay = await backend.registerStay({
      hotelId: HOTELS[2].id,
      guestName: 'Rui Inventado',
      checkIn: '2026-11-25',
      checkOut: '2026-11-26',
    });

    expect(stay.stayCode).toMatch(/^SALINAS-/);
    await expect(
      backend.signUpGuest({
        email: 'rui@example.test',
        password: 'synthetic-only',
        stayCode: stay.stayCode,
        locale: 'pt',
      }),
    ).resolves.toBeUndefined();
  });

  it('refuses an unknown hotel', async () => {
    const error = await problemOf(
      backend.registerStay({
        hotelId: 'hotel-nowhere',
        guestName: 'Rui Inventado',
        checkIn: '2026-11-25',
        checkOut: '2026-11-26',
      }),
    );

    expect(error.status).toBe(404);
  });

  it('refuses a stay without a guest name', async () => {
    const error = await problemOf(
      backend.registerStay({
        hotelId: praia.id,
        guestName: ' ',
        checkIn: '2026-11-25',
        checkOut: '2026-11-26',
      }),
    );

    expect(error).toMatchObject({ status: 400, fields: ['guestName'] });
  });

  it('refuses a check-out on the check-in day', async () => {
    const error = await problemOf(
      backend.registerStay({
        hotelId: praia.id,
        guestName: 'Rui Inventado',
        checkIn: '2026-11-25',
        checkOut: '2026-11-25',
      }),
    );

    expect(error).toMatchObject({ status: 422, fields: ['checkOut'] });
  });
});

describe('demo backend: latency', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('answers only after the configured delay', async () => {
    vi.useFakeTimers();
    const slow = createDemoBackend({ latencyMs: 100, now: () => START });
    let answered = false;

    const pending = slow.passPrices().then((prices) => {
      answered = true;
      return prices;
    });
    await vi.advanceTimersByTimeAsync(59);
    const early = answered;
    await vi.advanceTimersByTimeAsync(200);

    expect(early).toBe(false);
    expect(await pending).toEqual(BASE_PASS_PRICES);
  });

  it('rejects after the delay when the request is refused', async () => {
    vi.useFakeTimers();
    const slow = createDemoBackend({ latencyMs: 100, now: () => START });

    const pending = problemOf(slow.trip('trip-nowhere'));
    await vi.advanceTimersByTimeAsync(200);

    expect((await pending).status).toBe(404);
  });

  it('uses the real clock and a visible delay by default', () => {
    expect(createDemoBackend().mode).toBe('demo');
  });
});
