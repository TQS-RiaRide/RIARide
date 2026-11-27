import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { fakeAuthClient } from '../../testing/renderApp';
import AuthProvider from './AuthProvider';
import { useAuth } from './useAuth';
import { requirement } from '../../test/xray';

function LogoutButton() {
  const { logout } = useAuth();
  return (
    <button type="button" onClick={logout}>
      Log out
    </button>
  );
}

requirement('RIA-86');

describe('AuthProvider', () => {
  it('forgets what this browser kept before ending the session', async () => {
    const client = fakeAuthClient(false);
    const order: string[] = [];
    const forgetLocalData = vi.fn(() => order.push('forget'));
    vi.mocked(client.logout).mockImplementation(() => {
      order.push('logout');
      return Promise.resolve();
    });
    await act(async () => {
      render(
        <AuthProvider client={client} forgetLocalData={forgetLocalData}>
          <LogoutButton />
        </AuthProvider>,
      );
    });

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(order).toEqual(['forget', 'logout']);
  });
});
