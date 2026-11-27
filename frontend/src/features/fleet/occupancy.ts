import type { Hub } from '../../backend/types';

// When a hub counts as full or almost empty, for the operator's redistribution (scenario 2).
export const ALMOST_EMPTY_RATIO = 0.2;

export type OccupancyLevel = 'full' | 'low' | 'ok';

export function occupancyLevel(hub: Hub): OccupancyLevel {
  if (hub.capacity <= 0) return 'ok';
  if (hub.freeSlots === 0) return 'full';
  if (hub.dockedBikes / hub.capacity <= ALMOST_EMPTY_RATIO) return 'low';
  return 'ok';
}

export interface Redistribution {
  from: Hub;
  to: Hub;
  bikes: number;
}

/**
 * Suggests moving bikes from the fullest hub to the emptiest one, until both are at the group's
 * average share of their capacity. Null when there is nothing worth moving.
 */
export function suggestRedistribution(hubs: readonly Hub[]): Redistribution | null {
  const usable = hubs.filter((hub) => hub.capacity > 0);
  if (usable.length < 2) return null;
  const share = (hub: Hub) => hub.dockedBikes / hub.capacity;
  const sorted = [...usable].sort((a, b) => share(b) - share(a));
  const from = sorted[0];
  const to = sorted[sorted.length - 1];
  if (occupancyLevel(from) !== 'full' && occupancyLevel(to) !== 'low') return null;

  const docked = usable.reduce((sum, hub) => sum + hub.dockedBikes, 0);
  const capacity = usable.reduce((sum, hub) => sum + hub.capacity, 0);
  const target = docked / capacity;
  const surplus = Math.floor(from.dockedBikes - target * from.capacity);
  const deficit = Math.floor(target * to.capacity - to.dockedBikes);
  const bikes = Math.min(surplus, deficit, to.freeSlots);
  return bikes > 0 ? { from, to, bikes } : null;
}
