import { APP_URL, KEYCLOAK_URL, devUserPassword } from './env';

// Test data goes in through the public API, as an operator would enter it, never straight into
// the database. Tokens come from riaride-dev-cli, the dev-only client that allows the password
// grant (infra/keycloak/create-dev-users.sh).
export async function tokenFor(username: string): Promise<string> {
  const response = await fetch(`${KEYCLOAK_URL}/realms/riaride/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'riaride-dev-cli',
      username,
      password: devUserPassword(),
    }),
  });
  if (!response.ok) {
    throw new Error(`Login of ${username} failed with status ${response.status}`);
  }
  const body = (await response.json()) as { access_token: string };
  return body.access_token;
}

async function call(token: string, method: string, path: string, body?: unknown): Promise<unknown> {
  const response = await fetch(`${APP_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with status ${response.status}`);
  }
  return response.status === 204 ? undefined : response.json();
}

export interface Bike {
  id: string;
  code: string;
}

// A tourist-spot hub with fresh bikes, unique to this run, so runs never depend on each other.
export async function hubWithBikes(token: string, run: string, codes: string[]): Promise<Bike[]> {
  const hub = (await call(token, 'POST', '/api/v1/hubs', {
    name: `E2E hub ${run}`,
    kind: 'TOURIST_SPOT',
    latitude: 40.6443,
    longitude: -8.6455,
    capacity: 10,
  })) as { id: string };
  const bikes: Bike[] = [];
  for (const code of codes) {
    const bike = (await call(token, 'POST', `/api/v1/hubs/${hub.id}/vehicles`, {
      code,
      type: 'E_BIKE',
    })) as { id: string; code: string };
    bikes.push({ id: bike.id, code: bike.code });
  }
  return bikes;
}

export async function retire(token: string, bike: Bike): Promise<void> {
  await call(token, 'PUT', `/api/v1/vehicles/${bike.id}/status`, { status: 'RETIRED' });
}
