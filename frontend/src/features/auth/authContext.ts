import { createContext } from 'react';
import type { CurrentUser } from './api';

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: CurrentUser }
  // authenticated: the Keycloak session exists but the account could not be loaded.
  | { status: 'error'; authenticated: boolean };

export interface AuthContextValue {
  state: AuthState;
  login(): void;
  logout(): void;
  /** A valid access token for calls to the backend, refreshed first if it is about to expire. */
  getToken(): Promise<string>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
