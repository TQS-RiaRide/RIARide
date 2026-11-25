import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { VehicleStatus } from '../../backend/types';
import { IS_MAC } from '../../lib/hooks';
import './ui.css';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'inverse';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: 'md' | 'lg' | 'sm';
  /** The key that runs the button, such as "t": announced and shown as a tooltip, not drawn. */
  shortcut?: string;
  busy?: boolean;
}

export function Button({
  variant = 'secondary',
  size = 'md',
  shortcut,
  busy = false,
  className = '',
  children,
  type = 'button',
  title,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`btn btn-${variant} btn-${size} sq ${className}`}
      aria-busy={busy || undefined}
      aria-keyshortcuts={shortcut ? ariaKeys(shortcut) : undefined}
      title={title ?? (shortcut ? keyLabels(shortcut).join(' ') : undefined)}
      {...rest}
    >
      {busy && <span className="btn-spinner" aria-hidden="true" />}
      <span className="btn-label">{children}</span>
    </button>
  );
}

/** Labels of a shortcut's keys: "mod+k" gives ⌘ K on a Mac and Ctrl K elsewhere. */
function keyLabels(keys: string): string[] {
  return keys.split(/[+ ]/).map((part) => {
    if (part === 'mod') return IS_MAC ? '⌘' : 'Ctrl';
    if (part === 'enter') return '↵';
    if (part === 'esc') return 'Esc';
    return part.length === 1 ? part.toUpperCase() : part;
  });
}

/** A shortcut in the aria-keyshortcuts syntax: "mod+k" gives Meta+K or Control+K. */
function ariaKeys(keys: string): string {
  return keys
    .split('+')
    .map((part) => {
      if (part === 'mod') return IS_MAC ? 'Meta' : 'Control';
      return part.length === 1 ? part.toUpperCase() : part;
    })
    .join('+');
}

/** Keys of a shortcut: "mod+k" shows ⌘ K on a Mac and Ctrl K elsewhere. */
export function Kbd({
  keys,
  tone = 'default',
}: {
  keys: string;
  tone?: 'default' | 'on-action' | 'on-ink';
}) {
  const parts = keyLabels(keys);
  return (
    <span className={`kbd-group kbd-${tone}`} aria-hidden="true">
      {parts.map((part, index) => (
        <kbd key={`${part}-${index}`}>{part}</kbd>
      ))}
    </span>
  );
}

export function Skeleton({
  width,
  height = 12,
  radius = 6,
}: {
  width: number | string;
  height?: number;
  radius?: number;
}) {
  return (
    <span className="skeleton" style={{ width, height, borderRadius: radius }} aria-hidden="true" />
  );
}

const STATUS_TONE: Record<VehicleStatus, string> = {
  AVAILABLE: 'ok',
  RESERVED: 'warn',
  IN_USE: 'water',
  MAINTENANCE: 'action',
  RETIRED: 'muted',
};

export function StatusPill({ status }: { status: VehicleStatus }) {
  const { t } = useTranslation('ui');
  return (
    <span className={`pill pill-${STATUS_TONE[status]}`}>
      <span className="pill-dot" aria-hidden="true" />
      {t(`vehicleStatus.${status}`)}
    </span>
  );
}

export function BatteryMeter({ pct }: { pct: number | null }) {
  const { t } = useTranslation('ui');
  if (pct === null) return <span className="battery battery-unknown">{t('battery.unknown')}</span>;
  const tone = pct < 20 ? 'low' : pct < 40 ? 'mid' : 'high';
  return (
    <span className={`battery battery-${tone}`}>
      <span className="battery-track" aria-hidden="true">
        <span className="battery-fill" style={{ transform: `scaleX(${Math.max(pct, 3) / 100})` }} />
      </span>
      <span className="num">{t('battery.value', { pct })}</span>
    </span>
  );
}

interface EmptyStateProps {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  tone?: 'plain' | 'note';
}

export function EmptyState({ title, children, action, tone = 'plain' }: EmptyStateProps) {
  return (
    <div className={`empty-state empty-${tone}`}>
      <p className="empty-title">{title}</p>
      {children && <div className="empty-body">{children}</div>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  );
}

export function Panel({
  title,
  titleId,
  actions,
  children,
  className = '',
}: {
  title: ReactNode;
  titleId: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section aria-labelledby={titleId} className={`panel sq ${className}`}>
      <header className="panel-head">
        <h2 id={titleId} className="panel-title">
          {title}
        </h2>
        {actions && <div className="panel-actions">{actions}</div>}
      </header>
      {children}
    </section>
  );
}

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {ICONS[name]}
    </svg>
  );
}

export type IconName =
  | 'bike'
  | 'route'
  | 'clock'
  | 'card'
  | 'search'
  | 'back'
  | 'check'
  | 'close'
  | 'plus'
  | 'more'
  | 'wrench'
  | 'hub'
  | 'chart'
  | 'logout'
  | 'globe'
  | 'copy'
  | 'menu'
  | 'bed'
  | 'chevron'
  | 'arrowDown';

const ICONS: Record<IconName, ReactNode> = {
  bike: (
    <>
      <circle cx="6" cy="16" r="3.5" />
      <circle cx="18" cy="16" r="3.5" />
      <path d="M6 16l4-7h5l3 7M10 9 9 6H7M15 9l1-3h2" />
    </>
  ),
  route: <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  card: (
    <>
      <rect x="3" y="6" width="18" height="13" rx="3" />
      <path d="M3 10h18M7 15h4" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </>
  ),
  back: <path d="m15 5-7 7 7 7" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  plus: <path d="M12 5v14M5 12h14" />,
  more: (
    <>
      <circle cx="5" cy="12" r="1.2" />
      <circle cx="12" cy="12" r="1.2" />
      <circle cx="19" cy="12" r="1.2" />
    </>
  ),
  wrench: (
    <path d="M14.5 6.5a4 4 0 0 0 5 5L13 18a2.1 2.1 0 0 1-3-3l6.5-6.5zM14.5 6.5 17 4l3 3-2.5 2.5" />
  ),
  hub: (
    <>
      <path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z" />
      <circle cx="12" cy="9.5" r="2.5" />
    </>
  ),
  chart: <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />,
  logout: <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11" />,
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c2.5 3 2.5 15 0 18M12 3c-2.5 3-2.5 15 0 18" />
    </>
  ),
  copy: (
    <>
      <rect x="8" y="8" width="12" height="12" rx="2.5" />
      <path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" />
    </>
  ),
  menu: <path d="M4 7h16M4 12h16M4 17h10" />,
  bed: <path d="M3 18V7M3 13h18v5M21 13a3 3 0 0 0-3-3h-7v3M6.5 10.5h.01" />,
  chevron: <path d="m9 6 6 6-6 6" />,
  arrowDown: <path d="M12 5v14m-6-6 6 6 6-6" />,
};
