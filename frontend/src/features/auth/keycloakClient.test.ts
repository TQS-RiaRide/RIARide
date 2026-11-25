import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createKeycloakClient } from './keycloakClient';

const keycloak = vi.hoisted(() => ({
  constructor: vi.fn(),
  init: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  updateToken: vi.fn(),
  token: 'access-token' as string | undefined,
}));

vi.mock('keycloak-js', () => ({
  default: class {
    token = keycloak.token;
    constructor(config: unknown) {
      keycloak.constructor(config);
    }
    init = keycloak.init;
    login = keycloak.login;
    logout = keycloak.logout;
    updateToken = keycloak.updateToken;
  },
}));

const config = { url: 'http://localhost:8180', realm: 'riaride', clientId: 'riaride-spa' };

beforeEach(() => {
  vi.clearAllMocks();
  keycloak.token = 'access-token';
});

describe('createKeycloakClient', () => {
  it('connects to the realm and the SPA client', () => {
    createKeycloakClient(config);

    expect(keycloak.constructor).toHaveBeenCalledWith(config);
  });

  it('checks for an SSO session with Authorization Code and PKCE (S256)', async () => {
    keycloak.init.mockResolvedValue(true);

    const authenticated = await createKeycloakClient(config).init();

    expect(authenticated).toBe(true);
    expect(keycloak.init).toHaveBeenCalledWith({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
      enableLogging: false,
    });
  });

  // React runs effects twice in StrictMode, and keycloak-js can only be initialized once.
  it('initializes Keycloak only once however often init is called', async () => {
    keycloak.init.mockResolvedValue(false);
    const client = createKeycloakClient(config);

    await Promise.all([client.init(), client.init()]);

    expect(keycloak.init).toHaveBeenCalledTimes(1);
  });

  it('returns to the current page after login and logout', async () => {
    const client = createKeycloakClient(config);

    await client.login();
    await client.logout();

    expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: window.location.href });
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin + '/' });
  });

  it('refreshes the token when it expires within 30 seconds before returning it', async () => {
    keycloak.updateToken.mockResolvedValue(true);

    const token = await createKeycloakClient(config).getToken();

    expect(keycloak.updateToken).toHaveBeenCalledWith(30);
    expect(token).toBe('access-token');
  });

  it('fails when there is no token after the refresh', async () => {
    keycloak.updateToken.mockResolvedValue(false);
    keycloak.token = undefined;

    await expect(createKeycloakClient(config).getToken()).rejects.toThrow('Not logged in');
  });
});
