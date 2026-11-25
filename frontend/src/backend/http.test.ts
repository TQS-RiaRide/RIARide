import { describe, expect, it, vi } from 'vitest';
import { newIdempotencyKey, requestJson } from './http';
import { NetworkError, ProblemError } from './problem';

const token = () => Promise.resolve('test-token');

function fetchReturning(response: Response) {
  return vi.fn<typeof fetch>().mockResolvedValue(response);
}

function sentInit(fetchImpl: ReturnType<typeof fetchReturning>): RequestInit {
  const init = fetchImpl.mock.calls[0]?.[1];
  if (!init) throw new Error('fetch was not called with options');
  return init;
}

describe('requestJson', () => {
  it('sends a GET with the bearer token and returns the parsed body', async () => {
    const fetchImpl = fetchReturning(new Response(JSON.stringify({ id: 'a' }), { status: 200 }));

    const body = await requestJson(token, '/api/v1/things', {}, fetchImpl);

    expect(body).toEqual({ id: 'a' });
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/things', {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: 'Bearer test-token' },
      body: undefined,
    });
  });

  it('sends a JSON body with its content type and extra headers', async () => {
    const fetchImpl = fetchReturning(new Response('{}', { status: 201 }));

    await requestJson(
      token,
      '/api/v1/things',
      { method: 'POST', body: { name: 'Farol' }, headers: { 'X-Extra': '1' } },
      fetchImpl,
    );

    const init = sentInit(fetchImpl);
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"name":"Farol"}');
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-Extra': '1',
      Authorization: 'Bearer test-token',
    });
  });

  it('calls an anonymous endpoint without asking for a token', async () => {
    const getToken = vi.fn(token);
    const fetchImpl = fetchReturning(new Response(null, { status: 204 }));

    await requestJson(getToken, '/api/v1/guests', { anonymous: true }, fetchImpl);

    expect(getToken).not.toHaveBeenCalled();
    expect(sentInit(fetchImpl).headers).not.toHaveProperty('Authorization');
  });

  it('returns undefined for 204 No Content', async () => {
    const fetchImpl = fetchReturning(new Response(null, { status: 204 }));

    expect(await requestJson(token, '/x', {}, fetchImpl)).toBeUndefined();
  });

  it('returns undefined for an empty 200 body', async () => {
    const fetchImpl = fetchReturning(new Response('', { status: 200 }));

    expect(await requestJson(token, '/x', {}, fetchImpl)).toBeUndefined();
  });

  it('turns a problem+json answer into a ProblemError', async () => {
    const fetchImpl = fetchReturning(
      new Response(
        JSON.stringify({
          title: 'Bike not available',
          detail: 'Battery below 20 %',
          'invalid-params': [{ name: 'vehicleCode', reason: 'low battery' }],
        }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    const error = await requestJson(token, '/x', {}, fetchImpl).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ProblemError);
    expect(error).toMatchObject({
      status: 409,
      title: 'Bike not available',
      detail: 'Battery below 20 %',
      fields: ['vehicleCode'],
    });
  });

  it('turns a failed fetch into a NetworkError', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(requestJson(token, '/x', {}, fetchImpl)).rejects.toBeInstanceOf(NetworkError);
  });
});

describe('newIdempotencyKey', () => {
  it('gives a new UUID for each purchase attempt', () => {
    const first = newIdempotencyKey();
    const second = newIdempotencyKey();

    expect(first).toMatch(/^[0-9a-f-]{36}$/);
    expect(first).not.toBe(second);
  });
});
