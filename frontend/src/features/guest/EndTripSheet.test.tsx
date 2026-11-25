import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

// A demo platform with a ride already confirmed by telemetry on EB-401, taken from Marés Praia.
// Marés Centro is full (12 bikes for 12 slots).
async function ridingBackend(): Promise<{ backend: Backend; tripId: string }> {
  let offsetMs = 0;
  const backend = createDemoBackend({ latencyMs: 0, now: () => Date.now() + offsetMs });
  await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
  const trip = await backend.startTrip('EB-401');
  offsetMs = 3000;
  return { backend, tripId: trip.id };
}

async function openEndSheet(backend: Backend, tripId: string) {
  await renderApp({ path: `/guest/trips/${tripId}`, user: 'guest', backend });
  fireEvent.click(await screen.findByRole('button', { name: 'End trip' }));
  return screen.findByRole('dialog', { name: 'End trip' });
}

function refusingEnd(backend: Backend, error: ProblemError): Backend {
  return { ...backend, endTrip: () => Promise.reject(error) };
}

describe('EndTripSheet', () => {
  // BR10: a full hub stays in the list, disabled, after the hubs with room.
  it('lists the hubs with room first and a full hub as disabled', async () => {
    const { backend, tripId } = await ridingBackend();

    const sheet = await openEndSheet(backend, tripId);

    const hubs = await within(sheet).findAllByRole('radio');
    expect(hubs.at(-1)).toHaveAccessibleName(/^Marés Centro\s*Full right now$/);
    expect(hubs.at(-1)).toBeDisabled();
    expect(within(sheet).getByRole('radio', { name: /Farol da Barra/ })).toHaveTextContent(
      '4 free slots',
    );
    expect(within(sheet).getByRole('button', { name: 'Choose a hub' })).toBeDisabled();
  });

  it('ends the ride at the chosen hub and shows where it was returned', async () => {
    const { backend, tripId } = await ridingBackend();
    const sheet = await openEndSheet(backend, tripId);

    fireEvent.click(await within(sheet).findByRole('radio', { name: /Marés Praia/ }));
    expect(within(sheet).getByRole('radio', { name: /Marés Praia/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    fireEvent.click(within(sheet).getByRole('button', { name: 'End at Marés Praia' }));

    expect(await screen.findByText('Trip ended at Marés Praia.')).toBeInTheDocument();
    expect(await screen.findByText(/^Returned at Marés Praia at/)).toBeInTheDocument();
  });

  it('does not let the guest pick a full hub', async () => {
    const { backend, tripId } = await ridingBackend();
    const sheet = await openEndSheet(backend, tripId);

    fireEvent.click(await within(sheet).findByRole('radio', { name: /Marés Centro/ }));

    expect(within(sheet).getByRole('button', { name: 'Choose a hub' })).toBeDisabled();
  });

  it.each([
    [
      new ProblemError(409, 'Hub full', null, []),
      'That hub filled up a moment ago. Choose one with a free slot.',
    ],
    [new ProblemError(409, 'Trip not in progress', null, []), 'This trip is no longer running.'],
    [
      new ProblemError(404, 'Hub not found', null, []),
      'We could not find that hub. Choose another.',
    ],
  ])('explains a refused end of trip: %s', async (error, message) => {
    const { backend, tripId } = await ridingBackend();
    const sheet = await openEndSheet(refusingEnd(backend, error), tripId);

    fireEvent.click(await within(sheet).findByRole('radio', { name: /Farol da Barra/ }));
    fireEvent.click(within(sheet).getByRole('button', { name: 'End at Farol da Barra' }));

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(message);
  });

  // RIA-55: a hub that filled up meanwhile is refused with the nearest hubs that have room.
  it('suggests the nearest hubs with a free slot when the chosen one filled up', async () => {
    const { backend, tripId } = await ridingBackend();
    const full = new ProblemError(409, 'Hub full', null, [], {
      nearbyHubs: [
        { hubId: 'h-historico', name: 'Centro Histórico', freeSlots: 2, distanceMeters: 89 },
        { hubId: 'h-salinas', name: 'Marés Salinas', freeSlots: 1, distanceMeters: 1625 },
      ],
    });
    const sheet = await openEndSheet(refusingEnd(backend, full), tripId);
    fireEvent.click(await within(sheet).findByRole('radio', { name: /Farol da Barra/ }));
    fireEvent.click(within(sheet).getByRole('button', { name: 'End at Farol da Barra' }));

    const nearest = await within(sheet).findByRole('group', {
      name: 'Nearest hubs with a free slot',
    });
    const suggestions = within(nearest).getAllByRole('button');
    expect(suggestions.map((button) => button.textContent)).toEqual([
      'Centro Histórico89 m · 2 free slots',
      'Marés Salinas1.6 km · 1 free slot',
    ]);

    fireEvent.click(suggestions[1]);

    expect(within(sheet).getByRole('radio', { name: /Marés Salinas/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(sheet).getByRole('button', { name: 'End at Marés Salinas' })).toBeEnabled();
  });

  it('says when there is no hub to choose from', async () => {
    const { backend, tripId } = await ridingBackend();

    const sheet = await openEndSheet({ ...backend, hubs: () => Promise.resolve([]) }, tripId);

    expect(await within(sheet).findByText('No hubs to choose from.')).toBeInTheDocument();
  });
});
