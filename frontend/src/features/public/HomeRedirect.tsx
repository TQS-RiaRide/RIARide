import { Navigate } from 'react-router';
import { WORKSPACE_PATHS } from '../../app/paths';
import { useAuth } from '../auth/useAuth';
import Landing from './Landing';
import NoRole from './NoRole';

// "/" is the landing page for visitors and the first workspace of the user's roles otherwise.
export default function HomeRedirect() {
  const { state } = useAuth();
  if (state.status !== 'authenticated') return <Landing />;
  const [first] = state.user.workspaces;
  if (!first) return <NoRole />;
  return <Navigate to={WORKSPACE_PATHS[first]} replace />;
}
