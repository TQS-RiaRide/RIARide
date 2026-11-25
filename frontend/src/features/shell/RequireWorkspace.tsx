import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router';
import { PATHS } from '../../app/paths';
import { EmptyState } from '../../components/ui/ui';
import { useAuth } from '../auth/useAuth';
import type { Workspace } from '../workspaces/workspace';

// The backend decides which workspaces a user may open (GET /api/v1/me); each endpoint still checks
// the role. This only keeps the UI from showing a screen whose calls would all be refused.
export default function RequireWorkspace({
  workspace,
  children,
}: {
  workspace: Workspace;
  children: ReactNode;
}) {
  const { state } = useAuth();
  const { t } = useTranslation('shell');

  if (state.status !== 'authenticated') return <Navigate to={PATHS.home} replace />;
  if (!state.user.workspaces.includes(workspace)) {
    return (
      <main className="page-narrow">
        <EmptyState
          title={t('forbidden.title')}
          action={<Link to={PATHS.home}>{t('forbidden.back')}</Link>}
        >
          {t('forbidden.body')}
        </EmptyState>
      </main>
    );
  }
  return children;
}
