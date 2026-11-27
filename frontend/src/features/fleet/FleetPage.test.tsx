import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'sonner';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import type { Vehicle } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// Screen tests of the operator dashboard (RIA-120): filters kept in the URL, hub selection, the
// optimistic status change with undo, and opening a ticket from a bike's row.

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  toast.dismiss();
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function demoWith(overrides: Partial<Backend>): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), ...overrides };
}

async function bikesTable() {
  return screen.findByRole('table');
}

function shownCodes(table: HTMLElement): string[] {
  return within(table)
    .queryAllByRole('rowheader')
    .map((cell) => cell.textContent ?? '');
}

// The fleet has 29 bikes; the table shows them 12 at a time.
function expectEveryBike(table: HTMLElement) {
  expect(shownCodes(table)).toHaveLength(12);
  expect(screen.getByText('1–12 of 29')).toBeInTheDocument();
}

async function findRow(code: string): Promise<HTMLElement> {
  await screen.findByRole('rowheader', { name: code });
  return rowOf(code);
}

function rowOf(code: string): HTMLElement {
  const row = screen.getByRole('rowheader', { name: code }).closest('tr');
  if (!row) throw new Error(`No row for ${code}`);
  return row;
}

async function openRowMenu(code: string) {
  const trigger = await screen.findByRole('button', { name: `Actions for ${code}` });
  await act(async () => {
    fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false });
  });
  return screen.findByRole('menu');
}

async function chooseStatus(code: string, status: string) {
  await openRowMenu(code);
  await act(async () => {
    fireEvent.click(screen.getByRole('menuitem', { name: 'Change status' }));
  });
  const item = await screen.findByRole('menuitem', { name: status });
  await act(async () => {
    fireEvent.click(item);
  });
}

describe('FleetPage', () => {
  it('shows every bike, hub occupancy, the redistribution tip and the nearest SLA tickets', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });

    const table = await bikesTable();
    expect(await within(table).findByRole('rowheader', { name: 'EB-101' })).toBeInTheDocument();
    expectEveryBike(table);
    expect(
      screen.getByText('Move 3 bikes from Marés Centro to Marés Salinas.'),
    ).toBeInTheDocument();
    const centro = screen.getByRole('button', { name: /^Marés Centro/ });
    expect(centro).toHaveTextContent('Full');
    expect(within(centro).getByLabelText('12 of 12 slots taken')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Marés Salinas/ })).toHaveTextContent(
      'Almost empty',
    );
    const sla = screen.getByRole('region', { name: 'Tickets by SLA deadline' });
    expect(within(sla).getByText('Rear brake does not hold.')).toBeInTheDocument();
    expect(within(sla).getByRole('link', { name: 'All tickets' })).toBeInTheDocument();
    expect(await screen.findByText(/^Updated /)).toBeInTheDocument();
  });

  it('filters the bikes by the search in the URL and by what the operator types', async () => {
    await renderApp({ path: '/fleet?q=eb-10', user: 'operator' });

    const table = await bikesTable();
    await within(table).findByRole('rowheader', { name: 'EB-101' });
    expect(screen.getByRole('searchbox', { name: 'Code or hub' })).toHaveValue('eb-10');
    expect(shownCodes(table)).toEqual([
      'EB-101',
      'EB-102',
      'EB-103',
      'EB-104',
      'EB-105',
      'EB-106',
      'EB-107',
      'EB-108',
      'EB-109',
    ]);

    await act(async () => {
      fireEvent.change(screen.getByRole('searchbox', { name: 'Code or hub' }), {
        target: { value: 'farol' },
      });
    });

    expect(shownCodes(table)).toEqual(['EB-701', 'EB-702']);
  });

  it('tells the operator when no bike matches and lets the search be cleared', async () => {
    await renderApp({ path: '/fleet?q=zz-999', user: 'operator' });

    expect(await screen.findByText('No bike matches')).toBeInTheDocument();
    expect(screen.getByText('Clear the search or pick another filter.')).toBeInTheDocument();

    await act(async () => {
      fireEvent.change(screen.getByRole('searchbox', { name: 'Code or hub' }), {
        target: { value: '' },
      });
    });

    expect(await screen.findByRole('rowheader', { name: 'EB-101' })).toBeInTheDocument();
    expect(screen.queryByText('No bike matches')).not.toBeInTheDocument();
  });

  it('applies the status filter from the URL and switches it from the chips', async () => {
    await renderApp({ path: '/fleet?status=LOW_BATTERY', user: 'operator' });

    const table = await bikesTable();
    await within(table).findByRole('rowheader', { name: 'EB-372' });
    expect(screen.getByRole('button', { name: /^Low battery/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(shownCodes(table)).toEqual(['EB-372', 'EB-521']);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /^Maintenance/ }));
    });
    expect(shownCodes(table)).toEqual(['EB-233', 'EB-118', 'EB-602']);
    expect(screen.getByRole('button', { name: /^Maintenance/ })).toHaveTextContent('3');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'All' }));
    });
    expectEveryBike(table);
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('ignores an unknown status in the URL and shows every bike', async () => {
    await renderApp({ path: '/fleet?status=BROKEN', user: 'operator' });

    const table = await bikesTable();
    await within(table).findByRole('rowheader', { name: 'EB-101' });
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true');
    expectEveryBike(table);
  });

  it('filters the bikes by the hub in the URL until the hub chip is cleared', async () => {
    await renderApp({ path: '/fleet?hub=h-farol', user: 'operator' });

    const table = await bikesTable();
    const chip = await screen.findByRole('button', { name: /Farol da Barra.*Show every hub/ });
    expect(shownCodes(table)).toEqual(['EB-701', 'EB-702']);

    await act(async () => {
      fireEvent.click(chip);
    });

    expect(screen.queryByRole('button', { name: /Show every hub/ })).not.toBeInTheDocument();
    expectEveryBike(table);
  });

  it('filters the table to a hub when its occupancy row is selected, and back when unselected', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    const table = await bikesTable();
    await within(table).findByRole('rowheader', { name: 'EB-101' });
    const historico = screen.getByRole('button', { name: /^Centro Histórico/ });

    await act(async () => {
      fireEvent.click(historico);
    });

    expect(historico).toHaveAttribute('aria-pressed', 'true');
    expect(shownCodes(table)).toEqual(['EB-602', 'EB-520', 'EB-521', 'EB-522', 'EB-523']);

    await act(async () => {
      fireEvent.click(historico);
    });

    expect(historico).toHaveAttribute('aria-pressed', 'false');
    expectEveryBike(table);
  });

  it('changes a bike status at once and puts it back with the undo toast', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await bikesTable();
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await chooseStatus('EB-101', 'Maintenance');

    expect(await screen.findByText('EB-101 is now Maintenance.')).toBeInTheDocument();
    expect(within(rowOf('EB-101')).getByText('Maintenance')).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Undo' }));
    });

    expect(await screen.findByText('EB-101 is back as it was.')).toBeInTheDocument();
    await waitFor(() => expect(within(rowOf('EB-101')).getByText('Available')).toBeInTheDocument());
  });

  it('says a bike in use is on a trip, and a bike with no telemetry has no reading', async () => {
    await renderApp({ path: '/fleet?q=EB-514', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-514' });
    expect(within(rowOf('EB-514')).getByText('On a trip')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'EB-099' } });

    expect(within(await findRow('EB-099')).getByText('No reading')).toBeInTheDocument();
  });

  it('offers no undo when the lifecycle does not allow going back', async () => {
    await renderApp({ path: '/fleet?q=EB-514', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-514' });

    await chooseStatus('EB-514', 'Available');

    expect(await screen.findByText('EB-514 is now Available.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();
  });

  it('shows the new status before the backend answers and rolls it back when refused', async () => {
    let refuse: (error: Error) => void = () => undefined;
    const backend = demoWith({
      changeVehicleStatus: () =>
        new Promise<Vehicle>((_resolve, reject) => {
          refuse = reject;
        }),
    });
    await renderApp({ path: '/fleet', user: 'operator', backend });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await chooseStatus('EB-101', 'Retired');

    await waitFor(() => expect(within(rowOf('EB-101')).getByText('Retired')).toBeInTheDocument());

    await act(async () => {
      refuse(new ProblemError(409, 'Status change refused', null, []));
    });

    expect(await screen.findByText('EB-101 cannot change to that status now.')).toBeInTheDocument();
    await waitFor(() => expect(within(rowOf('EB-101')).getByText('Available')).toBeInTheDocument());
  });

  it('says the bike no longer exists when the backend does not find it', async () => {
    const backend = demoWith({
      changeVehicleStatus: () =>
        Promise.reject(new ProblemError(404, 'Vehicle not found', null, [])),
    });
    await renderApp({ path: '/fleet', user: 'operator', backend });
    await screen.findByRole('rowheader', { name: 'EB-102' });

    await chooseStatus('EB-102', 'Maintenance');

    expect(await screen.findByText('EB-102 no longer exists.')).toBeInTheDocument();
  });

  it('does not let a retired bike change status or get a ticket', async () => {
    await renderApp({ path: '/fleet?q=EB-099', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-099' });

    await openRowMenu('EB-099');

    expect(screen.getByRole('menuitem', { name: 'Change status' })).toHaveAttribute(
      'data-disabled',
    );
    expect(screen.getByRole('menuitem', { name: 'Open a ticket' })).toHaveAttribute(
      'data-disabled',
    );
  });

  it('does not let a bike unknown to this browser change status', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      fleet: async () =>
        (await demo.fleet()).map((v) => (v.code === 'EB-101' ? { ...v, id: null } : v)),
    };
    await renderApp({ path: '/fleet', user: 'operator', backend });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await openRowMenu('EB-101');

    expect(screen.getByRole('menuitem', { name: 'Change status' })).toHaveAttribute(
      'data-disabled',
    );
  });

  it('opens a ticket for a bike from its row, with the code filled in', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await openRowMenu('EB-101');
    await act(async () => {
      fireEvent.click(screen.getByRole('menuitem', { name: 'Open a ticket' }));
    });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Bike code')).toHaveValue('EB-101');
  });

  it('answers the page shortcuts: / focuses the search, b and n open the sheets', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await act(async () => {
      fireEvent.keyDown(window, { key: '/' });
    });
    expect(screen.getByRole('searchbox', { name: 'Code or hub' })).toHaveFocus();

    act(() => {
      screen.getByRole('searchbox', { name: 'Code or hub' }).blur();
    });
    await act(async () => {
      fireEvent.keyDown(window, { key: 'b' });
    });
    expect(await screen.findByRole('dialog', { name: 'Register a bike' })).toBeInTheDocument();
  });

  it('opens the hub sheet with the n shortcut', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await act(async () => {
      fireEvent.keyDown(window, { key: 'n' });
    });

    expect(await screen.findByRole('dialog', { name: 'Register a hub' })).toBeInTheDocument();
  });

  it('shows no sample-data marks or notes for the live adapter', async () => {
    const backend = demoWith({ mode: 'live' });
    await renderApp({ path: '/fleet', user: 'operator', backend });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    expect(screen.queryByText('Sample data')).not.toBeInTheDocument();
    expect(screen.queryByText(/registered from this browser/)).not.toBeInTheDocument();
  });

  it('keeps a static dot next to the update time, with no pulsing animation class', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByText(/^Updated /);

    const dot = document.querySelector('.updated-dot');
    expect(dot).not.toBeNull();
    expect(document.querySelector('.live-dot')).toBeNull();
  });

  it('shows every status chip in a fixed order, muting the ones with no bike', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      fleet: async () => (await demo.fleet()).filter((v) => v.status === 'AVAILABLE'),
    };
    await renderApp({ path: '/fleet', user: 'operator', backend });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    const toolbar = screen.getByRole('toolbar', { name: 'Filter bikes' });
    const chips = within(toolbar).getAllByRole('button');
    expect(chips.map((chip) => chip.firstChild?.textContent)).toEqual([
      'All',
      'Available',
      'Reserved',
      'In use',
      'Maintenance',
      'Retired',
      'Low battery',
    ]);
    const reserved = within(toolbar).getByRole('button', { name: /^Reserved/ });
    expect(reserved).toHaveTextContent('0');
    expect(reserved).toBeDisabled();
    expect(reserved).toHaveClass('is-empty');
    expect(within(toolbar).getByRole('button', { name: /^Available/ })).toBeEnabled();
  });

  it('keeps a chosen status chip usable when its count is zero', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      fleet: async () => (await demo.fleet()).filter((v) => v.status === 'AVAILABLE'),
    };
    await renderApp({ path: '/fleet?status=RETIRED', user: 'operator', backend });

    expect(await screen.findByText('No bike matches')).toBeInTheDocument();
    const retired = screen.getByRole('button', { name: /^Retired/ });
    expect(retired).toHaveAttribute('aria-pressed', 'true');
    expect(retired).toBeEnabled();
  });

  it('shows the bikes 12 at a time and moves between pages', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    const table = await bikesTable();
    await within(table).findByRole('rowheader', { name: 'EB-101' });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    });

    expect(screen.getByText('13–24 of 29')).toBeInTheDocument();
    expect(within(table).queryByRole('rowheader', { name: 'EB-101' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');
  });

  it('opens the page given in the URL, and the last page for a page past the end', async () => {
    await renderApp({ path: '/fleet?page=9', user: 'operator' });
    const table = await bikesTable();

    expect(await screen.findByText('25–29 of 29')).toBeInTheDocument();
    expect(shownCodes(table)).toHaveLength(5);
  });

  it('goes back to the first page when a filter changes', async () => {
    await renderApp({ path: '/fleet?page=3', user: 'operator' });
    await screen.findByText('25–29 of 29');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /^Available/ }));
    });

    expect(screen.getByText('1–12 of 22')).toBeInTheDocument();
  });

  it('shows no page buttons when the filtered bikes fit on one page', async () => {
    await renderApp({ path: '/fleet?status=MAINTENANCE', user: 'operator' });
    await bikesTable();

    expect(screen.queryByRole('navigation', { name: 'Bikes, pages' })).not.toBeInTheDocument();
  });

  it('offers to register the first hub and explains an empty fleet', async () => {
    const backend = demoWith({ hubs: async () => [], fleet: async () => [] });
    await renderApp({ path: '/fleet', user: 'operator', backend });

    expect(await screen.findByText('No hubs yet')).toBeInTheDocument();
    expect(screen.getByText('No bikes yet')).toBeInTheDocument();
    const hubs = screen.getByRole('region', { name: 'Hub occupancy' });

    await act(async () => {
      fireEvent.click(within(hubs).getByRole('button', { name: 'Register hub' }));
    });

    expect(await screen.findByRole('dialog', { name: 'Register a hub' })).toBeInTheDocument();
  });
});
