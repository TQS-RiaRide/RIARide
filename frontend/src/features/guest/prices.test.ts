import { describe, expect, it } from 'vitest';
import { cheapestPrice } from './prices';

describe('cheapestPrice', () => {
  it('returns the pass with the lowest price', () => {
    const prices = [
      { passType: 'STAY', priceCents: 4000, depositCents: 5000, currency: 'EUR' },
      { passType: 'ONE_DAY', priceCents: 1200, depositCents: 5000, currency: 'EUR' },
      { passType: 'THREE_DAY', priceCents: 2500, depositCents: 5000, currency: 'EUR' },
    ] as const;

    expect(cheapestPrice(prices)?.passType).toBe('ONE_DAY');
  });

  it('returns nothing when there are no prices', () => {
    expect(cheapestPrice([])).toBeUndefined();
  });
});
