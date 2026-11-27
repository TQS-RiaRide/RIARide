import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { PATHS } from '../../app/paths';
import { EmptyState } from '../../components/ui/ui';
import Wordmark from '../shell/Wordmark';

export default function NotFound() {
  const { t } = useTranslation('shell');
  return (
    <main className="page-narrow error-page">
      <Wordmark />
      <EmptyState
        title={t('notFound.title')}
        action={<Link to={PATHS.home}>{t('notFound.home')}</Link>}
      >
        {t('notFound.body')}
      </EmptyState>
    </main>
  );
}
