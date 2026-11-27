import type { RouteObject } from 'react-router';
import AdminPage from '../features/admin/AdminPage';
import FleetPage from '../features/fleet/FleetPage';
import GuestHome from '../features/guest/GuestHome';
import TripPage from '../features/guest/TripPage';
import TripsPage from '../features/guest/TripsPage';
import BackofficePage from '../features/hotel/BackofficePage';
import TicketsPage from '../features/maintenance/TicketsPage';
import HomeRedirect from '../features/public/HomeRedirect';
import NotFound from '../features/public/NotFound';
import SignInPage from '../features/public/SignInPage';
import SignUpPage from '../features/public/SignUpPage';
import GuestShell from '../features/shell/GuestShell';
import RequireWorkspace from '../features/shell/RequireWorkspace';
import RootLayout from '../features/shell/RootLayout';
import RouteError from '../features/shell/RouteError';
import StaffShell from '../features/shell/StaffShell';

export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: 'sign-in', element: <SignInPage /> },
      { path: 'sign-up', element: <SignUpPage /> },
      {
        element: (
          <RequireWorkspace workspace="GUEST_DASHBOARD">
            <GuestShell />
          </RequireWorkspace>
        ),
        children: [
          { path: 'guest', element: <GuestHome /> },
          { path: 'guest/trips', element: <TripsPage /> },
          { path: 'guest/trips/:tripId', element: <TripPage /> },
        ],
      },
      {
        element: <StaffShell />,
        children: [
          {
            path: 'fleet',
            element: (
              <RequireWorkspace workspace="OPERATOR_DASHBOARD">
                <FleetPage />
              </RequireWorkspace>
            ),
          },
          {
            path: 'maintenance',
            element: (
              <RequireWorkspace workspace="MAINTENANCE_TICKETS">
                <TicketsPage />
              </RequireWorkspace>
            ),
          },
          {
            path: 'hotel',
            element: (
              <RequireWorkspace workspace="HOTEL_BACKOFFICE">
                <BackofficePage />
              </RequireWorkspace>
            ),
          },
          {
            path: 'admin',
            element: (
              <RequireWorkspace workspace="ACCOUNT_ADMINISTRATION">
                <AdminPage />
              </RequireWorkspace>
            ),
          },
        ],
      },
      { path: '*', element: <NotFound /> },
    ],
  },
];
