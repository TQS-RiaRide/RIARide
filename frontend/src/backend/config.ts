import type { DataMode } from './Backend';

/**
 * live (default): the real backend and Keycloak. demo: synthetic data and a persona picker, so
 * the UI can be shown and reviewed with no stack running (`npm run dev:demo`, which is
 * `vite --mode demo`). VITE_DATA_MODE=demo turns it on for any other dev server. A production
 * build is always live: demo mode signs anyone in as any role, with no authentication.
 */
export function dataMode(
  value: string | undefined = import.meta.env.VITE_DATA_MODE,
  viteMode: string = import.meta.env.MODE,
  production: boolean = import.meta.env.PROD,
): DataMode {
  if (production) return 'live';
  if (value !== undefined) return value === 'demo' ? 'demo' : 'live';
  return viteMode === 'demo' ? 'demo' : 'live';
}
