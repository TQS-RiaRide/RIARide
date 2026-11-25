import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import { createDemoBackend } from '../../backend/demoBackend';
import i18n from '../../i18n';
import { fakeAuthClient, renderApp } from '../../testing/renderApp';
import AuthProvider from '../auth/AuthProvider';
import type { DemoAuthClient } from '../auth/demoAuth';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

// renderApp's client has no personas; the demo client adds signInAs.
async function renderDemoSignIn(): Promise<DemoAuthClient> {
  const client: DemoAuthClient = { ...fakeAuthClient(false), signInAs: vi.fn() };
  await act(async () => {
    render(
      <AuthProvider client={client} loadUser={() => Promise.reject(new Error('None'))}>
        <App backend={createDemoBackend({ latencyMs: 0 })} initialPath="/sign-in" />
      </AuthProvider>,
    );
  });
  return client;
}

describe('SignInPage', () => {
  it('lets a demo visitor choose a persona to sign in as', async () => {
    const client = await renderDemoSignIn();

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in as' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Ana/ })).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: /Sophie Laurent\s*Guest at Marés Praia/ }));

    expect(client.signInAs).toHaveBeenCalledWith('guest');
    expect(client.login).not.toHaveBeenCalled();
  });

  it('hands a visitor over to the identity provider when there are no personas', async () => {
    const { client } = await renderApp({ path: '/sign-in' });

    expect(screen.getByRole('heading', { level: 1, name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByText('Taking you to the login page…')).toBeInTheDocument();
    expect(client.login).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(client.login).toHaveBeenCalledTimes(2);
  });

  it('sends a user who is already signed in to their workspace', async () => {
    const { client } = await renderApp({ path: '/sign-in', user: 'guest' });

    expect(await screen.findByRole('navigation', { name: 'Main' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Log in' })).not.toBeInTheDocument();
    expect(client.login).not.toHaveBeenCalled();
  });
});
