import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import type { Stay } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';
import { STAYS_PAGE_SIZE } from './BackofficePage';

// The hotel group's backoffice (scenario 3): the stays with their codes and the base prices.

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

describe('BackofficePage', () => {
  it('lists the stays with their hotel and code', async () => {
    await renderApp({ path: '/hotel', user: 'manager' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Hotel backoffice' }),
    ).toBeInTheDocument();
    const stays = screen.getByRole('region', { name: 'Stays' });
    expect(await within(stays).findByText('Sophie Laurent')).toBeInTheDocument();
    expect(within(stays).getByText('PRAIA-7K4Q')).toBeInTheDocument();
    expect(within(stays).getByText('CENTRO-8H3T')).toBeInTheDocument();
    expect(within(stays).getAllByText(/^Marés Praia · /)).toHaveLength(2);
  });

  it('shows the base price of each pass and the deposit', async () => {
    await renderApp({ path: '/hotel', user: 'manager' });

    const prices = screen.getByRole('region', { name: 'Base prices of the passes' });
    expect(await within(prices).findByText('1-day pass')).toBeInTheDocument();
    expect(within(prices).getByText('€10.00')).toBeInTheDocument();
    expect(within(prices).getByText('3-day pass')).toBeInTheDocument();
    expect(within(prices).getByText('€25.00')).toBeInTheDocument();
    expect(within(prices).getByText('Stay pass')).toBeInTheDocument();
    expect(within(prices).getByText('€40.00')).toBeInTheDocument();
    expect(within(prices).getByText('Deposit (refundable)')).toBeInTheDocument();
    expect(within(prices).getByText('€50.00')).toBeInTheDocument();
  });

  it('says the list of stays is empty', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const empty: Backend = { ...demo, stays: async () => [] };
    await renderApp({ path: '/hotel', user: 'manager', backend: empty });

    expect(await screen.findByText('No stays yet')).toBeInTheDocument();
  });

  it('names a stay of a hotel outside the group with a dash', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      stays: async () => [
        {
          id: 's-x',
          hotelId: 'unknown',
          guestName: 'Ana Costa',
          stayCode: 'X-AAAA',
          checkIn: '2026-11-25',
          checkOut: '2026-11-27',
          status: 'ACTIVE',
        },
      ],
    };
    await renderApp({ path: '/hotel', user: 'manager', backend });

    expect(await screen.findByText('Ana Costa')).toBeInTheDocument();
    expect(screen.getByText(/^— · /)).toBeInTheDocument();
  });
});

// Synthetic stays at Marés Praia for fictional guests Guest 01, Guest 02, ...
function stays(count: number): Stay[] {
  return Array.from({ length: count }, (_, i) => {
    const n = String(i + 1).padStart(2, '0');
    return {
      id: `s-${n}`,
      hotelId: 'h-praia',
      guestName: `Guest ${n}`,
      stayCode: `PRAIA-T0${n}`,
      checkIn: '2026-11-25',
      checkOut: '2026-11-28',
      status: 'ACTIVE',
    };
  });
}

async function renderWithStays(count: number): Promise<HTMLElement> {
  const backend: Backend = {
    ...createDemoBackend({ latencyMs: 0 }),
    stays: async () => stays(count),
  };
  await renderApp({ path: '/hotel', user: 'manager', backend });
  const region = screen.getByRole('region', { name: 'Stays' });
  await within(region).findByText('Guest 01');
  return region;
}

function shownGuests(region: HTMLElement): string[] {
  return within(region)
    .getAllByText(/^Guest \d\d$/)
    .map((element) => element.textContent ?? '');
}

describe('BackofficePage stays pagination', () => {
  it('shows the first page of stays with the range', async () => {
    const region = await renderWithStays(14);

    expect(STAYS_PAGE_SIZE).toBe(6);
    expect(shownGuests(region)).toEqual([
      'Guest 01',
      'Guest 02',
      'Guest 03',
      'Guest 04',
      'Guest 05',
      'Guest 06',
    ]);
    const nav = within(region).getByRole('navigation', { name: 'Stays, pages' });
    expect(nav).toHaveTextContent('1–6 of 14');
    expect(within(nav).getByRole('button', { name: 'Page 1' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('button', { name: 'Page 3' })).toBeInTheDocument();
    expect(within(nav).queryByRole('button', { name: 'Page 4' })).not.toBeInTheDocument();
  });

  it('moves to the next page of stays', async () => {
    const region = await renderWithStays(14);
    const nav = within(region).getByRole('navigation', { name: 'Stays, pages' });

    fireEvent.click(within(nav).getByRole('button', { name: 'Next page' }));

    expect(shownGuests(region)).toEqual([
      'Guest 07',
      'Guest 08',
      'Guest 09',
      'Guest 10',
      'Guest 11',
      'Guest 12',
    ]);
    expect(nav).toHaveTextContent('7–12 of 14');
    expect(within(nav).getByRole('button', { name: 'Page 2' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('shows the remaining stays on the last page', async () => {
    const region = await renderWithStays(14);
    const nav = within(region).getByRole('navigation', { name: 'Stays, pages' });

    fireEvent.click(within(nav).getByRole('button', { name: 'Page 3' }));

    expect(shownGuests(region)).toEqual(['Guest 13', 'Guest 14']);
    expect(nav).toHaveTextContent('13–14 of 14');
    expect(within(nav).getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('draws no pagination when the stays fit on one page', async () => {
    const region = await renderWithStays(STAYS_PAGE_SIZE);

    expect(shownGuests(region)).toHaveLength(STAYS_PAGE_SIZE);
    expect(within(region).queryByRole('navigation')).not.toBeInTheDocument();
  });
});
