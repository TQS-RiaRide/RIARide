import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchCurrentUser, type CurrentUser } from './api';
import type { AuthClient } from './authClient';
import { AuthContext, type AuthState } from './authContext';

interface AuthProviderProps {
  client: AuthClient;
  loadUser?: (token: string) => Promise<CurrentUser>;
  children: ReactNode;
}

// Checks for a session when the app starts and, once logged in, loads the user and the
// workspaces of their roles from the backend.
export default function AuthProvider({
  client,
  loadUser = fetchCurrentUser,
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
      logout: () => void client.logout(),
      getToken: () => client.getToken(),
    }),
    [client, state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
