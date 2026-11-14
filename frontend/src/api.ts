export type HealthStatus = 'up' | 'down';

/**
 * Calls the backend health endpoint. /api/v1/health is mapped to Spring Boot
 * Actuator /actuator/health by the dev proxy (vite.config.ts) and by nginx.
 * Replace with the client generated from OpenAPI once the API exists.
 */
export async function fetchHealth(fetchImpl: typeof fetch = fetch): Promise<HealthStatus> {
  try {
    const response = await fetchImpl('/api/v1/health');
    if (!response.ok) return 'down';
    const body = (await response.json()) as { status?: string };
    return body.status === 'UP' ? 'up' : 'down';
  } catch {
    return 'down';
  }
}
