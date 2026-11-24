import { createContext } from 'react';
import type { CurrentUser } from './api';

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: CurrentUser }
  | { status: 'error' };

export interface AuthContextValue {
  state: AuthState;
  login(): void;
  logout(): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
