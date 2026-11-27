import { useTranslation } from 'react-i18next';
import Sheet from '../../components/ui/Sheet';
import { Kbd } from '../../components/ui/ui';
import { useCommands } from './commands';

interface ShortcutsHelpProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  goTo: Array<{ keys: string; label: string }>;
}

// "?" lists every shortcut that works on the current page.
export default function ShortcutsHelp({ open, onOpenChange, goTo }: ShortcutsHelpProps) {
  const { t } = useTranslation('command');
  const { actions } = useCommands();
  const pageShortcuts = actions.filter((action) => action.shortcut);

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('help.title')}
      description={t('help.description')}
    >
      <p className="shortcut-section">{t('help.everywhere')}</p>
      <ul className="shortcut-list">
        <li className="shortcut-row">
          {t('help.palette')} <Kbd keys="mod+k" />
        </li>
        <li className="shortcut-row">
          {t('help.thisList')} <Kbd keys="?" />
        </li>
        {goTo.map((item) => (
          <li key={item.keys} className="shortcut-row">
            {item.label} <Kbd keys={item.keys} />
          </li>
        ))}
      </ul>
      {pageShortcuts.length > 0 && (
        <>
          <p className="shortcut-section">{t('help.thisPage')}</p>
          <ul className="shortcut-list">
            {pageShortcuts.map((action) => (
              <li key={action.id} className="shortcut-row">
                {action.label} <Kbd keys={action.shortcut ?? ''} />
              </li>
            ))}
          </ul>
        </>
      )}
    </Sheet>
  );
}
