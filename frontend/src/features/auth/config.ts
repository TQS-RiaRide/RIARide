import type { KeycloakConfig } from './keycloakClient';

// Set at build time (Vite). The defaults match the local stack in infra/compose.yaml.
export const keycloakConfig: KeycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'riaride',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'riaride-spa',
};
