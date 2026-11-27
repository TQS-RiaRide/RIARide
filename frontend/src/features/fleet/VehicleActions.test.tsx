import { describe, expect, it } from 'vitest';
import { allowedChanges } from './VehicleActions';

describe('allowedChanges', () => {
  it('lets an available bike go to maintenance or be retired', () => {
    expect(allowedChanges('AVAILABLE')).toEqual(['MAINTENANCE', 'RETIRED']);
  });

  it('lets a bike in maintenance come back or be retired', () => {
    expect(allowedChanges('MAINTENANCE')).toEqual(['AVAILABLE', 'RETIRED']);
  });

  it('lets a reserved bike take any settable status', () => {
    expect(allowedChanges('RESERVED')).toEqual(['AVAILABLE', 'MAINTENANCE', 'RETIRED']);
  });

  it('never retires a bike that is on a trip', () => {
    expect(allowedChanges('IN_USE')).toEqual(['AVAILABLE', 'MAINTENANCE']);
  });

  it('allows no change once a bike is retired', () => {
    expect(allowedChanges('RETIRED')).toEqual([]);
  });
});
