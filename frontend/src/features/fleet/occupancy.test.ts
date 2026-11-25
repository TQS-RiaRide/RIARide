import { describe, expect, it } from 'vitest';
import type { Hub } from '../../backend/types';
import { ALMOST_EMPTY_RATIO, occupancyLevel, suggestRedistribution } from './occupancy';

function hub(id: string, capacity: number, dockedBikes: number, freeSlots?: number): Hub {
  return {
    id,
    name: `Hub ${id}`,
    kind: 'TOURIST_SPOT',
    hotel: null,
    latitude: 40.64,
    longitude: -8.65,
    capacity,
    dockedBikes,
    freeSlots: freeSlots ?? Math.max(capacity - dockedBikes, 0),
  };
}

describe('occupancyLevel', () => {
  it('treats a hub without capacity as ok', () => {
    expect(occupancyLevel(hub('a', 0, 0))).toBe('ok');
  });

  it('marks a hub with no free slot as full', () => {
    expect(occupancyLevel(hub('a', 10, 10))).toBe('full');
  });

  it('marks a hub at exactly the almost empty ratio as low', () => {
    expect(ALMOST_EMPTY_RATIO).toBe(0.2);
    expect(occupancyLevel(hub('a', 10, 2))).toBe('low');
  });

  it('marks an empty hub as low', () => {
    expect(occupancyLevel(hub('a', 10, 0))).toBe('low');
  });

  it('marks a hub just above the almost empty ratio as ok', () => {
    expect(occupancyLevel(hub('a', 100, 21))).toBe('ok');
  });

  it('marks a hub with one free slot as ok', () => {
    expect(occupancyLevel(hub('a', 10, 9))).toBe('ok');
  });
});

describe('suggestRedistribution', () => {
  it('suggests nothing with fewer than two hubs with capacity', () => {
    expect(suggestRedistribution([hub('a', 10, 10), hub('b', 0, 0)])).toBeNull();
    expect(suggestRedistribution([])).toBeNull();
  });

  it('suggests nothing when no hub is full or almost empty', () => {
    expect(suggestRedistribution([hub('a', 10, 6), hub('b', 10, 4)])).toBeNull();
  });

  it('moves bikes from the fullest hub to the emptiest until both reach the average share', () => {
    const full = hub('full', 10, 10);
    const empty = hub('empty', 10, 0);

    const suggestion = suggestRedistribution([hub('mid', 10, 5), full, empty]);

    expect(suggestion).toEqual({ from: full, to: empty, bikes: 5 });
  });

  it('never moves more bikes than the emptiest hub has free slots', () => {
    const full = hub('full', 10, 10);
    const low = hub('low', 10, 1, 2);

    expect(suggestRedistribution([full, low])?.bikes).toBe(2);
  });

  it('suggests nothing when the rounded number of bikes to move is zero', () => {
    // Shares 1.0 and 0.75: the target is 0.8 of 5 and 4 slots, so nothing whole to move.
    expect(suggestRedistribution([hub('a', 1, 1), hub('b', 4, 3)])).toBeNull();
  });
});
