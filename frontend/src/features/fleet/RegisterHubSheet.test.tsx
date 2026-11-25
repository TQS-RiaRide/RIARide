import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'sonner';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// Registering a hub (RIA-56) from the operator dashboard: local validation, the backend's
// refusals, and the new hub showing in the occupancy panel.

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  toast.dismiss();
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

async function openSheet(backend?: Backend) {
  await renderApp({ path: '/fleet', user: 'operator', backend });
  await screen.findByRole('rowheader', { name: 'EB-101' });
  await act(async () => {
    fireEvent.click(screen.getAllByRole('button', { name: 'Register hub' })[0]);
  });
  return screen.findByRole('dialog', { name: 'Register a hub' });
}

async function fill(sheet: HTMLElement, values: Record<string, string>) {
  await act(async () => {
    for (const [label, value] of Object.entries(values)) {
      fireEvent.change(within(sheet).getByLabelText(label), { target: { value } });
    }
  });
}

async function submit(sheet: HTMLElement) {
  await act(async () => {
    fireEvent.click(within(sheet).getByRole('button', { name: 'Register hub' }));
  });
}

describe('RegisterHubSheet', () => {
  it('asks for a name first when the form is sent empty', async () => {
    const sheet = await openSheet();

    await submit(sheet);

    expect(within(sheet).getByRole('alert')).toHaveTextContent('Give the hub a name.');
    expect(within(sheet).getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true');
    expect(within(sheet).getByLabelText('Latitude')).toHaveAttribute('aria-invalid', 'true');
    expect(within(sheet).getByLabelText('Longitude')).toHaveAttribute('aria-invalid', 'true');
    expect(within(sheet).getByLabelText('Slots')).not.toHaveAttribute('aria-invalid');
  });

  it('refuses a latitude that is not a number', async () => {
    const sheet = await openSheet();
    await fill(sheet, { Name: 'Ponte Nova', Latitude: 'north', Longitude: '-8.65' });

    await submit(sheet);

    expect(within(sheet).getByRole('alert')).toHaveTextContent(
      'Enter the latitude as a number, for example 40.6405.',
    );
  });

  it('refuses a longitude that is missing', async () => {
    const sheet = await openSheet();
    await fill(sheet, { Name: 'Ponte Nova', Latitude: '40.64' });

    await submit(sheet);

    expect(within(sheet).getByRole('alert')).toHaveTextContent(
      'Enter the longitude as a number, for example -8.6538.',
    );
  });

  it.each(['0', '1.5', ''])('refuses %j slots', async (capacity) => {
    const sheet = await openSheet();
    await fill(sheet, {
      Name: 'Ponte Nova',
      Latitude: '40.64',
      Longitude: '-8.65',
      Slots: capacity,
    });

    await submit(sheet);

    expect(within(sheet).getByRole('alert')).toHaveTextContent('The hub needs at least 1 slot.');
    expect(within(sheet).getByLabelText('Slots')).toHaveAttribute('aria-invalid', 'true');
  });

  it('registers a hub with one slot, accepting a decimal comma, and shows it in the occupancy', async () => {
    const registerHub = vi.fn();
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      registerHub: (request) => {
        registerHub(request);
        return demo.registerHub(request);
      },
    };
    const sheet = await openSheet(backend);
    await fill(sheet, {
      Name: '  Ponte Nova ',
      Latitude: '40,6412',
      Longitude: '-8,65',
      Slots: '1',
    });

    await submit(sheet);

    expect(await screen.findByText('Ponte Nova registered with 1 slot.')).toBeInTheDocument();
    expect(registerHub).toHaveBeenCalledWith({
      name: 'Ponte Nova',
      kind: 'TOURIST_SPOT',
      hotel: null,
      latitude: 40.6412,
      longitude: -8.65,
      capacity: 1,
    });
    // jsdom never ends the closing animation, so the closed sheet stays mounted.
    await waitFor(() => expect(sheet).toHaveAttribute('data-state', 'closed'));
    // While the closed sheet stays mounted the page behind it is still aria-hidden.
    expect(await screen.findByText('Ponte Nova')).toBeInTheDocument();
    expect(screen.getByLabelText('0 of 1 slots taken')).toBeInTheDocument();
  });

  it('sends the chosen hotel for a hotel hub', async () => {
    const registerHub = vi.fn();
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = {
      ...demo,
      registerHub: (request) => {
        registerHub(request);
        return demo.registerHub(request);
      },
    };
    const sheet = await openSheet(backend);
    await fill(sheet, { Name: 'Marés Ria', Kind: 'HOTEL' });
    await fill(sheet, {
      Hotel: 'Marés Praia',
      Latitude: '40.64',
      Longitude: '-8.74',
      Slots: '6',
    });

    await submit(sheet);

    expect(await screen.findByText('Marés Ria registered with 6 slots.')).toBeInTheDocument();
    expect(registerHub).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'HOTEL', hotel: 'Marés Praia', capacity: 6 }),
    );
  });

  it('says the name is taken when the backend refuses it with a conflict', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      registerHub: () =>
        Promise.reject(new ProblemError(409, 'Hub name already in use', 'Taken.', [])),
    };
    const sheet = await openSheet(backend);
    await fill(sheet, { Name: 'Farol da Barra', Latitude: '40.64', Longitude: '-8.74' });

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'Another hub already has this name.',
    );
  });

  it('marks the fields the backend refused', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      registerHub: () =>
        Promise.reject(
          new ProblemError(400, 'Invalid hub', null, [{ name: 'capacity', reason: 'too big' }]),
        ),
    };
    const sheet = await openSheet(backend);
    await fill(sheet, { Name: 'Ponte Nova', Latitude: '40.64', Longitude: '-8.74' });

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'The hub needs at least 1 slot.',
    );
    expect(within(sheet).getByLabelText('Slots')).toHaveAttribute('aria-invalid', 'true');
  });

  it('falls back to a general message for a refused field it has no text for', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      registerHub: () =>
        Promise.reject(
          new ProblemError(400, 'Invalid hub', null, [{ name: 'kind', reason: null }]),
        ),
    };
    const sheet = await openSheet(backend);
    await fill(sheet, { Name: 'Ponte Nova', Latitude: '40.64', Longitude: '-8.74' });

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'Check the fields of the hub.',
    );
  });

  it('shows a general message when the backend fails unexpectedly', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      registerHub: () => Promise.reject(new Error('boom')),
    };
    const sheet = await openSheet(backend);
    await fill(sheet, { Name: 'Ponte Nova', Latitude: '40.64', Longitude: '-8.74' });

    await submit(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'Something went wrong. Try again.',
    );
  });
});
