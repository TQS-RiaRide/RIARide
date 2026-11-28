import { useTranslation } from 'react-i18next';
import { NavLink, Outlet } from 'react-router';
import { PATHS } from '../../app/paths';
import { Icon } from '../../components/ui/ui';
import LanguageSwitcher from '../../components/LanguageSwitcher';
import { useShortcuts } from '../../lib/hooks';
import { useAuth } from '../auth/useAuth';
import CommandMenu from '../command/CommandMenu';
import { CommandProvider, useCommands } from '../command/commands';
import DemoTag from './DemoTag';
import Wordmark from './Wordmark';

export default function GuestShell() {
  return (
    <CommandProvider>
      <GuestFrame />
    </CommandProvider>
  );
}

// The guest app is phone first: content in one column, a tab bar within thumb reach that
// respects the home indicator (safe area), and the same column centred on wide screens.
function GuestFrame() {
  const { t } = useTranslation('shell');
  const { logout } = useAuth();
  const { setOpen } = useCommands();
  useShortcuts([{ keys: 'mod+k', run: () => setOpen(true), allowInFields: true }]);

  return (
    <div className="guest">
      <a className="skip-link" href="#main">
        {t('skip')}
      </a>
      <header className="guest-top">
        <div className="guest-brand">
          <Wordmark />
          <DemoTag />
        </div>
        <div className="guest-top-actions">
          <LanguageSwitcher compact />
          <button
            type="button"
            className="guest-icon-btn sq"
            onClick={logout}
            aria-label={t('logOut')}
          >
            <Icon name="logout" size={18} />
          </button>
        </div>
      </header>
      <main id="main" className="guest-main" tabIndex={-1}>
        <Outlet />
      </main>
      <nav aria-label={t('guestNav')} className="guest-tabs">
        <NavLink to={PATHS.guest} end viewTransition className="guest-tab">
          <Icon name="bike" size={22} />
          {t('tabs.bikes')}
        </NavLink>
        <NavLink to={PATHS.guestTrips} viewTransition className="guest-tab">
          <Icon name="clock" size={22} />
          {t('tabs.trips')}
        </NavLink>
      </nav>
      <CommandMenu />
    </div>
  );
}
