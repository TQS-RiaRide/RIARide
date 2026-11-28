import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router';
import { PATHS } from '../../app/paths';
import { Button, Icon } from '../../components/ui/ui';
import { useAuth } from '../auth/useAuth';
import Wordmark from '../shell/Wordmark';
import './public.css';

// Live: hands over to Keycloak. Demo: pick one of the synthetic personas.
export default function SignInPage() {
  const { t } = useTranslation('landing');
  const { t: tAuth } = useTranslation('auth');
  const { state, login, logout, personas, signInAs } = useAuth();
  const stuck = state.status === 'error' && state.authenticated;
  const demo = signInAs !== undefined && personas !== undefined;

  useEffect(() => {
    if (!demo && state.status === 'anonymous') login();
  }, [demo, state.status, login]);

  if (state.status === 'authenticated') return <Navigate to={PATHS.home} replace />;

  return (
    <main className="auth-page">
      <Link to={PATHS.home} className="auth-back" viewTransition>
        <Wordmark />
      </Link>
      <section className="auth-card sq" aria-labelledby="sign-in-title">
        <h1 id="sign-in-title" className="auth-title display">
          {demo ? t('demo.title') : tAuth('logIn')}
        </h1>
        {demo ? (
          <>
            <p className="auth-lead">{t('demo.lead')}</p>
            <ul className="persona-list">
              {personas.map((persona) => (
                <li key={persona.id}>
                  <button type="button" className="persona sq" onClick={() => signInAs(persona.id)}>
                    <span className="persona-main">
                      <span className="persona-name">{persona.name}</span>
                      <span className="persona-role">{t(`demo.personas.${persona.id}`)}</span>
                    </span>
                    <Icon name="chevron" size={18} />
                  </button>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <>
            {stuck ? (
              <p role="alert" className="form-error">
                {tAuth('error')}
              </p>
            ) : (
              <p className="auth-lead">{t('redirecting')}</p>
            )}
            <Button variant="primary" size="lg" onClick={stuck ? logout : login}>
              {tAuth(stuck ? 'logOut' : 'logIn')}
            </Button>
          </>
        )}
      </section>
    </main>
  );
}
