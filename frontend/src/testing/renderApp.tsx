import { act, render } from '@testing-library/react';
import { vi } from 'vitest';
import App from '../App';
import type { Backend } from '../backend/Backend';
import { createDemoBackend } from '../backend/demoBackend';
import type { CurrentUser } from '../features/auth/api';
import type { AuthClient } from '../features/auth/authClient';
import AuthProvider from '../features/auth/AuthProvider';
import { findPersona, type PersonaId } from '../features/auth/demoAuth';

// Renders the whole app at a path, with a fake session and the in-memory backend, so screen tests
// go through the real routes, shells and queries.

export interface RenderAppOptions {
  path?: string;
  user?: CurrentUser | PersonaId;
  backend?: Backend;
  health?: { ok: boolean; status?: string };
}

export function fakeAuthClient(authenticated: boolean): AuthClient {
  return {
    init: vi.fn().mockResolvedValue(authenticated),
    login: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    getToken: vi.fn().mockResolvedValue('access-token'),
  };
}

export function personaUser(id: PersonaId): CurrentUser {
  const persona = findPersona(id);
  if (!persona) throw new Error(`Unknown persona ${id}`);
  return {
    id: persona.id,
    name: persona.name,
    email: persona.email,
    roles: [...persona.roles],
    workspaces: [...persona.workspaces],
  };
}

export async function renderApp({
  path = '/',
  user,
  backend = createDemoBackend({ latencyMs: 0 }),
  health = { ok: true, status: 'UP' },
}: RenderAppOptions = {}) {
  const current = typeof user === 'string' ? personaUser(user) : user;
  const client = fakeAuthClient(current !== undefined);
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: health.ok, json: async () => ({ status: health.status }) }),
  );
  let result: ReturnType<typeof render> | undefined;
  await act(async () => {
    result = render(
      <AuthProvider
        client={client}
        loadUser={() => (current ? Promise.resolve(current) : Promise.reject(new Error('None')))}
      >
        <App backend={backend} initialPath={path} />
      </AuthProvider>,
    );
  });
  if (!result) throw new Error('App did not render');
  return { ...result, client, backend };
}
