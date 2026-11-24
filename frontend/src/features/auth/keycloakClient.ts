import Keycloak from 'keycloak-js';
import type { AuthClient } from './authClient';

export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
}

// Refresh the access token when it expires within this many seconds.
const MIN_TOKEN_VALIDITY_SECONDS = 30;

// Authorization Code with PKCE; keycloak-js keeps the tokens in memory only, never in storage
// (ADR 0009, architecture section 10).
export function createKeycloakClient(config: KeycloakConfig): AuthClient {
  const keycloak = new Keycloak(config);
  // keycloak-js can only be initialized once, and React runs effects twice in StrictMode.
  let initialized: Promise<boolean> | undefined;

  return {
    init() {
      // check-sso: a page reload keeps the Keycloak session instead of asking to log in again.
      initialized ??= keycloak.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        enableLogging: false,
      });
      return initialized;
    },
    async login() {
      await keycloak.login({ redirectUri: window.location.href });
    },
    async logout() {
      await keycloak.logout({ redirectUri: window.location.origin + '/' });
    },
    async getToken() {
      await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
      if (!keycloak.token) {
        throw new Error('Not logged in');
      }
      return keycloak.token;
    },
  };
}
