import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Workspace } from './workspace';

interface WorkspacesProps {
  workspaces: readonly Workspace[];
}

// The menu of the workspaces the user's roles may open, and the one that is open (RIA-86).
// Each workspace's own content arrives with its stories; for now each shows what it is for.
export default function Workspaces({ workspaces }: WorkspacesProps) {
  const { t } = useTranslation('workspaces');
  const headingId = useId();
  const [selected, setSelected] = useState<Workspace | undefined>(workspaces[0]);
  const current = selected && workspaces.includes(selected) ? selected : workspaces[0];

  if (!current) {
    return (
      <p role="status" className="workspaces-empty">
        {t('none')}
      </p>
    );
  }

  return (
    <div className="workspaces">
      <nav aria-label={t('menuLabel')}>
        <ul className="workspace-menu">
          {workspaces.map((workspace) => (
            <li key={workspace}>
              <button
                type="button"
                aria-current={workspace === current ? 'true' : undefined}
                onClick={() => setSelected(workspace)}
              >
                {t(`${workspace}.title`)}
              </button>
            </li>
          ))}
        </ul>
      </nav>
      <section aria-labelledby={headingId} className="workspace-panel">
        <h2 id={headingId}>{t(`${current}.title`)}</h2>
        <p>{t(`${current}.description`)}</p>
      </section>
    </div>
  );
}
