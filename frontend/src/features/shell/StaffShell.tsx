import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useNavigate } from 'react-router';
import { WORKSPACE_PATHS } from '../../app/paths';
import { Icon, type IconName } from '../../components/ui/ui';
import LanguageSwitcher from '../../components/LanguageSwitcher';
import { IS_MAC, useShortcuts } from '../../lib/hooks';
import { useAuth } from '../auth/useAuth';
import CommandMenu from '../command/CommandMenu';
import { CommandProvider, useCommands } from '../command/commands';
import ShortcutsHelp from '../command/ShortcutsHelp';
import HealthStatus from '../health/HealthStatus';
import DemoTag from './DemoTag';
import type { Workspace } from '../workspaces/workspace';
import Wordmark from './Wordmark';

// Account administration has no screen yet (RIA-113), so it stays out of the menu; /admin works.
const STAFF_WORKSPACES: Array<{ workspace: Workspace; icon: IconName; goKey: string }> = [
  { workspace: 'OPERATOR_DASHBOARD', icon: 'bike', goKey: 'g f' },
  { workspace: 'MAINTENANCE_TICKETS', icon: 'wrench', goKey: 'g m' },
  { workspace: 'HOTEL_BACKOFFICE', icon: 'bed', goKey: 'g h' },
];

export default function StaffShell() {
  return (
    <CommandProvider>
      <StaffFrame />
    </CommandProvider>
  );
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

// Back office for operators, technicians, hotel managers and admins: a side menu on wide screens,
// a top bar with a scrolling menu on narrow ones. ⌘K opens the command menu, ? lists shortcuts.
function StaffFrame() {
  const { t } = useTranslation('shell');
  const { t: tw } = useTranslation('workspaces');
  const { state, logout } = useAuth();
  const { setOpen } = useCommands();
  const navigate = useNavigate();
  const [helpOpen, setHelpOpen] = useState(false);

  const user = state.status === 'authenticated' ? state.user : null;
  const items = STAFF_WORKSPACES.filter((item) => user?.workspaces.includes(item.workspace));
  const displayName = user?.name ?? user?.email ?? t('account');

  useShortcuts([
    { keys: 'mod+k', run: () => setOpen(true), allowInFields: true },
    { keys: '?', run: () => setHelpOpen(true) },
    ...items.map((item) => ({
      keys: item.goKey,
      run: () => void navigate(WORKSPACE_PATHS[item.workspace], { viewTransition: true }),
    })),
  ]);

  return (
    <div className="staff">
      <a className="skip-link" href="#main">
        {t('skip')}
      </a>
      <aside className="staff-side">
        <div className="staff-brand">
          <Wordmark tone="on-ink" />
          <DemoTag tone="on-ink" />
        </div>
        <button
          type="button"
          className="staff-search sq"
          onClick={() => setOpen(true)}
          aria-keyshortcuts={IS_MAC ? 'Meta+K' : 'Control+K'}
        >
          <Icon name="search" size={16} />
          <span className="staff-search-label">{t('search')}</span>
        </button>
        <nav aria-label={tw('menuLabel')} className="staff-nav">
          <ul>
            {items.map((item) => (
              <li key={item.workspace}>
                <NavLink
                  to={WORKSPACE_PATHS[item.workspace]}
                  viewTransition
                  className="staff-link sq"
                >
                  <Icon name={item.icon} size={18} />
                  <span className="staff-link-label">{tw(`${item.workspace}.title`)}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="staff-foot">
          <HealthStatus />
          <div className="staff-tools">
            <LanguageSwitcher tone="on-ink" compact />
          </div>
          <div className="staff-account">
            <span className="avatar sq" aria-hidden="true">
              {initials(displayName)}
            </span>
            <span className="staff-account-name">
              {displayName}
              {user?.roles[0] && (
                <span className="staff-account-role">
                  {t(`roles.${user.roles[0]}`, { defaultValue: user.roles[0] })}
                </span>
              )}
            </span>
            <button
              type="button"
              className="staff-icon-btn sq"
              onClick={logout}
              aria-label={t('logOut')}
            >
              <Icon name="logout" size={18} />
            </button>
          </div>
        </div>
      </aside>
      <main id="main" className="staff-main" tabIndex={-1}>
        <Outlet />
      </main>
      <CommandMenu />
      <ShortcutsHelp
        open={helpOpen}
        onOpenChange={setHelpOpen}
        goTo={items.map((item) => ({
          keys: item.goKey,
          label: t('goTo', { place: tw(`${item.workspace}.title`) }),
        }))}
      />
    </div>
  );
}
