import { useTranslation } from 'react-i18next';
import { useAuth } from './useAuth';

// Log in and log out, in the header. Texts come from the "auth" namespace.
export default function AuthControls() {
  const { t } = useTranslation('auth');
  const { state, login, logout } = useAuth();

  if (state.status === 'loading') {
    return (
      <span role="status" className="auth-status">
        {t('loading')}
      </span>
    );
  }

  if (state.status === 'authenticated') {
    const { name, email } = state.user;
    return (
      <div className="auth-controls">
        <span className="auth-user">
          {t('signedInAs', { name: name ?? email ?? t('yourAccount') })}
        </span>
        <button type="button" onClick={logout}>
          {t('logOut')}
        </button>
      </div>
    );
  }

  return (
    <div className="auth-controls">
      {state.status === 'error' && (
        <p role="alert" className="auth-error">
          {t('error')}
        </p>
      )}
      {state.status === 'error' && state.authenticated ? (
        <button type="button" onClick={logout}>
          {t('logOut')}
        </button>
      ) : (
        <button type="button" className="primary" onClick={login}>
          {t('logIn')}
        </button>
      )}
    </div>
  );
}
