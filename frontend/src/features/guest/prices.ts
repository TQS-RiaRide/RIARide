import type { PassPrice } from '../../backend/types';

/** The cheapest pass, for "from €X" copy. */
export function cheapestPrice(prices: readonly PassPrice[]): PassPrice | undefined {
  return prices.reduce<PassPrice | undefined>(
    (min, price) => (min === undefined || price.priceCents < min.priceCents ? price : min),
    undefined,
  );
}
