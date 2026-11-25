import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { keycloakify } from 'keycloakify/vite-plugin';

export default defineConfig(({ mode }) => {
  // In development, /api/v1 calls are forwarded to the Spring Boot backend.
  const env = loadEnv(mode, '.', '');
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [
      react(),
      // The same build also packs the Keycloak login theme (npm run build:keycloak-theme), so
      // Keycloak's pages are drawn with the app's own components (src/keycloak-theme).
      keycloakify({
        themeName: 'riaride',
        accountThemeImplementation: 'none',
        keycloakVersionTargets: {
          '22-to-25': false,
          'all-other-versions': 'riaride-keycloak-theme.jar',
        },
      }),
    ],
    server: {
      port: 5173,
      proxy: {
        // Same mapping as nginx.conf: exact match on /api/v1/health -> Actuator
        '^/api/v1/health$': {
          target: backendUrl,
          rewrite: () => '/actuator/health',
        },
        '/api/v1': backendUrl,
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/setupTests.ts',
      coverage: {
        // LCOV is imported by SonarQube (QA Manual, section 3.3)
        provider: 'v8',
        reporter: ['text', 'lcov'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/main.tsx',
          'src/keycloak-theme/kc.gen.tsx',
          'src/**/*.test.{ts,tsx}',
          'src/setupTests.ts',
        ],
      },
    },
  };
});
