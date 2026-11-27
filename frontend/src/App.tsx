import { useTranslation } from 'react-i18next';
import LanguageSwitcher from './components/LanguageSwitcher';
import AuthControls from './features/auth/AuthControls';
import { useAuth } from './features/auth/useAuth';
import HealthStatus from './features/health/HealthStatus';
import Workspaces from './features/workspaces/Workspaces';

export default function App() {
  const { t } = useTranslation();
  const { t: tAuth } = useTranslation('auth');
  const { state } = useAuth();

  return (
    <main className="app">
      <header>
        <h1>{t('appName')}</h1>
        <div className="header-actions">
          <LanguageSwitcher />
          <AuthControls />
        </div>
      </header>
      {state.status === 'authenticated' ? (
        <Workspaces workspaces={state.user.workspaces} />
      ) : (
        <section className="intro">
          <p className="tagline">{t('tagline')}</p>
          {state.status !== 'loading' && <p>{tAuth('welcome')}</p>}
        </section>
      )}
      <HealthStatus />
    </main>
  );
}
