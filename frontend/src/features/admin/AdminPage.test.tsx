import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

describe('AdminPage', () => {
  it('says account management is not available yet and where roles are managed', async () => {
    await renderApp({ path: '/admin', user: 'admin' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Accounts and roles' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Account management is not available yet')).toBeInTheDocument();
    expect(screen.getByText('Manage accounts and roles in Keycloak.')).toBeInTheDocument();
  });

  it('is not part of an operator role', async () => {
    await renderApp({ path: '/admin', user: 'operator' });

    expect(screen.getByText('This area is not part of your role')).toBeInTheDocument();
    expect(screen.queryByText('Account management is not available yet')).not.toBeInTheDocument();
  });
});
