import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'sonner';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// At check-in the hotel registers the stay and gets the code the guest signs up with (RIA-50, BR1).

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  toast.dismiss();
  vi.unstubAllGlobals();
  window.localStorage.clear();
  Reflect.deleteProperty(navigator, 'clipboard');
});

async function stayForm(backend?: Backend) {
  await renderApp({ path: '/hotel', user: 'manager', backend });
  return screen.findByRole('region', { name: 'Register a stay' });
}

async function fill(form: HTMLElement, values: Record<string, string>) {
  await act(async () => {
    for (const [label, value] of Object.entries(values)) {
      fireEvent.change(within(form).getByLabelText(label), { target: { value } });
    }
  });
}

async function submit(form: HTMLElement) {
  await act(async () => {
    fireEvent.click(within(form).getByRole('button', { name: 'Register stay' }));
  });
}

function rejecting(error: Error): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), registerStay: () => Promise.reject(error) };
}

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
}

describe('RegisterStayForm', () => {
  it('starts with a three-night stay from today at the first hotel', async () => {
    const form = await stayForm();

    expect(within(form).getByLabelText('Hotel')).toHaveDisplayValue('Marés Centro');
    const checkIn = within(form).getByLabelText<HTMLInputElement>('Check-in').value;
    const checkOut = within(form).getByLabelText<HTMLInputElement>('Check-out').value;
    expect(checkIn).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(Date.parse(checkOut) - Date.parse(checkIn)).toBe(3 * 86_400_000);
  });

  it('asks for the guest name before sending', async () => {
    const form = await stayForm();
    await fill(form, { 'Guest name': '   ' });

    await submit(form);

    expect(within(form).getByRole('alert')).toHaveTextContent("Enter the guest's name.");
    expect(within(form).getByLabelText('Guest name')).toHaveAttribute('aria-invalid', 'true');
  });

  it('refuses a check-out on the check-in day', async () => {
    const form = await stayForm();
    await fill(form, {
      'Guest name': 'Ana Costa',
      'Check-in': '2026-11-25',
      'Check-out': '2026-11-25',
    });

    await submit(form);

    expect(within(form).getByRole('alert')).toHaveTextContent(
      'The check-out must be after the check-in.',
    );
    expect(within(form).getByLabelText('Check-out')).toHaveAttribute('aria-invalid', 'true');
  });

  it('registers the stay, shows its code and adds it to the list', async () => {
    const form = await stayForm();
    await fill(form, {
      'Guest name': '  Ana Costa ',
      Hotel: '00000000-0000-0000-0000-000000000002',
      'Check-in': '2026-11-25',
      'Check-out': '2026-11-26',
    });

    await submit(form);

    const card = await within(form).findByRole('status');
    expect(card).toHaveTextContent('Stay code for Ana Costa');
    expect(within(card).getByText(/^PRAIA-[A-Z2-9]{4}$/)).toBeInTheDocument();
    expect(card).toHaveTextContent('Wed, Nov 25 → Thu, Nov 26');
    expect(within(form).getByLabelText('Guest name')).toHaveValue('');
    const stays = screen.getByRole('region', { name: 'Stays' });
    expect(await within(stays).findByText('Ana Costa')).toBeInTheDocument();
  });

  it('copies the stay code for the guest', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);
    const form = await stayForm();
    await fill(form, { 'Guest name': 'Ana Costa' });
    await submit(form);
    const card = await within(form).findByRole('status');
    const code = within(card).getByText(/^CENTRO-/).textContent;

    await act(async () => {
      fireEvent.click(within(card).getByRole('button', { name: 'Copy code' }));
    });

    expect(writeText).toHaveBeenCalledWith(code);
    expect(await screen.findByText('Code copied.')).toBeInTheDocument();
  });

  it('asks to copy the code by hand when the clipboard refuses', async () => {
    stubClipboard(() => Promise.reject(new Error('denied')));
    const form = await stayForm();
    await fill(form, { 'Guest name': 'Ana Costa' });
    await submit(form);
    const card = await within(form).findByRole('status');

    await act(async () => {
      fireEvent.click(within(card).getByRole('button', { name: 'Copy code' }));
    });

    expect(
      await screen.findByText('The code could not be copied. Select it and copy it by hand.'),
    ).toBeInTheDocument();
  });

  it('says a manager can only register stays at their own hotel', async () => {
    const form = await stayForm(rejecting(new ProblemError(403, 'Forbidden', null, [])));
    await fill(form, { 'Guest name': 'Ana Costa' });

    await submit(form);

    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'You can only register stays at the hotel you manage.',
    );
  });

  it('says the hotel is not part of the group when the backend does not know it', async () => {
    const form = await stayForm(rejecting(new ProblemError(404, 'Hotel not found', null, [])));
    await fill(form, { 'Guest name': 'Ana Costa' });

    await submit(form);

    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'This hotel is not part of the group.',
    );
  });

  it('marks the check-out when the backend refuses the dates', async () => {
    const form = await stayForm(
      rejecting(
        new ProblemError(422, 'Invalid stay dates', null, [{ name: 'checkOut', reason: null }]),
      ),
    );
    await fill(form, { 'Guest name': 'Ana Costa' });

    await submit(form);

    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'The check-out must be after the check-in.',
    );
    expect(within(form).getByLabelText('Check-out')).toHaveAttribute('aria-invalid', 'true');
  });

  it('falls back to a general message for a refused field it has no text for', async () => {
    const form = await stayForm(
      rejecting(new ProblemError(400, 'Invalid stay', null, [{ name: 'hotelId', reason: null }])),
    );
    await fill(form, { 'Guest name': 'Ana Costa' });

    await submit(form);

    await waitFor(() =>
      expect(within(form).getByRole('alert')).toHaveTextContent('Check the fields of the stay.'),
    );
    expect(within(form).getByLabelText('Hotel')).toHaveAttribute('aria-invalid', 'true');
  });
});
