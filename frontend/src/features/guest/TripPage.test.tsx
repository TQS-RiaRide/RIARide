import { act, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import type { Trip } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

const HOUR_MS = 3_600_000;

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function tripReturning(trip: Trip): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), trip: () => Promise.resolve(trip) };
}

function ridingTrip(startedMsAgo: number): Trip {
  const startedAt = new Date(Date.now() - startedMsAgo).toISOString();
  return {
    id: 'trip-1',
    status: 'IN_USE',
    vehicleCode: 'EB-401',
    requestedAt: startedAt,
    startedAt,
    endedAt: null,
    endHubId: null,
  };
}

describe('TripPage', () => {
  // BR13: the page waits for telemetry to confirm the unlock, then shows the running clock (BR7).
  it('shows the unlock in progress, then the ride once telemetry confirms it', async () => {
    let offsetMs = 0;
    const backend = createDemoBackend({ latencyMs: 0, now: () => Date.now() + offsetMs });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    const trip = await backend.startTrip('EB-401');
    await renderApp({ path: `/guest/trips/${trip.id}`, user: 'guest', backend });

    expect(await screen.findByRole('heading', { level: 1, name: /Unlocking/ })).toBeInTheDocument();
    expect(screen.getByText('Unlocking…')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'End trip' })).not.toBeInTheDocument();

    act(() => {
      offsetMs = 3000;
    });

    expect(
      await screen.findByRole('heading', { level: 1, name: /Riding/ }, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(screen.getByText(/^(3h|2h 59m) left of the 3\s?h included\.$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Time on the bike:/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'End trip' })).toBeInTheDocument();
  });

  // BR7: beyond 3 h the extra time is overtime.
  it('shows the overtime of a ride longer than the 3 h included', async () => {
    await renderApp({
      path: '/guest/trips/trip-1',
      user: 'guest',
      backend: tripReturning(ridingTrip(4 * HOUR_MS + 30 * 60_000)),
    });

    expect(
      await screen.findByText('Overtime: 1h 30m, charged every 15 minutes.'),
    ).toBeInTheDocument();
  });

  it('tells the guest a bike that did not confirm the unlock is still booked for them', async () => {
    const failed: Trip = { ...ridingTrip(0), status: 'FAILED', startedAt: null };
    await renderApp({ path: '/guest/trips/trip-1', user: 'guest', backend: tripReturning(failed) });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "The bike didn't unlock. Your pass wasn't used; EB-401 is held for you for 15 minutes.",
    );
    expect(screen.getAllByRole('link', { name: 'Back to bikes' })).toHaveLength(1);
  });

  it('shows where and when an ended trip was returned', async () => {
    await renderApp({ path: '/guest/trips/trip-past-1', user: 'guest' });

    expect(await screen.findByRole('heading', { level: 1, name: /Ended/ })).toBeInTheDocument();
    expect(
      await screen.findByText(/^Returned at Centro Histórico at \d{1,2}:\d{2}\s?[AP]M\.$/),
    ).toBeInTheDocument();
    expect(screen.getByText('0:48:00')).toBeInTheDocument();
  });

  it('names no hub when the end hub is unknown', async () => {
    const ended: Trip = {
      ...ridingTrip(HOUR_MS),
      status: 'ENDED',
      endedAt: new Date().toISOString(),
      endHubId: 'h-gone',
    };
    await renderApp({ path: '/guest/trips/trip-1', user: 'guest', backend: tripReturning(ended) });

    expect(await screen.findByText(/^Returned at a hub at/)).toBeInTheDocument();
  });

  it('says the trip could not be found', async () => {
    const backend: Backend = {
      ...createDemoBackend({ latencyMs: 0 }),
      trip: () => Promise.reject(new ProblemError(404, 'Trip not found', null, [])),
    };
    await renderApp({ path: '/guest/trips/nope', user: 'guest', backend });

    expect(
      await screen.findByText('We could not find this trip', {}, { timeout: 4000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('It may belong to another account.')).toBeInTheDocument();
  });

  it('opens the sheet to end the ride at a hub', async () => {
    await renderApp({
      path: '/guest/trips/trip-1',
      user: 'guest',
      backend: tripReturning(ridingTrip(10 * 60_000)),
    });

    fireEvent.click(await screen.findByRole('button', { name: 'End trip' }));

    expect(await screen.findByRole('dialog', { name: 'End trip' })).toBeInTheDocument();
  });
});
