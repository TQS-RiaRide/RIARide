import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Ticket } from '../../backend/types';
import i18n from '../../i18n';
import TicketList, { slaTone, TICKETS_PAGE_SIZE, URGENT_HOURS } from './TicketList';

const NOW = Date.parse('2026-11-25T10:00:00Z');
const HOUR = 3_600_000;

function dueIn(ms: number): string {
  return new Date(NOW + ms).toISOString();
}

describe('slaTone', () => {
  it('is overdue once the deadline has passed', () => {
    expect(slaTone(dueIn(-1000), NOW)).toBe('overdue');
  });

  it('is urgent at the deadline itself', () => {
    expect(slaTone(dueIn(0), NOW)).toBe('urgent');
  });

  it('is urgent just under the urgent threshold', () => {
    expect(URGENT_HOURS).toBe(4);
    expect(slaTone(dueIn(URGENT_HOURS * HOUR - 1000), NOW)).toBe('urgent');
  });

  it('is ok at exactly the urgent threshold and beyond', () => {
    expect(slaTone(dueIn(URGENT_HOURS * HOUR), NOW)).toBe('ok');
    expect(slaTone(dueIn(24 * HOUR), NOW)).toBe('ok');
  });
});

// Synthetic tickets on fictional bikes EB-301, EB-302, ..., the n-th due n hours from now.
function tickets(count: number): Ticket[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `t-${i + 1}`,
    vehicleCode: `EB-${301 + i}`,
    status: 'OPEN',
    description: `Fault ${i + 1}`,
    openedAt: new Date(Date.now() - HOUR).toISOString(),
    slaDueAt: new Date(Date.now() + (i + 1) * HOUR).toISOString(),
    vehicleStatus: 'MAINTENANCE',
    maintenancePending: false,
  }));
}

function shownCodes(): string[] {
  return screen.getAllByText(/^EB-3\d\d$/).map((element) => element.textContent ?? '');
}

describe('TicketList pagination', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('shows the first page of tickets, nearest SLA deadline first, with the range', () => {
    const list = tickets(20);
    render(<TicketList tickets={[...list].reverse()} loading={false} />);

    expect(TICKETS_PAGE_SIZE).toBe(8);
    expect(shownCodes()).toEqual(list.slice(0, 8).map((ticket) => ticket.vehicleCode));
    const nav = screen.getByRole('navigation', { name: 'Open tickets, pages' });
    expect(nav).toHaveTextContent('1–8 of 20');
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: 'Page 3' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Page 4' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
  });

  it('moves to the next page of tickets', () => {
    render(<TicketList tickets={tickets(20)} loading={false} />);

    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(shownCodes()).toEqual([
      'EB-309',
      'EB-310',
      'EB-311',
      'EB-312',
      'EB-313',
      'EB-314',
      'EB-315',
      'EB-316',
    ]);
    expect(screen.getByText('9–16 of 20')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: 'Page 1' })).not.toHaveAttribute('aria-current');
  });

  it('shows the remaining tickets on the last page', () => {
    render(<TicketList tickets={tickets(20)} loading={false} />);

    fireEvent.click(screen.getByRole('button', { name: 'Page 3' }));

    expect(shownCodes()).toEqual(['EB-317', 'EB-318', 'EB-319', 'EB-320']);
    expect(screen.getByText('17–20 of 20')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('draws no pagination when the tickets fit on one page', () => {
    render(<TicketList tickets={tickets(TICKETS_PAGE_SIZE)} loading={false} />);

    expect(shownCodes()).toHaveLength(TICKETS_PAGE_SIZE);
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('never pages the compact list of the fleet dashboard', () => {
    render(<TicketList tickets={tickets(20)} loading={false} compact />);

    expect(shownCodes()).toHaveLength(20);
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });
});
