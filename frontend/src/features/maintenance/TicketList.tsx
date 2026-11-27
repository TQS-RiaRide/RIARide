import { useTranslation } from 'react-i18next';
import type { Ticket } from '../../backend/types';
import { EmptyState, Skeleton } from '../../components/ui/ui';
import { formatDuration } from '../../lib/format';
import { useNow } from '../../lib/hooks';
import Pagination from '../../components/ui/Pagination';
import { usePagination } from '../../lib/pagination';

/** Under this many hours to the SLA deadline a ticket is urgent. */
export const URGENT_HOURS = 4;

export const TICKETS_PAGE_SIZE = 8;

interface TicketListProps {
  tickets: Ticket[] | undefined;
  loading: boolean;
  compact?: boolean;
}

export function slaTone(dueAt: string, now: number): 'overdue' | 'urgent' | 'ok' {
  const hours = (Date.parse(dueAt) - now) / 3_600_000;
  if (hours < 0) return 'overdue';
  if (hours < URGENT_HOURS) return 'urgent';
  return 'ok';
}

// Open tickets, nearest SLA deadline first, with the time left counting down.
export default function TicketList({ tickets, loading, compact = false }: TicketListProps) {
  const { t, i18n } = useTranslation('maintenance');
  const now = useNow(30_000);
  const sorted = [...(tickets ?? [])].sort((a, b) => a.slaDueAt.localeCompare(b.slaDueAt));
  // The dashboard's compact list is already cut to the nearest deadlines; the full list pages.
  const page = usePagination(sorted, compact ? Math.max(sorted.length, 1) : TICKETS_PAGE_SIZE);

  return (
    <>
      <ul className={`tickets ${compact ? 'tickets-compact' : ''}`} aria-busy={loading}>
        {loading &&
          [0, 1, 2].map((i) => (
            <li key={i} className="ticket" aria-hidden="true">
              <Skeleton width={56} height={14} />
              <Skeleton width="60%" height={12} />
              <Skeleton width={70} height={12} />
            </li>
          ))}
        {!loading && sorted.length === 0 && (
          <li>
            <EmptyState title={t('list.emptyTitle')}>{t('list.emptyBody')}</EmptyState>
          </li>
        )}
        {page.items.map((ticket) => {
          const tone = slaTone(ticket.slaDueAt, now);
          const minutes = (Date.parse(ticket.slaDueAt) - now) / 60_000;
          return (
            <li key={ticket.id} className={`ticket ticket-${tone}`}>
              <span className="num ticket-code">{ticket.vehicleCode}</span>
              <span className="ticket-main">
                <span className="ticket-desc">{ticket.description}</span>
                {!compact && ticket.maintenancePending && (
                  <span className="ticket-sub">{t('pending')}</span>
                )}
              </span>
              <span className="num ticket-due">
                {tone === 'overdue'
                  ? t('list.overdue', { time: formatDuration(-minutes, i18n.language) })
                  : t('list.left', { time: formatDuration(minutes, i18n.language) })}
              </span>
            </li>
          );
        })}
      </ul>
      {!compact && (
        <Pagination
          page={page.page}
          total={sorted.length}
          pageSize={TICKETS_PAGE_SIZE}
          onChange={page.setPage}
          label={t('list.title')}
        />
      )}
    </>
  );
}
