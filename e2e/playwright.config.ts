import { defineConfig, devices } from '@playwright/test';

// End-to-end tests drive the web UI against the Compose stack (QA Manual 5.3): run them after
// `docker compose -f infra/compose.yaml --profile app up -d --build` and
// `infra/keycloak/create-dev-users.sh`. They run outside the regular build: nightly on main, on
// demand and before each tag (.github/workflows/e2e.yml).
export default defineConfig({
  testDir: './tests',
  globalSetup: './support/global-setup.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [
        ['list'],
        ['html', { open: 'never' }],
        [
          'junit',
          // Annotations become JUnit properties: "requirements" links a result to its story in Xray.
          { outputFile: 'test-results/junit.xml', embedAnnotationsAsProperties: true },
        ],
      ]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    // Keycloak lets the SPA return only to http://localhost:8081 (infra/README.md).
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8081',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'en-GB',
    timezoneId: 'Europe/Lisbon',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
