import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/useAuth';
import Wordmark from './Wordmark';
import './shell.css';

// Holds every page. While the session is checked it shows the wordmark, not a blank page.
export default function RootLayout() {
  const { state } = useAuth();
  const { t } = useTranslation('auth');
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);

  if (state.status === 'loading') {
    return (
      <div className="splash" role="status">
        <Wordmark size="lg" />
        <p className="splash-text">{t('loading')}</p>
      </div>
    );
  }
  return <Outlet />;
}
