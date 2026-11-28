/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
  /** "demo" runs the UI on synthetic data with no backend; anything else uses the real API. */
  readonly VITE_DATA_MODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
