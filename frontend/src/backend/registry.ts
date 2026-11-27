import type { Hub, Pass, Stay, Ticket } from './types';

// What this browser created or read through the live API, kept until the API can list it
// (RIA-107). Stays stay in memory only: a stay code lets someone sign up (BR1), so neither it nor
// the guest's name is written to storage, and logout clears what is (clearRegistries).

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
      return { ...EMPTY, ...parsed, stays: memory.stays };
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
        storage?.setItem(key(), JSON.stringify({ ...next, stays: [] }));
      } catch {
        // Not remembering is acceptable: the next read falls back to memory.
      }
      return next;
    },
  };
}

/** Removes every user's registry from this browser; called on logout. */
export function clearRegistries(
  storage: Pick<Storage, 'key' | 'length' | 'removeItem'> | undefined = safeStorage(),
): void {
  if (!storage) return;
  try {
    const keys = Array.from({ length: storage.length }, (_, i) => storage.key(i));
    keys
      .filter((key): key is string => key?.startsWith(`${REGISTRY_KEY}:`) ?? false)
      .forEach((key) => {
        storage.removeItem(key);
      });
  } catch {
    // Storage the browser refuses to touch holds nothing of ours to clear.
  }
}

export function upsertBy<T>(items: readonly T[], item: T, same: (a: T, b: T) => boolean): T[] {
  return [item, ...items.filter((existing) => !same(existing, item))];
}
