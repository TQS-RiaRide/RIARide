/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  // In development, /api/v1 calls are forwarded to the Spring Boot backend.
  const env = loadEnv(mode, '.', '');
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // Same mapping as nginx.conf: /api/v1/health -> Spring Boot Actuator
        '/api/v1/health': {
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
        exclude: ['src/main.tsx', 'src/**/*.test.{ts,tsx}', 'src/setupTests.ts'],
      },
    },
  };
});
