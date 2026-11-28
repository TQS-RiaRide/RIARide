import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Drawer } from 'vaul';
import { useBackToClose, useMediaQuery } from '../../lib/hooks';
import { Icon } from './ui';
import './sheet.css';

interface SheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * A bottom sheet on phones (drag down to dismiss, the drag can be caught and reversed midway)
 * and a side panel on wider screens. The system back gesture closes it (predictive back).
 */
export default function Sheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
}: SheetProps) {
  const { t } = useTranslation('ui');
  const wide = useMediaQuery('(min-width: 768px)');
  useBackToClose(open, () => onOpenChange(false));

  return (
    <Drawer.Root open={open} onOpenChange={onOpenChange} direction={wide ? 'right' : 'bottom'}>
      <Drawer.Portal>
        <Drawer.Overlay className="sheet-overlay" />
        <Drawer.Content
          className={`sheet sq ${wide ? 'sheet-side' : 'sheet-bottom'}`}
          aria-describedby={undefined}
        >
          {!wide && <div className="sheet-grabber" aria-hidden="true" />}
          <header className="sheet-head">
            <div>
              <Drawer.Title className="sheet-title display">{title}</Drawer.Title>
              {description && (
                <Drawer.Description className="sheet-description">{description}</Drawer.Description>
              )}
            </div>
            <Drawer.Close className="icon-btn hit-slop sq" aria-label={t('close')}>
              <Icon name="close" />
            </Drawer.Close>
          </header>
          <div className="sheet-body">{children}</div>
          {footer && <footer className="sheet-foot">{footer}</footer>}
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
