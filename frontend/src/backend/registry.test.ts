import { afterEach, describe, expect, it } from 'vitest';
import {
  REGISTRY_KEY,
  clearRegistries,
  createRegistryStore,
  upsertBy,
  type Registry,
} from './registry';
import type { Pass, Stay } from './types';

const PASS: Pass = {
  id: 'p-1',
  passType: 'ONE_DAY',
  status: 'INACTIVE',
  priceCents: 1000,
  depositCents: 5000,
  currency: 'EUR',
};

const STAY: Stay = {
  id: 's-1',
  hotelId: 'h-1',
  guestName: 'Synthetic Guest',
  stayCode: 'MC-7Q2K',
  checkIn: '2026-11-20',
  checkOut: '2026-11-23',
  status: 'ACTIVE',
};

const withPass = (current: Registry): Registry => ({ ...current, pass: PASS });
const withStay = (current: Registry): Registry => ({ ...current, stays: [STAY] });

afterEach(() => {
  window.localStorage.clear();
});

describe('createRegistryStore', () => {
  it('starts empty', () => {
    const store = createRegistryStore(() => 'guest-a');

    expect(store.read()).toEqual({
      hubs: [],
      vehicles: [],
      pass: null,
      tripIds: [],
      tickets: [],
      stays: [],
    });
  });

  it('keeps what was stored in localStorage under a key per user', () => {
    const store = createRegistryStore(() => 'guest-a');

    store.update(withPass);

    expect(window.localStorage.getItem(`${REGISTRY_KEY}:guest-a`)).toContain('"p-1"');
    expect(createRegistryStore(() => 'guest-a').read().pass).toEqual(PASS);
  });

  it('never shares a pass between two users of the same browser', () => {
    createRegistryStore(() => 'guest-a').update(withPass);

    expect(createRegistryStore(() => 'guest-b').read().pass).toBeNull();
  });

  it('fills missing fields of an older stored registry', () => {
    window.localStorage.setItem(`${REGISTRY_KEY}:guest-a`, JSON.stringify({ tripIds: ['t-1'] }));

    const registry = createRegistryStore(() => 'guest-a').read();

    expect(registry.tripIds).toEqual(['t-1']);
    expect(registry.hubs).toEqual([]);
  });

  it('falls back to memory when the stored value is not JSON', () => {
    const store = createRegistryStore(() => 'guest-a');
    store.update(withPass);
    window.localStorage.setItem(`${REGISTRY_KEY}:guest-a`, '{broken');

    expect(store.read().pass).toEqual(PASS);
  });

  it('remembers in memory when the storage refuses writes', () => {
    const storage = {
      getItem: () => null,
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    };
    const store = createRegistryStore(() => 'guest-a', storage);

    const next = store.update(withPass);

    expect(next.pass).toEqual(PASS);
    expect(store.read().pass).toEqual(PASS);
  });

  it('never writes stay codes or guest names to storage', () => {
    const store = createRegistryStore(() => 'manager-a');

    store.update(withStay);

    const stored = window.localStorage.getItem(`${REGISTRY_KEY}:manager-a`) ?? '';
    expect(stored).not.toContain(STAY.stayCode);
    expect(stored).not.toContain(STAY.guestName);
    expect(store.read().stays).toEqual([STAY]);
  });

  it('forgets stays when the page reloads', () => {
    createRegistryStore(() => 'manager-a').update(withStay);

    expect(createRegistryStore(() => 'manager-a').read().stays).toEqual([]);
  });

  it('ignores stays an older version left in storage', () => {
    window.localStorage.setItem(`${REGISTRY_KEY}:manager-a`, JSON.stringify({ stays: [STAY] }));

    expect(createRegistryStore(() => 'manager-a').read().stays).toEqual([]);
  });

  it('works with no storage at all', () => {
    const store = createRegistryStore(() => 'guest-a', undefined);

    store.update(withPass);

    expect(store.read().pass).toEqual(PASS);
  });
});

describe('clearRegistries', () => {
  it('removes the registry of every user and keeps other keys', () => {
    createRegistryStore(() => 'guest-a').update(withPass);
    createRegistryStore(() => 'guest-b').update(withPass);
    window.localStorage.setItem('riaride.language', 'pt');

    clearRegistries();

    expect(window.localStorage.getItem(`${REGISTRY_KEY}:guest-a`)).toBeNull();
    expect(window.localStorage.getItem(`${REGISTRY_KEY}:guest-b`)).toBeNull();
    expect(window.localStorage.getItem('riaride.language')).toBe('pt');
  });

  it('does nothing when there is no storage', () => {
    expect(() => {
      clearRegistries(undefined);
    }).not.toThrow();
  });
});

describe('upsertBy', () => {
  const sameId = (a: { id: string }, b: { id: string }) => a.id === b.id;

  it('puts a new item first', () => {
    expect(upsertBy([{ id: 'a' }], { id: 'b' }, sameId)).toEqual([{ id: 'b' }, { id: 'a' }]);
  });

  it('replaces an item with the same identity and moves it first', () => {
    const items = [
      { id: 'a', v: 1 },
      { id: 'b', v: 1 },
    ];

    const next = upsertBy(items, { id: 'b', v: 2 }, sameId);

    expect(next).toEqual([
      { id: 'b', v: 2 },
      { id: 'a', v: 1 },
    ]);
    expect(items).toHaveLength(2);
  });
});
