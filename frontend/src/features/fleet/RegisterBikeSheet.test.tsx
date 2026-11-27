import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'sonner';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// Registering an e-bike at a hub with a free slot (RIA-57), one at a time or several in a row.

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  toast.dismiss();
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

async function openSheet(path = '/fleet', backend?: Backend) {
  await renderApp({ path, user: 'operator', backend });
  await screen.findAllByRole('rowheader');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Register bike' }));
  });
  return screen.findByRole('dialog', { name: 'Register a bike' });
}

async function typeCode(sheet: HTMLElement, code: string) {
  await act(async () => {
    fireEvent.change(within(sheet).getByLabelText('Bike code'), { target: { value: code } });
  });
}

async function submit(sheet: HTMLElement) {
  await act(async () => {
    fireEvent.click(within(sheet).getByRole('button', { name: 'Register bike' }));
  });
}

describe('RegisterBikeSheet', () => {
  it('offers only hubs with a free slot, the first one chosen, and waits for a code', async () => {
    const sheet = await openSheet();

    const hub = within(sheet).getByLabelText('Hub');
    await waitFor(() => expect(hub).toHaveDisplayValue('Marés Praia · 3 free slots'));
    expect(
      within(hub)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).not.toContain(expect.stringMatching(/^Marés Centro/));
    expect(within(sheet).getByRole('button', { name: 'Register bike' })).toBeDisabled();

    await typeCode(sheet, '   ');

    expect(within(sheet).getByRole('button', { name: 'Register bike' })).toBeDisabled();
  });

  it('starts at the hub the dashboard is filtered by', async () => {
    const sheet = await openSheet('/fleet?hub=h-farol');

    expect(within(sheet).getByLabelText('Hub')).toHaveDisplayValue('Farol da Barra · 4 free slots');
  });

  it('registers a bike in upper case at the chosen hub and closes', async () => {
    const sheet = await openSheet();
    await act(async () => {
      fireEvent.change(within(sheet).getByLabelText('Hub'), { target: { value: 'h-salinas' } });
    });
    await typeCode(sheet, ' eb-800 ');

    await submit(sheet);

    expect(await screen.findByText('EB-800 registered.')).toBeInTheDocument();
    await waitFor(() => expect(sheet).toHaveAttribute('data-state', 'closed'));
    expect(await screen.findByText('1–12 of 30')).toBeInTheDocument();
  });

  it('stays open with an empty code to add another bike', async () => {
    const sheet = await openSheet();
    await act(async () => {
      fireEvent.click(within(sheet).getByLabelText('Add another'));
    });
    await typeCode(sheet, 'EB-801');

    await submit(sheet);

    expect(await screen.findByText('EB-801 registered.')).toBeInTheDocument();
    expect(sheet).toHaveAttribute('data-state', 'open');
    expect(within(sheet).getByLabelText('Bike code')).toHaveValue('');
  });

  it('says another bike has the code when the backend refuses it', async () => {
    const sheet = await openSheet();
    await typeCode(sheet, 'EB-101');

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'Another bike already has this code.',
    );
    expect(within(sheet).getByLabelText('Bike code')).toHaveAttribute('aria-invalid', 'true');
  });

  it('explains the code format when the backend finds it invalid', async () => {
    const sheet = await openSheet();
    await typeCode(sheet, 'E');

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'Use letters, digits and hyphens, for example EB-701.',
    );
  });

  it('asks for another hub when the chosen one filled up meanwhile', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      registerVehicle: () => Promise.reject(new ProblemError(409, 'Hub full', null, [])),
    };
    const sheet = await openSheet('/fleet', backend);
    await typeCode(sheet, 'EB-802');

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'This hub has no free slot. Choose another hub.',
    );
  });

  it('says every hub is full when none has a free slot', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      hubs: async () => (await demo.hubs()).map((hub) => ({ ...hub, freeSlots: 0 })),
    };
    const sheet = await openSheet('/fleet', backend);

    expect(
      await within(sheet).findByText('Every hub is full. Free a slot or register a hub first.'),
    ).toBeInTheDocument();
    expect(within(sheet).getByLabelText('Hub')).toBeDisabled();
    await typeCode(sheet, 'EB-803');
    expect(within(sheet).getByRole('button', { name: 'Register bike' })).toBeDisabled();
  });
});
