import { act, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { NetworkError, ProblemError } from '../../backend/problem';
import type { Ticket } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';
import { TicketError } from './api';

// The maintenance tickets workspace (RIA-92): open tickets by SLA deadline, and the form to open one.

const HOUR = 3_600_000;

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function ticket(id: string, code: string, dueInHours: number, extra: Partial<Ticket> = {}): Ticket {
  return {
    id,
    vehicleCode: code,
    status: 'OPEN',
    description: `Fault on ${code}`,
    openedAt: new Date(Date.now() - HOUR).toISOString(),
    slaDueAt: new Date(Date.now() + dueInHours * HOUR).toISOString(),
    vehicleStatus: 'MAINTENANCE',
    maintenancePending: false,
    ...extra,
  };
}

function demoWith(overrides: Partial<Backend>): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), ...overrides };
}

async function ticketList() {
  const panel = await screen.findByRole('region', { name: 'Open tickets' });
  return panel;
}

async function openTicket(code: string, description: string) {
  await act(async () => {
    fireEvent.change(screen.getByLabelText('Bike code'), { target: { value: code } });
    fireEvent.change(screen.getByLabelText('Fault'), { target: { value: description } });
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Open ticket' }));
  });
}

describe('TicketsPage', () => {
  it('lists the open tickets nearest SLA deadline first, whatever order the backend sends', async () => {
    const backend = demoWith({
      tickets: async () => [
        ticket('a', 'EB-118', 20),
        ticket('b', 'EB-602', -2),
        ticket('c', 'EB-233', 3),
      ],
    });
    await renderApp({ path: '/maintenance', user: 'operator', backend });

    const panel = await ticketList();
    await within(panel).findByText('EB-602');
    const codes = within(panel)
      .getAllByRole('listitem')
      .map((item) => item.querySelector('.ticket-code')?.textContent);
    expect(codes).toEqual(['EB-602', 'EB-233', 'EB-118']);
    const [overdue, urgent, ok] = within(panel).getAllByRole('listitem');
    expect(overdue).toHaveClass('ticket-overdue');
    expect(overdue).toHaveTextContent(/late$/);
    expect(urgent).toHaveClass('ticket-urgent');
    expect(urgent).toHaveTextContent(/left$/);
    expect(ok).toHaveClass('ticket-ok');
  });

  it('says when a ticket waits for its bike to end a trip', async () => {
    const backend = demoWith({
      tickets: async () => [ticket('a', 'EB-514', 23, { maintenancePending: true })],
    });
    await renderApp({ path: '/maintenance', user: 'operator', backend });

    const panel = await ticketList();
    expect(
      await within(panel).findByText(
        'The bike is on a trip: it moves to maintenance when the trip ends.',
      ),
    ).toBeInTheDocument();
  });

  it('says when there are no open tickets', async () => {
    await renderApp({
      path: '/maintenance',
      user: 'technician',
      backend: demoWith({ tickets: async () => [] }),
    });

    expect(await screen.findByText('No open tickets')).toBeInTheDocument();
    expect(screen.getByText('Every bike is in service.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Open a ticket' })).not.toBeInTheDocument();
  });

  it('opens a ticket and adds it to the list', async () => {
    await renderApp({ path: '/maintenance', user: 'operator' });
    const panel = await ticketList();
    await within(panel).findByText('EB-602');

    await openTicket('eb-520', 'Chain slips.');

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Ticket opened for EB-520. The bike is in maintenance until it is repaired.',
    );
    expect(await within(panel).findByText('Chain slips.')).toBeInTheDocument();
  });

  it.each([
    ['', '', 'Enter the code of the bike.'],
    ['EB-000', 'Bell.', 'No bike has this code.'],
    ['EB-099', 'Bell.', 'This bike is retired, so it cannot get a ticket.'],
  ])('refuses code %j with %j: %s', async (code, description, message) => {
    await renderApp({ path: '/maintenance', user: 'operator' });
    await ticketList();

    await openTicket(code, description);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it.each([
    [new ProblemError(401, 'Unauthorized', null, []), 'Your session has ended.'],
    [new ProblemError(403, 'Forbidden', null, []), 'Your account cannot open tickets.'],
    [new NetworkError(), 'The ticket could not be opened. Try again.'],
    [new ProblemError(500, 'Server error', null, []), 'The ticket could not be opened. Try again.'],
    [new TicketError('retired'), 'This bike is retired, so it cannot get a ticket.'],
    [
      new ProblemError(400, 'Invalid', null, [
        { name: 'description', reason: null },
        { name: 'other', reason: null },
      ]),
      'Describe the fault in up to 500 characters.',
    ],
  ])('turns the refusal %s into a message', async (error, message) => {
    const backend = demoWith({ openTicket: () => Promise.reject(error) });
    await renderApp({ path: '/maintenance', user: 'admin', backend });
    await ticketList();

    await openTicket('EB-520', 'Bell.');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('focuses the bike code with the t shortcut', async () => {
    await renderApp({ path: '/maintenance', user: 'operator' });
    await ticketList();

    await act(async () => {
      fireEvent.keyDown(window, { key: 't' });
    });

    expect(screen.getByLabelText('Bike code')).toHaveFocus();
  });

  it('names the list for assistive technology without repeating the page heading', async () => {
    await renderApp({
      path: '/maintenance',
      user: 'technician',
      backend: demoWith({ mode: 'live' }),
    });

    const panel = await ticketList();
    expect(within(panel).queryByRole('heading')).not.toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Sample data')).not.toBeInTheDocument();
  });
});
