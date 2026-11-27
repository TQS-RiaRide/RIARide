// The parts of the app a user can open, in menu order. The backend decides which ones each
// user may open from their roles (GET /api/v1/me, RIA-86); every endpoint still checks the role.
export const WORKSPACES = [
  'GUEST_DASHBOARD',
  'OPERATOR_DASHBOARD',
  'MAINTENANCE_TICKETS',
  'HOTEL_BACKOFFICE',
  'ACCOUNT_ADMINISTRATION',
] as const;

export type Workspace = (typeof WORKSPACES)[number];

export function isWorkspace(value: unknown): value is Workspace {
  return typeof value === 'string' && (WORKSPACES as readonly string[]).includes(value);
}
