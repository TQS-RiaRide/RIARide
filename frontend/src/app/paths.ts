import type { Workspace } from '../features/workspaces/workspace';

export const PATHS = {
  home: '/',
  signIn: '/sign-in',
  signUp: '/sign-up',
  guest: '/guest',
  guestTrips: '/guest/trips',
  guestTrip: (id: string) => `/guest/trips/${encodeURIComponent(id)}`,
  fleet: '/fleet',
  maintenance: '/maintenance',
  hotel: '/hotel',
  admin: '/admin',
} as const;

export const WORKSPACE_PATHS: Record<Workspace, string> = {
  GUEST_DASHBOARD: PATHS.guest,
  OPERATOR_DASHBOARD: PATHS.fleet,
  MAINTENANCE_TICKETS: PATHS.maintenance,
  HOTEL_BACKOFFICE: PATHS.hotel,
  ACCOUNT_ADMINISTRATION: PATHS.admin,
};
