import { describe, expect, it, vi } from 'vitest';
import { act, render } from '@testing-library/react';
import AuthProvider from './AuthProvider';
import type { AuthClient } from './authClient';
import { useAuth } from './useAuth';
import { requirement } from '../../test/xray';

requirement('RIA-92');

// A form that calls the backend (for example, OpenTicketForm) asks the provider for a token at the
// moment it sends, so the identity provider can refresh it first.
describe('AuthProvider', () => {
  it('hands out the access token of the identity provider when a component asks for it', async () => {
    const client: AuthClient = {
      init: vi.fn().mockResolvedValue(false),
      login: vi.fn().mockResolvedValue(undefined),
      logout: vi.fn().mockResolvedValue(undefined),
      getToken: vi.fn().mockResolvedValue('fresh-token'),
    };
    let getToken: () => Promise<string> = () => Promise.reject(new Error('not rendered'));
    function TokenReader() {
      getToken = useAuth().getToken;
      return null;
    }
    await act(async () => {
      render(
        <AuthProvider client={client}>
          <TokenReader />
        </AuthProvider>,
      );
    });

    await expect(getToken()).resolves.toBe('fresh-token');
    expect(client.getToken).toHaveBeenCalledOnce();
  });
});
