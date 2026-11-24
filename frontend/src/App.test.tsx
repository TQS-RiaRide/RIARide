import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import App from './App';
import i18n from './i18n';
import AuthProvider from './features/auth/AuthProvider';
import type { AuthClient } from './features/auth/authClient';
import type { CurrentUser } from './features/auth/api';

// Reset the language before each test, while no component is mounted.
// Doing it in afterEach would re-render a still-mounted App outside act.
beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function authClient(authenticated: boolean): AuthClient {
  return {
    init: vi.fn().mockResolvedValue(authenticated),
    login: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    getToken: vi.fn().mockResolvedValue('access-token'),
  };
}

async function renderApp(health: { ok: boolean; status?: string }, user?: CurrentUser) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: health.ok, json: async () => ({ status: health.status }) }),
  );
  // The health check and the session check resolve after the first render, so flush them
  // inside act
  await act(async () => {
    render(
      <AuthProvider client={authClient(user !== undefined)} loadUser={async () => user!}>
        <App />
      </AuthProvider>,
    );
  });
}

async function chooseLanguage(label: string, language: string) {
  // changeLanguage is asynchronous: wait for i18next to emit languageChanged
  // and for React to re-render, all inside act
  await act(async () => {
    fireEvent.change(screen.getByLabelText(label), { target: { value: language } });
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe('App', () => {
  it('shows the backend as online and switches language both ways', async () => {
    await renderApp({ ok: true, status: 'UP' });

    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(screen.getByText('Backend status:')).toBeInTheDocument();

    await chooseLanguage('Language', 'pt');
    expect(screen.getByText('Disponível')).toBeInTheDocument();
    expect(screen.getByText('Estado do backend:')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('pt');

    await chooseLanguage('Língua', 'en');
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('shows the backend as unreachable when the health check fails', async () => {
    await renderApp({ ok: false });

    expect(screen.getByText('Unreachable')).toBeInTheDocument();
  });

  it('invites a visitor to log in before showing any workspace', async () => {
    await renderApp({ ok: true, status: 'UP' });

    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByText('Log in to see the features of your role.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Workspaces' })).not.toBeInTheDocument();
  });

  // Acceptance criterion of RIA-86.
  it('shows a logged-in operator the operator dashboard and not the hotel backoffice', async () => {
    await renderApp(
      { ok: true, status: 'UP' },
      {
        id: 'user-1',
        name: 'Rui Almeida',
        email: 'rui.almeida@example.test',
        roles: ['OPERATOR'],
        workspaces: ['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS'],
      },
    );

    const menu = screen.getByRole('navigation', { name: 'Workspaces' });
    expect(menu).toHaveTextContent('Operator dashboard');
    expect(menu).not.toHaveTextContent('Hotel backoffice');
    expect(screen.queryByText('Log in to see the features of your role.')).not.toBeInTheDocument();
  });
});
