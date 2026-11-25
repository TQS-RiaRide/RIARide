import { Command } from 'cmdk';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { WORKSPACE_PATHS } from '../../app/paths';
import { useFleet, useHubs } from '../../backend/queries';
import { Kbd, StatusPill } from '../../components/ui/ui';
import { SUPPORTED_LANGUAGES, LANGUAGE_NAMES } from '../../i18n/config';
import { useAuth } from '../auth/useAuth';
import { useCommands } from './commands';
import './command.css';

// ⌘K / Ctrl K: go anywhere, find a bike or a hub, or run the open page's actions by typing.
export default function CommandMenu() {
  const { t, i18n } = useTranslation('command');
  const { t: tw } = useTranslation('workspaces');
  const { state, logout } = useAuth();
  const { actions, open, setOpen } = useCommands();
  const navigate = useNavigate();
  const workspaces = state.status === 'authenticated' ? state.user.workspaces : [];
  // Account administration has no screen yet (RIA-113), so it is not offered as a place.
  const places = workspaces.filter((workspace) => workspace !== 'ACCOUNT_ADMINISTRATION');
  const canSeeFleet = workspaces.includes('OPERATOR_DASHBOARD');
  const fleet = useFleet(open && canSeeFleet);
  const hubs = useHubs(open && canSeeFleet);

  const run = (action: () => void) => {
    setOpen(false);
    action();
  };
  const go = (to: string) => run(() => void navigate(to, { viewTransition: true }));

  return (
    <Command.Dialog
      open={open}
      onOpenChange={setOpen}
      label={t('label')}
      overlayClassName="cmd-overlay"
      contentClassName="cmd sq"
      loop
    >
      <div className="cmd-input-row">
        <svg
          aria-hidden="true"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-4-4" />
        </svg>
        <Command.Input className="cmd-input" placeholder={t('placeholder')} />
        <Kbd keys="esc" />
      </div>
      <Command.List className="cmd-list">
        <Command.Empty className="cmd-empty">{t('empty')}</Command.Empty>

        {actions.length > 0 && (
          <Command.Group heading={t('groups.actions')}>
            {actions.map((action) => (
              <Command.Item
                key={action.id}
                value={`${action.label} ${action.keywords?.join(' ') ?? ''}`}
                onSelect={() => run(action.run)}
                className="cmd-item"
              >
                <span className="cmd-item-label">{action.label}</span>
                {action.shortcut && <Kbd keys={action.shortcut} />}
              </Command.Item>
            ))}
          </Command.Group>
        )}

        <Command.Group heading={t('groups.navigate')}>
          {places.map((workspace) => (
            <Command.Item
              key={workspace}
              value={`${t('goTo')} ${tw(`${workspace}.title`)}`}
              onSelect={() => go(WORKSPACE_PATHS[workspace])}
              className="cmd-item"
            >
              <span className="cmd-item-label">{tw(`${workspace}.title`)}</span>
            </Command.Item>
          ))}
        </Command.Group>

        {canSeeFleet && (fleet.data?.length ?? 0) > 0 && (
          <Command.Group heading={t('groups.bikes')}>
            {fleet.data?.map((vehicle) => (
              <Command.Item
                key={vehicle.code}
                value={`${vehicle.code} ${vehicle.hubName ?? ''}`}
                onSelect={() =>
                  go(`${WORKSPACE_PATHS.OPERATOR_DASHBOARD}?q=${encodeURIComponent(vehicle.code)}`)
                }
                className="cmd-item"
              >
                <span className="cmd-item-code num">{vehicle.code}</span>
                <span className="cmd-item-meta">{vehicle.hubName ?? t('noHub')}</span>
                <StatusPill status={vehicle.status} />
              </Command.Item>
            ))}
          </Command.Group>
        )}

        {canSeeFleet && (hubs.data?.length ?? 0) > 0 && (
          <Command.Group heading={t('groups.hubs')}>
            {hubs.data?.map((hub) => (
              <Command.Item
                key={hub.id}
                value={`hub ${hub.name}`}
                onSelect={() =>
                  go(`${WORKSPACE_PATHS.OPERATOR_DASHBOARD}?hub=${encodeURIComponent(hub.id)}`)
                }
                className="cmd-item"
              >
                <span className="cmd-item-label">{hub.name}</span>
                <span className="cmd-item-meta num">
                  {t('occupancy', { docked: hub.dockedBikes, capacity: hub.capacity })}
                </span>
              </Command.Item>
            ))}
          </Command.Group>
        )}

        <Command.Group heading={t('groups.preferences')}>
          {SUPPORTED_LANGUAGES.filter((language) => language !== i18n.resolvedLanguage).map(
            (language) => (
              <Command.Item
                key={language}
                value={`language ${LANGUAGE_NAMES[language]}`}
                onSelect={() => run(() => void i18n.changeLanguage(language))}
                className="cmd-item"
              >
                <span className="cmd-item-label" lang={language}>
                  {t('switchLanguage', { language: LANGUAGE_NAMES[language] })}
                </span>
              </Command.Item>
            ),
          )}
          {state.status === 'authenticated' && (
            <Command.Item value={t('logOut')} onSelect={() => run(logout)} className="cmd-item">
              <span className="cmd-item-label">{t('logOut')}</span>
            </Command.Item>
          )}
        </Command.Group>
      </Command.List>
      <div className="cmd-foot">
        <span>
          <Kbd keys="?" /> {t('hints.shortcuts')}
        </span>
      </div>
    </Command.Dialog>
  );
}
