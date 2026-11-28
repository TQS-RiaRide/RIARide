import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import { createDemoBackend } from '../../backend/demoBackend';
import i18n from '../../i18n';
import { requirement } from '../../test/xray';
import { fakeAuthClient } from '../../testing/renderApp';
import AuthProvider from '../auth/AuthProvider';

requirement('RIA-86');

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// The Keycloak session exists (or not), but GET /api/v1/me fails, so the account cannot load.
async function renderWithAccountError(path: string, { authenticated }: { authenticated: boolean }) {
  const client = fakeAuthClient(authenticated);
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) }));
  await act(async () => {
    render(
      <AuthProvider client={client} loadUser={() => Promise.reject(new Error('503'))}>
        <App backend={createDemoBackend({ latencyMs: 0 })} initialPath={path} />
      </AuthProvider>,
    );
  });
  return client;
}

describe('a logged-in user whose account fails to load', () => {
  it.each(['/', '/sign-in'])('can log out from %s to end the Keycloak session', async (path) => {
    const client = await renderWithAccountError(path, { authenticated: true });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'We could not load your account. Try logging in again.',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(client.logout).toHaveBeenCalledOnce();
  });

  it('is not offered a log out when there is no Keycloak session', async () => {
    await renderWithAccountError('/', { authenticated: false });

    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
  });
});
