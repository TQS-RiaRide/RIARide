import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import type { HubVehicle } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';
import { BIKES_PAGE_SIZE } from './HubVehicles';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function withVehicles(vehicles: Backend['hubVehicles']): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), hubVehicles: vehicles };
}

function bike(code: string, overrides: Partial<HubVehicle> = {}): HubVehicle {
  return {
    code,
    type: 'E_BIKE',
    status: 'AVAILABLE',
    batteryPct: 80,
    bookable: true,
    ...overrides,
  };
}

function rowOf(code: string): HTMLElement {
  const row = screen.getByText(code).closest('li');
  if (!row) throw new Error(`No row for ${code}`);
  return row;
}

describe('HubVehicles', () => {
  it('lists the bookable bikes, the fullest battery first, with their range', async () => {
    const backend = withVehicles(() =>
      Promise.resolve([
        bike('EB-1', { batteryPct: 15, bookable: false }),
        bike('EB-2', { batteryPct: 40 }),
        bike('EB-3', { batteryPct: 100 }),
      ]),
    );
    await renderApp({ path: '/guest', user: 'guest', backend });

    await screen.findByText('EB-3');
    const codes = within(screen.getAllByRole('list')[0])
      .getAllByText(/^EB-\d$/)
      .map((element) => element.textContent);

    expect(codes).toEqual(['EB-3', 'EB-2']);
    expect(within(rowOf('EB-3')).getByText('~ 55 km')).toBeInTheDocument();
    expect(screen.getByText('+1 unavailable')).toBeInTheDocument();
  });

  // BR12: under 20 % a bike cannot be unlocked, like a booked or repaired one.
  it.each([
    bike('EB-9', { batteryPct: 19, bookable: false }),
    bike('EB-9', { status: 'RESERVED', bookable: false }),
    bike('EB-9', { status: 'MAINTENANCE', bookable: false }),
    bike('EB-9', { status: 'IN_USE', batteryPct: null, bookable: false }),
  ])('counts a bike that cannot be unlocked instead of listing it (%#)', async (vehicle) => {
    await renderApp({
      path: '/guest',
      user: 'guest',
      backend: withVehicles(() => Promise.resolve([vehicle])),
    });

    expect(await screen.findByText('+1 unavailable')).toBeInTheDocument();
    expect(screen.queryByText('EB-9')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Unlock EB-9' })).not.toBeInTheDocument();
  });

  it('collapses every bike that cannot be unlocked into one line', async () => {
    const backend = withVehicles(() =>
      Promise.resolve([
        bike('EB-1'),
        bike('EB-2', { status: 'RESERVED', bookable: false }),
        bike('EB-3', { batteryPct: 5, bookable: false }),
      ]),
    );
    await renderApp({ path: '/guest', user: 'guest', backend });

    expect(await screen.findByRole('button', { name: 'Unlock EB-1' })).toBeInTheDocument();
    expect(screen.getByText('+2 unavailable')).toBeInTheDocument();
    expect(screen.queryByText('No bikes here right now')).not.toBeInTheDocument();
  });

  it('shows the range of a bookable bike, with the battery level in its meter', async () => {
    const backend = withVehicles(() => Promise.resolve([bike('EB-1', { batteryPct: 92 })]));
    await renderApp({ path: '/guest', user: 'guest', backend });

    await screen.findByText('EB-1');
    const row = rowOf('EB-1');
    expect(within(row).getByText(/^~ \d+ km$/)).toBeInTheDocument();
    expect(within(row).getByText('92 %')).toBeInTheDocument();
  });

  it('keeps a bike at exactly 20 % bookable', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    await renderApp({ path: '/guest', user: 'guest', backend: demo });

    fireEvent.click(await screen.findByRole('tab', { name: /Centro Histórico/ }));

    expect(await screen.findByRole('button', { name: 'Unlock EB-520' })).toBeEnabled();
    expect(screen.queryByText('EB-521')).not.toBeInTheDocument();
    expect(screen.getByText('+2 unavailable')).toBeInTheDocument();
  });

  it('says when a hub has no bikes', async () => {
    await renderApp({
      path: '/guest',
      user: 'guest',
      backend: withVehicles(() => Promise.resolve([])),
    });

    expect(await screen.findByText('No bikes here right now')).toBeInTheDocument();
    expect(screen.getByText('Try another hub. Bikes come back all day.')).toBeInTheDocument();
  });

  it('says when the bikes could not be loaded', async () => {
    const backend = withVehicles(() =>
      Promise.reject(new ProblemError(500, 'Server error', null, [])),
    );
    await renderApp({ path: '/guest', user: 'guest', backend });

    expect(
      await screen.findByText(
        'We could not load the bikes. Showing the last list we had.',
        {},
        { timeout: 4000 },
      ),
    ).toBeInTheDocument();
  });

  it('marks the bike being unlocked as busy and disables the others', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    await demo.buyPass('STAY', 'pm_card_visa', 'key-1');
    const backend: Backend = { ...demo, startTrip: () => new Promise(() => undefined) };
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-102' }));

    expect(await screen.findByRole('button', { name: 'Unlock EB-102' })).toHaveAttribute(
      'aria-busy',
      'true',
    );
    expect(screen.getByRole('button', { name: 'Unlock EB-101' })).toBeDisabled();
  });
});

// Marés Centro (the first hub) has 13 bookable bikes, EB-701 fullest to EB-713, and 2 that cannot
// be unlocked; Marés Praia has 3 bookable bikes.
const CENTRO_BIKES = Array.from({ length: 13 }, (_, i) =>
  bike(`EB-${701 + i}`, { batteryPct: 95 - i }),
);

function pagedHubs(): Backend {
  return withVehicles((hubId) =>
    Promise.resolve(
      hubId === 'h-centro'
        ? [
            ...[...CENTRO_BIKES].reverse(),
            bike('EB-790', { batteryPct: 10, bookable: false }),
            bike('EB-791', { status: 'MAINTENANCE', bookable: false }),
          ]
        : [bike('EB-801', { batteryPct: 90 }), bike('EB-802'), bike('EB-803', { batteryPct: 70 })],
    ),
  );
}

function shownBikes(): string[] {
  return screen.getAllByText(/^EB-\d{3}$/).map((element) => element.textContent ?? '');
}

function bikePages(): HTMLElement {
  return screen.getByRole('navigation', { name: 'Bikes, pages' });
}

describe('HubVehicles pagination', () => {
  it('shows the first page of bookable bikes with the range and the unavailable count', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: pagedHubs() });

    await screen.findByText('EB-701');

    expect(BIKES_PAGE_SIZE).toBe(8);
    expect(shownBikes()).toEqual(CENTRO_BIKES.slice(0, 8).map((vehicle) => vehicle.code));
    expect(bikePages()).toHaveTextContent('1–8 of 13');
    expect(within(bikePages()).getByRole('button', { name: 'Page 1' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(bikePages()).getByRole('button', { name: 'Page 2' })).toBeInTheDocument();
    expect(screen.getByText('+2 unavailable')).toBeInTheDocument();
  });

  it('shows the remaining bikes on the next page and keeps the unavailable count', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: pagedHubs() });
    await screen.findByText('EB-701');

    fireEvent.click(within(bikePages()).getByRole('button', { name: 'Next page' }));

    expect(shownBikes()).toEqual(['EB-709', 'EB-710', 'EB-711', 'EB-712', 'EB-713']);
    expect(bikePages()).toHaveTextContent('9–13 of 13');
    expect(within(bikePages()).getByRole('button', { name: 'Page 2' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(bikePages()).getByRole('button', { name: 'Next page' })).toBeDisabled();
    expect(screen.getByText('+2 unavailable')).toBeInTheDocument();
  });

  it('draws no pagination at a hub whose bikes fit on one page', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: pagedHubs() });
    await screen.findByText('EB-701');

    fireEvent.click(screen.getByRole('tab', { name: /Marés Praia/ }));

    expect(await screen.findByText('EB-801')).toBeInTheDocument();
    expect(shownBikes()).toEqual(['EB-801', 'EB-802', 'EB-803']);
    expect(screen.queryByRole('navigation', { name: 'Bikes, pages' })).not.toBeInTheDocument();
  });

  it('goes back to the first page when the guest switches hub', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: pagedHubs() });
    await screen.findByText('EB-701');
    fireEvent.click(within(bikePages()).getByRole('button', { name: 'Page 2' }));
    await screen.findByText('EB-713');

    fireEvent.click(screen.getByRole('tab', { name: /Marés Praia/ }));
    await screen.findByText('EB-801');
    fireEvent.click(screen.getByRole('tab', { name: /Marés Centro/ }));

    expect(await screen.findByText('EB-701')).toBeInTheDocument();
    expect(shownBikes()).toEqual(CENTRO_BIKES.slice(0, 8).map((vehicle) => vehicle.code));
    expect(bikePages()).toHaveTextContent('1–8 of 13');
    expect(within(bikePages()).getByRole('button', { name: 'Page 1' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });
});
