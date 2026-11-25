import type { Hub, Pass, Stay, Ticket } from './types';

// What this browser created or read through the live API, kept until the API can list it
// (RIA-107). Only ids, codes and names of synthetic data: no tokens, no passwords.

export interface KnownVehicle {
  id: string;
  code: string;
  hubId: string | null;
}

export interface Registry {
  hubs: Hub[];
  vehicles: KnownVehicle[];
  pass: Pass | null;
  tripIds: string[];
  tickets: Ticket[];
  stays: Stay[];
}

export const REGISTRY_KEY = 'riaride.registry.v1';

const EMPTY: Registry = { hubs: [], vehicles: [], pass: null, tripIds: [], tickets: [], stays: [] };

export interface RegistryStore {
  read(): Registry;
  update(change: (current: Registry) => Registry): Registry;
}

function safeStorage(): Storage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.localStorage;
  } catch {
    return undefined;
  }
}

/** Keeps the registry per user, so two accounts on one browser never share a pass. */
export function createRegistryStore(
  userKey: () => string,
  storage: Pick<Storage, 'getItem' | 'setItem'> | undefined = safeStorage(),
): RegistryStore {
  let memory: Registry = EMPTY;
  const key = () => `${REGISTRY_KEY}:${userKey()}`;

  const read = (): Registry => {
    try {
      const raw = storage?.getItem(key());
      if (!raw) return memory;
      const parsed = JSON.parse(raw) as Partial<Registry>;
      return { ...EMPTY, ...parsed };
    } catch {
      return memory;
    }
  };

  return {
    read,
    update(change) {
      const next = change(read());
      memory = next;
      try {
        storage?.setItem(key(), JSON.stringify(next));
      } catch {
        // Not remembering is acceptable: the next read falls back to memory.
      }
      return next;
    },
  };
}

export function upsertBy<T>(items: readonly T[], item: T, same: (a: T, b: T) => boolean): T[] {
  return [item, ...items.filter((existing) => !same(existing, item))];
}
