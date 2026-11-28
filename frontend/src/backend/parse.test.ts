import { describe, expect, it } from 'vitest';
import {
  InvalidResponseError,
  list,
  parseHub,
  parseHubVehicle,
  parsePass,
  parseStay,
  parseTicket,
  parseTrip,
  parseVehicle,
} from './parse';

const PASS = {
  id: 'p-1',
  passType: 'STAY',
  status: 'INACTIVE',
  priceCents: 4000,
  depositCents: 5000,
  currency: 'EUR',
};

const HUB = {
  id: 'h-1',
  name: 'Farol da Barra',
  kind: 'TOURIST_SPOT',
  latitude: 40.64,
  longitude: -8.74,
  capacity: 6,
};

const TRIP = {
  id: 'trip-1',
  status: 'STARTING',
  vehicleCode: 'EB-101',
  requestedAt: '2026-11-25T10:00:00Z',
};

const TICKET = {
  id: 't-1',
  vehicleCode: 'EB-101',
  status: 'OPEN',
  description: 'Flat tyre.',
  openedAt: '2026-11-25T10:00:00Z',
  slaDueAt: '2026-11-26T10:00:00Z',
  vehicleStatus: 'MAINTENANCE',
};

const STAY = {
  id: 's-1',
  hotelId: 'hotel-1',
  guestName: 'Ana Ficticia',
  stayCode: 'PRAIA-7K4Q',
  checkIn: '2026-11-24',
  checkOut: '2026-11-27',
  status: 'ACTIVE',
};

describe('parsePass', () => {
  it('accepts a well-formed pass', () => {
    expect(parsePass(PASS)).toEqual(PASS);
  });

  it.each([
    ['a null body', null],
    ['an array', [PASS]],
    ['a missing id', { ...PASS, id: undefined }],
    ['an unknown pass type', { ...PASS, passType: 'WEEK' }],
    ['an unknown status', { ...PASS, status: 'LOST' }],
    ['a price that is not a number', { ...PASS, priceCents: '1000' }],
    ['a price that is not finite', { ...PASS, priceCents: Number.NaN }],
  ])('rejects %s', (_label, body) => {
    expect(() => parsePass(body)).toThrow(InvalidResponseError);
  });

  it('names what was invalid', () => {
    expect(() => parsePass(null)).toThrow('The backend returned an invalid pass');
  });
});

describe('parseHub', () => {
  it('assumes an empty hub when occupancy is missing', () => {
    expect(parseHub(HUB)).toEqual({ ...HUB, hotel: null, dockedBikes: 0, freeSlots: 6 });
  });

  it('keeps the hotel and occupancy when given', () => {
    const hub = parseHub({ ...HUB, hotel: 'Marés Praia', dockedBikes: 4, freeSlots: 2 });

    expect(hub).toMatchObject({ hotel: 'Marés Praia', dockedBikes: 4, freeSlots: 2 });
  });

  it('rejects an unknown hub kind', () => {
    expect(() => parseHub({ ...HUB, kind: 'PARKING' })).toThrow(InvalidResponseError);
  });
});

describe('parseHubVehicle', () => {
  it('reads a bookable bike', () => {
    expect(
      parseHubVehicle({
        code: 'EB-1',
        type: 'E_BIKE',
        status: 'AVAILABLE',
        batteryPct: 80,
        bookable: true,
      }),
    ).toEqual({
      code: 'EB-1',
      type: 'E_BIKE',
      status: 'AVAILABLE',
      batteryPct: 80,
      bookable: true,
    });
  });

  it('treats a missing battery as unknown and a missing flag as not bookable', () => {
    const vehicle = parseHubVehicle({ code: 'EB-1', type: 'E_BIKE', status: 'AVAILABLE' });

    expect(vehicle.batteryPct).toBeNull();
    expect(vehicle.bookable).toBe(false);
  });

  it('rejects an unknown vehicle type', () => {
    expect(() => parseHubVehicle({ code: 'EB-1', type: 'SCOOTER', status: 'AVAILABLE' })).toThrow(
      InvalidResponseError,
    );
  });
});

describe('parseVehicle', () => {
  it('reads a vehicle with an optional hub', () => {
    expect(
      parseVehicle({ id: 'v-1', code: 'EB-1', type: 'E_BIKE', status: 'IN_USE', hubId: null }),
    ).toEqual({
      id: 'v-1',
      code: 'EB-1',
      type: 'E_BIKE',
      status: 'IN_USE',
      hubId: null,
      batteryPct: null,
    });
  });

  it('rejects an unknown status', () => {
    expect(() =>
      parseVehicle({ id: 'v-1', code: 'EB-1', type: 'E_BIKE', status: 'STOLEN' }),
    ).toThrow(InvalidResponseError);
  });
});

describe('parseTrip', () => {
  it('reads a starting trip with no start, end or hub yet', () => {
    expect(parseTrip(TRIP)).toEqual({
      ...TRIP,
      startedAt: null,
      endedAt: null,
      endHubId: null,
    });
  });

  it('rejects a trip without a vehicle code', () => {
    expect(() => parseTrip({ ...TRIP, vehicleCode: 7 })).toThrow(InvalidResponseError);
  });
});

describe('parseTicket', () => {
  it('reads a ticket and defaults maintenancePending to false', () => {
    expect(parseTicket(TICKET)).toEqual({ ...TICKET, maintenancePending: false });
  });

  it('keeps a pending maintenance flag', () => {
    expect(
      parseTicket({ ...TICKET, vehicleStatus: 'IN_USE', maintenancePending: true })
        .maintenancePending,
    ).toBe(true);
  });

  it('rejects an unexpected vehicle status', () => {
    expect(() => parseTicket({ ...TICKET, vehicleStatus: 'AVAILABLE' })).toThrow(
      InvalidResponseError,
    );
  });
});

describe('parseStay', () => {
  it('reads a stay', () => {
    expect(parseStay(STAY)).toEqual(STAY);
  });

  it('rejects an unknown stay status', () => {
    expect(() => parseStay({ ...STAY, status: 'CANCELLED' })).toThrow(InvalidResponseError);
  });
});

describe('list', () => {
  it('parses each entry of an array', () => {
    expect(list([PASS, PASS], parsePass, 'passes')).toHaveLength(2);
  });

  it('rejects a body that is not an array', () => {
    expect(() => list({ items: [] }, parsePass, 'passes')).toThrow(
      'The backend returned an invalid passes',
    );
  });
});
