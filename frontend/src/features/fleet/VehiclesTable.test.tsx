import { describe, expect, it } from 'vitest';
import type { FleetVehicle } from '../../backend/types';
import { filterVehicles } from './VehiclesTable';

function bike(code: string, overrides: Partial<FleetVehicle> = {}): FleetVehicle {
  return {
    code,
    type: 'E_BIKE',
    status: 'AVAILABLE',
    batteryPct: 80,
    bookable: true,
    id: `id-${code}`,
    hubId: 'h-praia',
    hubName: 'Marés Praia',
    ...overrides,
  };
}

const FLEET: FleetVehicle[] = [
  bike('EB-101', { hubId: 'h-centro', hubName: 'Marés Centro' }),
  bike('EB-118', { status: 'MAINTENANCE' }),
  bike('EB-372', { batteryPct: 19 }),
  bike('EB-373', { batteryPct: 20 }),
  bike('EB-514', { status: 'IN_USE', hubId: null, hubName: null }),
  bike('EB-099', { status: 'RETIRED', batteryPct: null, hubId: null, hubName: null }),
];

const codes = (vehicles: FleetVehicle[]) => vehicles.map((v) => v.code);
const ALL = { query: '', status: 'ALL' as const, hubId: null };

describe('filterVehicles', () => {
  it('keeps every bike with no filter', () => {
    expect(filterVehicles(FLEET, ALL)).toEqual(FLEET);
  });

  it('keeps only the bikes of the chosen hub', () => {
    expect(codes(filterVehicles(FLEET, { ...ALL, hubId: 'h-centro' }))).toEqual(['EB-101']);
  });

  it('keeps only the bikes in the chosen status', () => {
    expect(codes(filterVehicles(FLEET, { ...ALL, status: 'MAINTENANCE' }))).toEqual(['EB-118']);
  });

  it('counts a bike under 20 % as low battery but not one at exactly 20 % or with no reading', () => {
    expect(codes(filterVehicles(FLEET, { ...ALL, status: 'LOW_BATTERY' }))).toEqual(['EB-372']);
  });

  it('matches the search against the code and the hub name, ignoring case and spaces', () => {
    expect(codes(filterVehicles(FLEET, { ...ALL, query: '  eb-37 ' }))).toEqual([
      'EB-372',
      'EB-373',
    ]);
    expect(codes(filterVehicles(FLEET, { ...ALL, query: 'CENTRO' }))).toEqual(['EB-101']);
  });

  it('does not match a bike with no hub by hub name', () => {
    expect(filterVehicles(FLEET, { ...ALL, query: 'null' })).toEqual([]);
  });

  it('combines the hub, the status and the search', () => {
    expect(
      codes(filterVehicles(FLEET, { query: '372', status: 'LOW_BATTERY', hubId: 'h-praia' })),
    ).toEqual(['EB-372']);
    expect(
      filterVehicles(FLEET, { query: '372', status: 'LOW_BATTERY', hubId: 'h-centro' }),
    ).toEqual([]);
  });
});
