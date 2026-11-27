import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { clearRegistries } from '../../backend/registry';
import { fetchCurrentUser, type CurrentUser } from './api';
import type { AuthClient } from './authClient';
import type { DemoAuthClient } from './demoAuth';
import { AuthContext, type AuthState } from './authContext';

type WithPersonas = AuthClient & Pick<DemoAuthClient, 'personas' | 'signInAs'>;

function hasPersonas(client: AuthClient): client is WithPersonas {
  const candidate = client as Partial<WithPersonas>;
  return typeof candidate.signInAs === 'function' && Array.isArray(candidate.personas);
}

interface AuthProviderProps {
  client: AuthClient;
  loadUser?: (token: string) => Promise<CurrentUser>;
  /** Clears what this browser kept for the user, before the session ends. */
  forgetLocalData?: () => void;
  children: ReactNode;
}

// Checks for a session when the app starts and, once logged in, loads the user and the
// workspaces of their roles from the backend.
export default function AuthProvider({
  client,
  loadUser = fetchCurrentUser,
  forgetLocalData = clearRegistries,
  children,
}: AuthProviderProps) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  useEffect(() => {
    // Ignore a result that arrives after unmount (or StrictMode's extra mount and unmount)
    let ignore = false;
    const update = (next: AuthState) => {
      if (!ignore) setState(next);
    };
    const start = async () => {
      let authenticated = false;
      try {
        authenticated = await client.init();
        if (!authenticated) {
          update({ status: 'anonymous' });
          return;
        }
        update({ status: 'authenticated', user: await loadUser(await client.getToken()) });
      } catch {
        update({ status: 'error', authenticated });
      }
    };
    void start();
    return () => {
      ignore = true;
    };
  }, [client, loadUser]);

  const value = useMemo(
    () => ({
      state,
      login: () => void client.login(),
      logout: () => {
        forgetLocalData();
        void client.logout();
      },
      getToken: () => client.getToken(),
      personas: hasPersonas(client) ? client.personas : undefined,
      signInAs: hasPersonas(client) ? (id: string) => client.signInAs(id) : undefined,
    }),
    [client, state, forgetLocalData],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
