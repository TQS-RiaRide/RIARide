import { describe, expect, it, vi } from 'vitest';
import { fetchCurrentUser } from './api';
import { requirement } from '../../test/xray';

requirement('RIA-86');

function respond(status: number, body: unknown) {
  return vi
    .fn()
    .mockResolvedValue({ ok: status >= 200 && status < 300, status, json: async () => body });
}

const operator = {
  id: '6f1c2b9e-4a57-4d0e-9a51-2f0d8c3b7e10',
  name: 'Rui Almeida',
  email: 'rui.almeida@example.test',
  roles: ['OPERATOR'],
  workspaces: ['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS'],
};

describe('fetchCurrentUser', () => {
  it('sends the access token as a bearer token to /api/v1/me', async () => {
    const fetchImpl = respond(200, operator);

    await fetchCurrentUser('access-token', fetchImpl);

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/me', {
      headers: { Authorization: 'Bearer access-token', Accept: 'application/json' },
    });
  });

  it('returns the user with their roles and workspaces', async () => {
    const user = await fetchCurrentUser('access-token', respond(200, operator));

    expect(user).toEqual(operator);
  });

  it('leaves out a workspace this version of the app does not know', async () => {
    const user = await fetchCurrentUser(
      'access-token',
      respond(200, { ...operator, workspaces: ['FUTURE_WORKSPACE', 'OPERATOR_DASHBOARD'] }),
    );

    expect(user.workspaces).toEqual(['OPERATOR_DASHBOARD']);
  });

  it('accepts a user without a name or email', async () => {
    const user = await fetchCurrentUser(
      'access-token',
      respond(200, { ...operator, name: null, email: null }),
    );

    expect(user.name).toBeNull();
    expect(user.email).toBeNull();
  });

  it.each([401, 403, 500])('fails when the backend answers %i', async (status) => {
    await expect(fetchCurrentUser('access-token', respond(status, {}))).rejects.toThrow(
      `GET /api/v1/me failed with status ${status}`,
    );
  });

  it.each([
    ['not an object', 'operator'],
    ['no id', { ...operator, id: undefined }],
    ['workspaces not a list', { ...operator, workspaces: 'OPERATOR_DASHBOARD' }],
    ['roles not a list', { ...operator, roles: null }],
  ])('fails when the body is invalid (%s)', async (_case, body) => {
    await expect(fetchCurrentUser('access-token', respond(200, body))).rejects.toThrow(
      'GET /api/v1/me returned an invalid user',
    );
  });
});
