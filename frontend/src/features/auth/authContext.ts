import { createContext } from 'react';
import type { CurrentUser } from './api';
import type { Persona } from './demoAuth';

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
  /** Demo mode only: the synthetic personas, which come with the demo client so the persona data
   * stays out of live builds. */
  personas?: readonly Persona[];
  /** Demo mode only: signs in as one of the synthetic personas. */
  signInAs?: (personaId: string) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
