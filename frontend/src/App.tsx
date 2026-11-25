import { useTranslation } from 'react-i18next';
import LanguageSwitcher from './components/LanguageSwitcher';
import AuthControls from './features/auth/AuthControls';
import { useAuth } from './features/auth/useAuth';
import HealthStatus from './features/health/HealthStatus';
import OpenTicketForm from './features/maintenance/OpenTicketForm';
import Workspaces from './features/workspaces/Workspaces';

// The roles that may open a maintenance ticket (RIA-92); the backend refuses the others.
const TICKET_OPENERS = ['OPERATOR', 'ADMIN'];

export default function App() {
  const { t } = useTranslation();
  const { t: tAuth } = useTranslation('auth');
  const { state, getToken } = useAuth();

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
        <Workspaces
          workspaces={state.user.workspaces}
          content={
            state.user.roles.some((role) => TICKET_OPENERS.includes(role))
              ? { MAINTENANCE_TICKETS: <OpenTicketForm getToken={getToken} /> }
              : {}
          }
        />
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
