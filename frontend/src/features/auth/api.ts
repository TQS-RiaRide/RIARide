import { isWorkspace, type Workspace } from '../workspaces/workspace';

export interface CurrentUser {
  id: string;
  name: string | null;
  email: string | null;
  roles: string[];
  workspaces: Workspace[];
}

const ME = '/api/v1/me';

function isNullableString(value: unknown): value is string | null {
  return value === null || value === undefined || typeof value === 'string';
}

// The body comes from outside the app, so it is checked before use. A workspace this version
// of the app does not know yet is left out instead of failing the whole login.
function parseUser(body: unknown): CurrentUser {
  if (typeof body !== 'object' || body === null) {
    throw new Error(`GET ${ME} returned an invalid user`);
  }
  const { id, name, email, roles, workspaces } = body as Record<string, unknown>;
  if (
    typeof id !== 'string' ||
    !isNullableString(name) ||
    !isNullableString(email) ||
    !Array.isArray(roles) ||
    !Array.isArray(workspaces)
  ) {
    throw new Error(`GET ${ME} returned an invalid user`);
  }
  return {
    id,
    name: name ?? null,
    email: email ?? null,
    roles: roles.filter((role): role is string => typeof role === 'string'),
    workspaces: workspaces.filter(isWorkspace),
  };
}

/** Reads the logged-in user, their roles and the workspaces they may open (RIA-86). */
export async function fetchCurrentUser(
  token: string,
  fetchImpl: typeof fetch = fetch,
): Promise<CurrentUser> {
  const response = await fetchImpl(ME, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`GET ${ME} failed with status ${response.status}`);
  }
  return parseUser(await response.json());
}
