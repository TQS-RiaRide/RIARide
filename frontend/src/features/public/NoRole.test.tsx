import { fireEvent, screen } from '@testing-library/react';
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

describe('NoRole', () => {
  it('tells a user with no workspace to ask for a role and lets them log out', async () => {
    const { client } = await renderApp({
      user: { id: 'u-1', name: 'Nuno Reis', email: 'nuno@example.test', roles: [], workspaces: [] },
    });

    expect(screen.getByRole('status')).toHaveTextContent(
      'Your account has no role yet. Ask an administrator to give you one.',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(client.logout).toHaveBeenCalledOnce();
  });
});
