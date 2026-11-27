import { useTranslation } from 'react-i18next';
import { EmptyState } from '../../components/ui/ui';
import Wordmark from './Wordmark';
import './shell.css';

// Shown when a screen fails to render; reloading is the one recovery that always works.
export default function RouteError() {
  const { t } = useTranslation('shell');
  return (
    <main className="page-narrow error-page">
      <Wordmark />
      <EmptyState
        title={t('crash.title')}
        action={
          <button
            type="button"
            className="btn btn-primary btn-md sq"
            onClick={() => window.location.reload()}
          >
            {t('crash.reload')}
          </button>
        }
      >
        {t('crash.body')}
      </EmptyState>
    </main>
  );
}
