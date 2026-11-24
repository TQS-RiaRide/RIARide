import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import i18n from '../../i18n';
import AuthProvider from './AuthProvider';
import AuthControls from './AuthControls';
import type { AuthClient } from './authClient';
import type { CurrentUser } from './api';

const operator: CurrentUser = {
  id: 'user-1',
  name: 'Rui Almeida',
  email: 'rui.almeida@example.test',
  roles: ['OPERATOR'],
  workspaces: ['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS'],
};

function fakeClient(authenticated: boolean): AuthClient {
  return {
    init: vi.fn().mockResolvedValue(authenticated),
    login: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    getToken: vi.fn().mockResolvedValue('access-token'),
  };
}

async function renderControls(client: AuthClient, loadUser = vi.fn().mockResolvedValue(operator)) {
  await act(async () => {
    render(
      <AuthProvider client={client} loadUser={loadUser}>
        <AuthControls />
      </AuthProvider>,
    );
  });
  return loadUser;
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

describe('AuthControls', () => {
  it('offers to log in when there is no session, and starts the login', async () => {
    const client = fakeClient(false);
    await renderControls(client);

    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(client.login).toHaveBeenCalledOnce();
  });

  it('shows who is logged in and logs them out', async () => {
    const client = fakeClient(true);
    const loadUser = await renderControls(client);

    expect(loadUser).toHaveBeenCalledWith('access-token');
    expect(screen.getByText('Signed in as Rui Almeida')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(client.logout).toHaveBeenCalledOnce();
  });

  it('names the user by email when the account has no name', async () => {
    await renderControls(fakeClient(true), vi.fn().mockResolvedValue({ ...operator, name: null }));

    expect(screen.getByText('Signed in as rui.almeida@example.test')).toBeInTheDocument();
  });

  it('reports an account that could not be loaded', async () => {
    await renderControls(fakeClient(true), vi.fn().mockRejectedValue(new Error('500')));

    expect(screen.getByRole('alert')).toHaveTextContent(
      'We could not load your account. Try logging in again.',
    );
  });

  it('reports a session check that failed', async () => {
    const client = fakeClient(false);
    vi.mocked(client.init).mockRejectedValue(new Error('Keycloak unreachable'));

    await renderControls(client);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument();
  });

  it('names the user "your account" when it has neither a name nor an email', async () => {
    await renderControls(
      fakeClient(true),
      vi.fn().mockResolvedValue({ ...operator, name: null, email: null }),
    );

    expect(screen.getByText('Signed in as your account')).toBeInTheDocument();
  });

  it('announces the session check to screen readers', () => {
    const client = fakeClient(false);
    vi.mocked(client.init).mockReturnValue(new Promise(() => undefined));
    render(
      <AuthProvider client={client} loadUser={vi.fn()}>
        <AuthControls />
      </AuthProvider>,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Checking your session…');
  });

  // Logged in at Keycloak but the account could not be loaded: logging in again would reuse the
  // same session, so the user must be able to log out and try another account.
  it('lets a logged-in user whose account failed to load log out', async () => {
    const client = fakeClient(true);
    await renderControls(client, vi.fn().mockRejectedValue(new Error('500')));

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(client.logout).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Log in' })).not.toBeInTheDocument();
  });
});
