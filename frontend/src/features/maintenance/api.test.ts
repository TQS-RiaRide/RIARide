import { describe, expect, it, vi } from 'vitest';
import { openTicket, TicketError } from './api';

function respond(status: number, body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  });
}

const opened = {
  id: '3f1c9d2e-8a4b-4c6d-9e0f-1a2b3c4d5e6f',
  vehicleCode: 'EB-801',
  status: 'OPEN',
  description: 'Rear brake does not work',
  openedAt: '2026-11-30T10:00:00Z',
  slaDueAt: '2026-12-01T10:00:00Z',
  vehicleStatus: 'MAINTENANCE',
  maintenancePending: false,
};

const request = { vehicleCode: 'EB-801', description: 'Rear brake does not work' };

async function errorOf(promise: Promise<unknown>): Promise<TicketError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof TicketError) return error;
    throw error;
  }
  throw new Error('expected a TicketError');
}

describe('openTicket', () => {
  it('posts the bike code and the fault with the access token', async () => {
    const fetchImpl = respond(201, opened);

    await openTicket('access-token', request, fetchImpl);

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/tickets', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer access-token',
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });
  });

  it('returns the opened ticket with its deadline and the state of the bike', async () => {
    const ticket = await openTicket('access-token', request, respond(201, opened));

    expect(ticket).toEqual(opened);
  });

  it('returns a bike in use that waits for its trip to end', async () => {
    const pending = { ...opened, vehicleStatus: 'IN_USE', maintenancePending: true };

    const ticket = await openTicket('access-token', request, respond(201, pending));

    expect(ticket.vehicleStatus).toBe('IN_USE');
    expect(ticket.maintenancePending).toBe(true);
  });

  it('names the invalid fields of a 400 problem detail', async () => {
    const problem = {
      status: 400,
      title: 'Invalid ticket',
      'invalid-params': [
        { name: 'description', reason: 'A fault description has 1 to 500 characters.' },
      ],
    };

    const error = await errorOf(openTicket('access-token', request, respond(400, problem)));

    expect(error.kind).toBe('invalid');
    expect(error.fields).toEqual(['description']);
  });

  it('ignores invalid parameters that are not fields of the form', async () => {
    const problem = { status: 400, 'invalid-params': [{ name: 'other' }, { reason: 'x' }] };

    const error = await errorOf(openTicket('access-token', request, respond(400, problem)));

    expect(error.kind).toBe('invalid');
    expect(error.fields).toEqual([]);
  });

  it.each([
    ['no body', null],
    ['no list of invalid parameters', { status: 400, 'invalid-params': 'vehicleCode' }],
    ['parameters that are not objects', { status: 400, 'invalid-params': [null, 'vehicleCode'] }],
  ])('names no field for a 400 with %s', async (_case, problem) => {
    const error = await errorOf(openTicket('access-token', request, respond(400, problem)));

    expect(error.kind).toBe('invalid');
    expect(error.fields).toEqual([]);
  });

  it('names no field for a 400 whose body is not JSON', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    });

    const error = await errorOf(openTicket('access-token', request, fetchImpl));

    expect(error.kind).toBe('invalid');
    expect(error.fields).toEqual([]);
  });

  it.each([
    [404, 'notFound'],
    [409, 'retired'],
    [401, 'sessionExpired'],
    [403, 'forbidden'],
    [500, 'unexpected'],
  ] as const)('maps status %i to a %s error', async (status, kind) => {
    const error = await errorOf(
      openTicket('access-token', request, respond(status, { status, title: 'Problem' })),
    );

    expect(error.kind).toBe(kind);
    expect(error.fields).toEqual([]);
  });

  it('treats an answer that is not a ticket as unexpected', async () => {
    const error = await errorOf(
      openTicket('access-token', request, respond(201, { id: 42, vehicleStatus: 'FLYING' })),
    );

    expect(error.kind).toBe('unexpected');
  });

  it('treats a 201 without a body as unexpected', async () => {
    const error = await errorOf(openTicket('access-token', request, respond(201, null)));

    expect(error.kind).toBe('unexpected');
  });

  it('treats a network failure as unexpected', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await errorOf(openTicket('access-token', request, fetchImpl));

    expect(error.kind).toBe('unexpected');
  });
});
