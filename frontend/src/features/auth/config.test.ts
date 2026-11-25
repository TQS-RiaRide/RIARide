describe('keycloakConfig', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  async function loadConfig() {
    vi.resetModules();
    const { keycloakConfig } = await import('./config');
    return keycloakConfig;
  }

  it('points at the local stack when nothing is set', async () => {
    vi.stubEnv('VITE_KEYCLOAK_URL', undefined);
    vi.stubEnv('VITE_KEYCLOAK_REALM', undefined);
    vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', undefined);

    const config = await loadConfig();

    expect(config).toEqual({
      url: 'http://localhost:8180',
      realm: 'riaride',
      clientId: 'riaride-spa',
    });
  });

  it('uses the values set at build time', async () => {
    vi.stubEnv('VITE_KEYCLOAK_URL', 'https://auth.vila-ria.test');
    vi.stubEnv('VITE_KEYCLOAK_REALM', 'staging');
    vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', 'spa-staging');

    const config = await loadConfig();

    expect(config).toEqual({
      url: 'https://auth.vila-ria.test',
      realm: 'staging',
      clientId: 'spa-staging',
    });
  });
});
