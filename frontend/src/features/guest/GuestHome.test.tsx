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

async function hubTab(name: RegExp) {
  return screen.findByRole('tab', { name });
}

describe('GuestHome', () => {
  it('greets the guest by first name and lists the bikes of the first hub', async () => {
    await renderApp({ path: '/guest', user: 'guest' });

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: /^Good (morning|afternoon|evening), Sophie\.$/,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Bikes' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Unlock EB-101' })).toBeInTheDocument();
    expect(await hubTab(/Marés Centro/)).toHaveAttribute('aria-selected', 'true');
  });

  it('shows the anonymous greeting when the user has no name', async () => {
    await renderApp({
      path: '/guest',
      user: {
        id: 'g',
        name: null,
        email: 'g@example.test',
        roles: ['GUEST'],
        workspaces: ['GUEST_DASHBOARD'],
      },
    });

    expect(screen.getByRole('heading', { level: 1, name: 'Where to today?' })).toBeInTheDocument();
  });

  // BR12: a bike below 20 % battery cannot be unlocked; with the booked and repaired ones it is
  // only counted.
  it('counts the bikes of another hub that cannot be unlocked', async () => {
    await renderApp({ path: '/guest', user: 'guest' });

    fireEvent.click(await hubTab(/Marés Praia/));

    expect(await screen.findByRole('button', { name: 'Unlock EB-401' })).toBeEnabled();
    expect(screen.getByText('+3 unavailable')).toBeInTheDocument();
    for (const code of ['EB-372', 'EB-118', 'EB-409']) {
      expect(screen.queryByText(code)).not.toBeInTheDocument();
    }
  });

  it('opens the hub given in the address', async () => {
    await renderApp({ path: '/guest?hub=h-farol', user: 'guest' });

    expect(await screen.findByRole('button', { name: 'Unlock EB-701' })).toBeInTheDocument();
    expect(await hubTab(/Farol da Barra/)).toHaveAttribute('aria-selected', 'true');
  });

  it('offers a pass, from the cheapest price, as the main action of a guest without one', async () => {
    await renderApp({ path: '/guest', user: 'guest' });

    expect(
      await screen.findByRole('button', { name: 'Buy a pass from €10.00' }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Unlock EB-101' })).toHaveClass(
      'btn-secondary',
    );
  });

  it('opens the pass sheet instead of failing when a guest without a pass unlocks a bike', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = { ...demo, startTrip: vi.fn(demo.startTrip) };
    await renderApp({ path: '/guest', user: 'guest', backend });
    await screen.findByRole('button', { name: /Buy a pass from/ });

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-101' }));

    expect(await screen.findByRole('dialog', { name: 'Choose your pass' })).toBeInTheDocument();
    expect(backend.startTrip).not.toHaveBeenCalled();
  });

  it('draws Unlock as the main action once the guest has a pass', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await renderApp({ path: '/guest', user: 'guest', backend });

    expect(await screen.findByRole('button', { name: 'Unlock EB-101' })).toHaveClass('btn-primary');
  });

  it('offers to buy a pass when the backend says the pass is not valid', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    await demo.buyPass('STAY', 'pm_card_visa', 'key-1');
    const backend: Backend = {
      ...demo,
      startTrip: vi.fn().mockRejectedValue(new ProblemError(403, 'No valid pass', null, [])),
    };
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-101' }));

    expect(await screen.findByText('Buy a pass before your first trip.')).toBeInTheDocument();
    const toastAction = screen
      .getAllByRole('button', { name: 'Buy a pass' })
      .find((button) => button.closest('[data-sonner-toast]') !== null);
    expect(toastAction).toBeDefined();
    fireEvent.click(toastAction as HTMLElement);
    expect(await screen.findByRole('dialog', { name: 'Choose your pass' })).toBeInTheDocument();
  });

  it('starts a trip from a bike in the list and opens the trip page', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-101' }));

    expect(await screen.findByText('Unlocking')).toBeInTheDocument();
    expect(screen.getByText('EB-101')).toBeInTheDocument();
  });

  it('starts a trip from a bike code typed in the sheet', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(screen.getByRole('button', { name: 'Use a bike code' }));
    const sheet = await screen.findByRole('dialog', { name: 'Start with a bike code' });
    fireEvent.change(within(sheet).getByLabelText('Bike code'), { target: { value: ' eb-401 ' } });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Unlock' }));

    expect(await screen.findByText('Unlocking')).toBeInTheDocument();
    expect(screen.getByText('EB-401')).toBeInTheDocument();
  });

  it('says that no bike has a code that does not exist', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(screen.getByRole('button', { name: 'Use a bike code' }));
    const sheet = await screen.findByRole('dialog', { name: 'Start with a bike code' });
    fireEvent.change(within(sheet).getByLabelText('Bike code'), { target: { value: 'XX-000' } });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Unlock' }));

    expect(
      await screen.findByText('No bike has this code. Check the code on the frame.'),
    ).toBeInTheDocument();
  });

  // BR6: one bike at a time.
  it('links to the running trip and refuses a second bike', async () => {
    const backend = createDemoBackend({ latencyMs: 0 });
    await backend.buyPass('STAY', 'pm_card_visa', 'key-1');
    await backend.startTrip('EB-401');
    await renderApp({ path: '/guest', user: 'guest', backend });

    expect(await screen.findByRole('link', { name: /Trip in progress on EB-401/ })).toHaveAttribute(
      'href',
      expect.stringMatching(/^\/guest\/trips\//),
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-101' }));

    expect(
      await screen.findByText('You already have a bike. End that trip first.'),
    ).toBeInTheDocument();
  });

  it('shows the start errors of the backend in words', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    await demo.buyPass('STAY', 'pm_card_visa', 'key-1');
    const backend: Backend = {
      ...demo,
      startTrip: vi.fn().mockRejectedValue(new ProblemError(403, 'Forbidden', null, [])),
    };
    await renderApp({ path: '/guest', user: 'guest', backend });

    fireEvent.click(await screen.findByRole('button', { name: 'Unlock EB-101' }));

    expect(
      await screen.findByText('Your stay is not active, so you cannot ride.'),
    ).toBeInTheDocument();
  });

  it('offers the bike code when there are no hubs', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const backend: Backend = { ...demo, hubs: () => Promise.resolve([]) };
    await renderApp({ path: '/guest', user: 'guest', backend });

    expect(await screen.findByText('No hubs to show yet')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Bikes' })).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });
});
