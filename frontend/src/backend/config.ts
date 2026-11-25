import type { DataMode } from './Backend';

/**
 * live (default): the real backend and Keycloak. demo: synthetic data and a persona picker, so
 * the UI can be shown and reviewed with no stack running (`npm run dev:demo`, which is
 * `vite --mode demo`). VITE_DATA_MODE=demo turns it on for any other build.
 */
export function dataMode(
  value: string | undefined = import.meta.env.VITE_DATA_MODE,
  viteMode: string = import.meta.env.MODE,
): DataMode {
  if (value !== undefined) return value === 'demo' ? 'demo' : 'live';
  return viteMode === 'demo' ? 'demo' : 'live';
}
