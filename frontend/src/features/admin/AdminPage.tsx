import { useTranslation } from 'react-i18next';
import { EmptyState } from '../../components/ui/ui';

// Accounts and roles (RIA-113) have no endpoint yet; roles are managed in Keycloak meanwhile.
export default function AdminPage() {
  const { t } = useTranslation('workspaces');
  const { t: ts } = useTranslation('shell');
  return (
    <div className="admin-page">
      <header className="page-head">
        <h1 className="page-title display">{t('ACCOUNT_ADMINISTRATION.title')}</h1>
      </header>
      <EmptyState tone="note" title={ts('admin.title')}>
        {ts('admin.body')}
      </EmptyState>
    </div>
  );
}
