import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import i18n from '../../i18n';
import OpenTicketForm from './OpenTicketForm';
import { TicketError, type OpenedTicket } from './api';

const opened: OpenedTicket = {
  id: '3f1c9d2e-8a4b-4c6d-9e0f-1a2b3c4d5e6f',
  vehicleCode: 'EB-801',
  status: 'OPEN',
  description: 'Rear brake does not work',
  openedAt: '2026-11-30T10:00:00Z',
  slaDueAt: '2026-12-01T10:00:00Z',
  vehicleStatus: 'MAINTENANCE',
  maintenancePending: false,
};

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

function renderForm(submit = vi.fn().mockResolvedValue(opened)) {
  const getToken = vi.fn().mockResolvedValue('access-token');
  render(<OpenTicketForm getToken={getToken} submit={submit} />);
  return { submit, getToken };
}

function fillIn(code: string, fault: string) {
  fireEvent.change(screen.getByLabelText('Bike code'), { target: { value: code } });
  fireEvent.change(screen.getByLabelText('Fault'), { target: { value: fault } });
}

async function send() {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Open ticket' }));
  });
}

describe('OpenTicketForm', () => {
  // Acceptance criterion of RIA-92, from the operator's side.
  it('opens a ticket and shows that the bike is in maintenance with its deadline', async () => {
    const { submit } = renderForm();
    fillIn('EB-801', 'Rear brake does not work');

    await send();

    expect(submit).toHaveBeenCalledWith('access-token', {
      vehicleCode: 'EB-801',
      description: 'Rear brake does not work',
    });
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('Ticket opened for EB-801.');
    expect(status).toHaveTextContent('The bike is in maintenance until it is repaired.');
    expect(status).toHaveTextContent('Repair deadline: Dec 1, 2026, 10:00');
  });

  it('clears the fields after a ticket is opened', async () => {
    renderForm();
    fillIn('EB-801', 'Rear brake does not work');

    await send();

    expect(screen.getByLabelText('Bike code')).toHaveValue('');
    expect(screen.getByLabelText('Fault')).toHaveValue('');
  });

  it('tells the operator that a bike in use moves to maintenance when its trip ends', async () => {
    renderForm(
      vi.fn().mockResolvedValue({
        ...opened,
        vehicleCode: 'EB-301',
        vehicleStatus: 'IN_USE',
        maintenancePending: true,
      }),
    );
    fillIn('EB-301', 'Guest reported a strange noise');

    await send();

    expect(screen.getByRole('status')).toHaveTextContent(
      'The bike is on a trip: it moves to maintenance when the trip ends.',
    );
  });

  it('disables the button while the ticket is being opened', async () => {
    let finish: (ticket: OpenedTicket) => void = () => undefined;
    const submit = vi.fn().mockReturnValue(
      new Promise<OpenedTicket>((resolve) => {
        finish = resolve;
      }),
    );
    renderForm(submit);
    fillIn('EB-801', 'Flat tyre');

    await send();

    expect(screen.getByRole('button', { name: 'Opening…' })).toBeDisabled();
    await act(async () => {
      finish(opened);
    });
    expect(screen.getByRole('button', { name: 'Open ticket' })).toBeEnabled();
  });

  it('marks the field the backend refused', async () => {
    renderForm(vi.fn().mockRejectedValue(new TicketError('invalid', ['description'])));
    fillIn('EB-801', ' ');

    await send();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Describe the fault in up to 500 characters.',
    );
    expect(screen.getByLabelText('Fault')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Fault')).toHaveAccessibleDescription(
      'Describe the fault in up to 500 characters.',
    );
    expect(screen.getByLabelText('Bike code')).not.toHaveAttribute('aria-invalid');
  });

  it.each([
    ['notFound', 'No bike has this code.'],
    ['retired', 'This bike is retired, so it cannot get a ticket.'],
    ['forbidden', 'Your account cannot open tickets.'],
    ['sessionExpired', 'Your session has ended. Log in again to open the ticket.'],
    ['unexpected', 'The ticket could not be opened. Try again.'],
  ] as const)('explains a %s error and keeps what was typed', async (kind, message) => {
    renderForm(vi.fn().mockRejectedValue(new TicketError(kind)));
    fillIn('EB-803', 'Flat tyre');

    await send();

    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('Bike code')).toHaveValue('EB-803');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('treats a failure to get a token as unexpected', async () => {
    const submit = vi.fn();
    render(
      <OpenTicketForm getToken={vi.fn().mockRejectedValue(new Error('expired'))} submit={submit} />,
    );
    fillIn('EB-801', 'Flat tyre');

    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'The ticket could not be opened. Try again.',
    );
  });

  it('is translated into Portuguese', async () => {
    await act(async () => {
      await i18n.changeLanguage('pt');
    });
    renderForm();

    expect(screen.getByLabelText('Código da bicicleta')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abrir pedido' })).toBeInTheDocument();
  });
});
