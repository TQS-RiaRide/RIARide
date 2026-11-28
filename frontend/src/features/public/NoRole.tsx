import { useTranslation } from 'react-i18next';
import { Button, EmptyState } from '../../components/ui/ui';
import { useAuth } from '../auth/useAuth';
import Wordmark from '../shell/Wordmark';

export default function NoRole() {
  const { t } = useTranslation('workspaces');
  const { t: tAuth } = useTranslation('auth');
  const { logout } = useAuth();
  return (
    <main className="page-narrow error-page">
      <Wordmark />
      <div role="status">
        <EmptyState
          title={t('none')}
          action={<Button onClick={logout}>{tAuth('logOut')}</Button>}
        />
      </div>
    </main>
  );
}
