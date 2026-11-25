import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { NetworkError, ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

// The demo backend accepts every card; this one declines Stripe's declined test card, as Stripe
// test mode does (402).
function stripeLikeBackend(): Backend {
  const demo = createDemoBackend({ latencyMs: 0 });
  return {
    ...demo,
    buyPass: vi.fn((passType, paymentMethod: string, key: string) =>
      paymentMethod === 'pm_card_chargeDeclined'
        ? Promise.reject(new ProblemError(402, 'Payment declined', 'The card was declined.', []))
        : demo.buyPass(passType, paymentMethod, key),
    ),
  };
}

async function openSheet() {
  fireEvent.click(await screen.findByRole('button', { name: 'Buy a pass from €10.00' }));
  return screen.findByRole('dialog', { name: 'Choose your pass' });
}

function pay(sheet: HTMLElement) {
  fireEvent.click(within(sheet).getByRole('button', { name: 'Pay and get the pass' }));
}

describe('BuyPassSheet', () => {
  it('charges the stay pass and its deposit by default', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: stripeLikeBackend() });

    const sheet = await openSheet();

    expect(await within(sheet).findByRole('radio', { name: /Stay pass/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(sheet).getByText('Charged today')).toBeInTheDocument();
    expect(within(sheet).getByText('€90.00')).toBeInTheDocument();
    expect(within(sheet).getByText('Refunded when the pass ends.')).toBeInTheDocument();
    expect(within(sheet).getAllByText('€40.00')).toHaveLength(1);
    expect(within(sheet).getByLabelText('Test card')).toHaveValue('pm_card_visa');
  });

  it('updates the total when the guest picks another pass', async () => {
    await renderApp({ path: '/guest', user: 'guest', backend: stripeLikeBackend() });
    const sheet = await openSheet();

    fireEvent.click(await within(sheet).findByRole('radio', { name: /1-day pass/ }));

    expect(within(sheet).getByRole('radio', { name: /1-day pass/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(sheet).getByText('€60.00')).toBeInTheDocument();
  });

  it('buys a pass with the Visa test card and shows it as ready to ride', async () => {
    const backend = stripeLikeBackend();
    await renderApp({ path: '/guest', user: 'guest', backend });
    const sheet = await openSheet();
    await within(sheet).findByRole('radio', { name: /3-day pass/ });
    fireEvent.click(within(sheet).getByRole('radio', { name: /3-day pass/ }));

    pay(sheet);

    expect(await screen.findByText('3-day pass bought.')).toBeInTheDocument();
    expect(await screen.findByText('Not started')).toBeInTheDocument();
    expect(backend.buyPass).toHaveBeenCalledWith('THREE_DAY', 'pm_card_visa', expect.any(String));
  });

  it('shows that a declined card charged nothing and tries the next attempt with a new key', async () => {
    const backend = stripeLikeBackend();
    await renderApp({ path: '/guest', user: 'guest', backend });
    const sheet = await openSheet();
    await within(sheet).findByRole('radio', { name: /Stay pass/ });
    fireEvent.change(within(sheet).getByLabelText('Test card'), {
      target: { value: 'pm_card_chargeDeclined' },
    });

    pay(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'The card was declined and nothing was charged. Try another card.',
    );
    fireEvent.change(within(sheet).getByLabelText('Test card'), {
      target: { value: 'pm_card_mastercard' },
    });
    pay(sheet);
    expect(await screen.findByText('Stay pass bought.')).toBeInTheDocument();
    const calls = vi.mocked(backend.buyPass).mock.calls;
    expect(calls[1][1]).toBe('pm_card_mastercard');
    expect(calls[1][2]).not.toBe(calls[0][2]);
  });

  it('retries with the same key after a network failure, so the guest is never charged twice', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const buyPass = vi
      .fn<Backend['buyPass']>()
      .mockRejectedValueOnce(new NetworkError())
      .mockImplementation((type, method, key) => demo.buyPass(type, method, key));
    await renderApp({ path: '/guest', user: 'guest', backend: { ...demo, buyPass } });
    const sheet = await openSheet();
    await within(sheet).findByRole('radio', { name: /Stay pass/ });

    pay(sheet);
    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'The payment could not be completed. Try again: you will not be charged twice.',
    );
    pay(sheet);

    expect(await screen.findByText('Stay pass bought.')).toBeInTheDocument();
    expect(buyPass.mock.calls[1][2]).toBe(buyPass.mock.calls[0][2]);
  });

  it.each([
    [
      403,
      'Forbidden',
      'Your stay is not active, so you cannot buy a pass. Check with the front desk.',
    ],
    [409, 'Pass already open', 'You already have a pass that has not ended.'],
    [409, 'Conflict', 'This purchase is still being processed. Wait a moment and try again.'],
    [
      503,
      'Unavailable',
      'The payment could not be completed. Try again: you will not be charged twice.',
    ],
  ])('explains a %i %s answer to the purchase', async (status, title, message) => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const buyPass = vi.fn().mockRejectedValue(new ProblemError(status, title, null, []));
    await renderApp({ path: '/guest', user: 'guest', backend: { ...demo, buyPass } });
    const sheet = await openSheet();
    await within(sheet).findByRole('radio', { name: /Stay pass/ });

    pay(sheet);

    expect(await within(sheet).findByRole('alert')).toHaveTextContent(message);
  });

  it('clears the last error when the sheet opens again', async () => {
    const demo = createDemoBackend({ latencyMs: 0 });
    const buyPass = vi.fn().mockRejectedValue(new ProblemError(402, 'Declined', null, []));
    await renderApp({ path: '/guest', user: 'guest', backend: { ...demo, buyPass } });
    let sheet = await openSheet();
    await within(sheet).findByRole('radio', { name: /Stay pass/ });
    pay(sheet);
    await within(sheet).findByRole('alert');

    fireEvent.click(within(sheet).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(sheet).toHaveAttribute('data-state', 'closed'));
    fireEvent.click(screen.getByRole('button', { name: 'Buy a pass from €10.00', hidden: true }));
    sheet = await screen.findByRole('dialog', { name: 'Choose your pass' });

    expect(within(sheet).queryByRole('alert')).not.toBeInTheDocument();
  });
});
