import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import type { Trip } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';
import { TRIPS_PAGE_SIZE } from './TripsPage';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function section(name: string): HTMLElement {
  return screen.getByRole('region', { name });
}

describe('TripsPage', () => {
  it('lists past trips newest first, with where they ended and how long they took', async () => {
    await renderApp({ path: '/guest/trips', user: 'guest' });

    expect(screen.getByRole('heading', { level: 1, name: 'Your trips' })).toBeInTheDocument();
    const links = await within(section('Trips')).findAllByRole('link');
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveTextContent('EB-520 → Marés Praia');
    expect(links[0]).toHaveTextContent('1:24:00');
    expect(links[0]).toHaveAttribute('href', '/guest/trips/trip-past-2');
    expect(links[1]).toHaveTextContent('EB-405 → Centro Histórico');
    expect(links[1]).not.toHaveTextContent('Ended');
  });

  it('shows a running trip without an end hub or a duration', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await backend.startTrip('EB-101');
    await renderApp({ path: '/guest/trips', user: 'guest', backend });

    const links = await within(section('Trips')).findAllByRole('link');

    expect(links).toHaveLength(3);
    expect(links[0]).toHaveTextContent('EB-101');
    expect(links[0]).not.toHaveTextContent('→');
    expect(links[0]).toHaveTextContent('Unlocking');
    expect(links[0]).toHaveTextContent('—');
  });

  it('names no hub when the end hub is not known', async () => {
    const trip: Trip = {
      id: 'trip-x',
      status: 'ENDED',
      vehicleCode: 'EB-777',
      requestedAt: '2026-11-20T10:00:00Z',
      startedAt: '2026-11-20T10:00:00Z',
      endedAt: '2026-11-20T10:30:00Z',
      endHubId: 'h-gone',
    };
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      myTrips: () => Promise.resolve([trip]),
    };
    await renderApp({ path: '/guest/trips', user: 'guest', backend });

    expect(await within(section('Trips')).findByRole('link')).toHaveTextContent('EB-777 → a hub');
  });

  it('says when the guest has no trips and no charges', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      myTrips: () => Promise.resolve([]),
    };
    await renderApp({ path: '/guest/trips', user: 'guest', backend });

    expect(await screen.findByText('No trips yet')).toBeInTheDocument();
    expect(screen.getByText('No charges')).toBeInTheDocument();
  });

  // BR5: the deposit is charged with the pass and refunded at the end.
  it('lists the pass price and the deposit held as charges', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('ONE_DAY', 'pm_card_visa', 'key-1');
    await renderApp({ path: '/guest/trips', user: 'guest', backend });

    const charges = section('Charges');

    expect(await within(charges).findByText('1-day pass')).toBeInTheDocument();
    expect(within(charges).getByText('€10.00')).toBeInTheDocument();
    expect(within(charges).getByText('Deposit, refunded at the end')).toBeInTheDocument();
    expect(within(charges).getByText('Held')).toBeInTheDocument();
    expect(within(charges).getByText('€50.00')).toBeInTheDocument();
  });

  it('opens a trip from the history', async () => {
    await renderApp({ path: '/guest/trips', user: 'guest' });

    fireEvent.click((await within(section('Trips')).findAllByRole('link'))[1]);

    expect(await screen.findByText(/^Returned at Centro Histórico at/)).toBeInTheDocument();
  });
});

// Synthetic ended trips on fictional bikes EB-601, EB-602, ..., trip n on 1 November plus n hours.
function endedTrips(count: number): Trip[] {
  return Array.from({ length: count }, (_, i) => {
    const start = Date.parse('2026-11-01T08:00:00Z') + i * 3_600_000;
    return {
      id: `trip-${i + 1}`,
      status: 'ENDED',
      vehicleCode: `EB-${601 + i}`,
      requestedAt: new Date(start).toISOString(),
      startedAt: new Date(start).toISOString(),
      endedAt: new Date(start + 1_800_000).toISOString(),
      endHubId: 'h-praia',
    };
  });
}

async function renderWithTrips(count: number): Promise<HTMLElement> {
  const backend: Backend = {
    ...createDemoBackend({ latencyMs: 0 }),
    myTrips: () => Promise.resolve(endedTrips(count)),
  };
  await renderApp({ path: '/guest/trips', user: 'guest', backend });
  const trips = section('Trips');
  await within(trips).findAllByRole('link');
  return trips;
}

function shownCodes(trips: HTMLElement): string[] {
  return within(trips)
    .getAllByRole('link')
    .map((link) => /EB-\d+/.exec(link.textContent ?? '')?.[0] ?? '');
}

describe('TripsPage pagination', () => {
  it('shows the newest trips on the first page with the range', async () => {
    const trips = await renderWithTrips(18);

    expect(TRIPS_PAGE_SIZE).toBe(8);
    expect(shownCodes(trips)).toEqual([
      'EB-618',
      'EB-617',
      'EB-616',
      'EB-615',
      'EB-614',
      'EB-613',
      'EB-612',
      'EB-611',
    ]);
    const nav = within(trips).getByRole('navigation', { name: 'Trips, pages' });
    expect(nav).toHaveTextContent('1–8 of 18');
    expect(within(nav).getByRole('button', { name: 'Page 1' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('button', { name: 'Page 3' })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Previous page' })).toBeDisabled();
  });

  it('moves to the next page of older trips', async () => {
    const trips = await renderWithTrips(18);
    const nav = within(trips).getByRole('navigation', { name: 'Trips, pages' });

    fireEvent.click(within(nav).getByRole('button', { name: 'Next page' }));

    expect(shownCodes(trips)).toEqual([
      'EB-610',
      'EB-609',
      'EB-608',
      'EB-607',
      'EB-606',
      'EB-605',
      'EB-604',
      'EB-603',
    ]);
    expect(nav).toHaveTextContent('9–16 of 18');
    expect(within(nav).getByRole('button', { name: 'Page 2' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('shows the oldest trips on the last page', async () => {
    const trips = await renderWithTrips(18);
    const nav = within(trips).getByRole('navigation', { name: 'Trips, pages' });

    fireEvent.click(within(nav).getByRole('button', { name: 'Page 3' }));

    expect(shownCodes(trips)).toEqual(['EB-602', 'EB-601']);
    expect(nav).toHaveTextContent('17–18 of 18');
    expect(within(nav).getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('draws no pagination when the trips fit on one page', async () => {
    const trips = await renderWithTrips(TRIPS_PAGE_SIZE);

    expect(shownCodes(trips)).toHaveLength(TRIPS_PAGE_SIZE);
    expect(within(trips).queryByRole('navigation')).not.toBeInTheDocument();
  });
});
