// What the app needs from the identity provider. Keycloak implements it in keycloakClient.ts;
// tests pass a fake, so no component depends on keycloak-js directly.
export interface AuthClient {
  /** Checks for a session, finishing a login redirect if there is one. True when logged in. */
  init(): Promise<boolean>;
  /** Sends the browser to the login page, back to the current page afterwards. */
  login(): Promise<void>;
  /** Ends the session at the identity provider and comes back to the home page. */
  logout(): Promise<void>;
  /** A valid access token, refreshed first if it is about to expire. */
  getToken(): Promise<string>;
}
