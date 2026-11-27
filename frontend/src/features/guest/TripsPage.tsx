import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { PATHS } from '../../app/paths';
import { useCurrentPass, useHubs, useMyTrips } from '../../backend/queries';
import type { Trip } from '../../backend/types';
import { EmptyState, Icon, Skeleton } from '../../components/ui/ui';
import { formatClock, formatDateTime, formatMoney } from '../../lib/format';
import './guest.css';
import Pagination from '../../components/ui/Pagination';
import { usePagination } from '../../lib/pagination';

function durationSeconds(trip: Trip): number | null {
  if (!trip.startedAt || !trip.endedAt) return null;
  return (Date.parse(trip.endedAt) - Date.parse(trip.startedAt)) / 1000;
}

// The guest's trips and what they paid, newest first (RIA-118). Charges are the pass and its
// deposit; overtime appears here once the backend reports it (RIA-88).
export const TRIPS_PAGE_SIZE = 8;

export default function TripsPage() {
  const { t, i18n } = useTranslation('guest');
  const trips = useMyTrips();
  const pass = useCurrentPass();
  const hubs = useHubs();
  const hubName = (id: string | null) => hubs.data?.find((h) => h.id === id)?.name;
  const sorted = [...(trips.data ?? [])].sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
  const page = usePagination(sorted, TRIPS_PAGE_SIZE);

  return (
    <div className="trips-page">
      <h1 className="guest-title display">{t('trips.title')}</h1>

      <section aria-labelledby="trips-list" className="history-block">
        <h2 id="trips-list" className="section-title">
          {t('trips.trips')}
        </h2>
        <ul className="history sq" aria-busy={trips.isPending}>
          {trips.isPending &&
            [0, 1].map((i) => (
              <li key={i} className="history-row" aria-hidden="true">
                <Skeleton width={120} height={14} />
                <Skeleton width={60} height={14} />
              </li>
            ))}
          {trips.isSuccess && sorted.length === 0 && (
            <li>
              <EmptyState title={t('trips.emptyTitle')}>{t('trips.emptyBody')}</EmptyState>
            </li>
          )}
          {page.items.map((trip) => {
            const seconds = durationSeconds(trip);
            return (
              <li key={trip.id}>
                <Link
                  to={PATHS.guestTrip(trip.id)}
                  viewTransition
                  className="history-row history-link"
                >
                  <span className="history-main">
                    <span className="history-title">
                      <span className="num">{trip.vehicleCode}</span>
                      {trip.endHubId && (
                        <span className="history-to">
                          {' '}
                          → {hubName(trip.endHubId) ?? t('trip.aHub')}
                        </span>
                      )}
                    </span>
                    <span className="history-sub">
                      {formatDateTime(trip.requestedAt, i18n.language)}
                      {trip.status !== 'ENDED' && ` · ${t(`trip.status.${trip.status}`)}`}
                    </span>
                  </span>
                  <span className="num history-value">
                    {seconds === null ? '—' : formatClock(seconds)}
                  </span>
                  <Icon name="chevron" size={16} />
                </Link>
              </li>
            );
          })}
        </ul>
        <Pagination
          page={page.page}
          total={page.total}
          pageSize={TRIPS_PAGE_SIZE}
          onChange={page.setPage}
          label={t('trips.trips')}
        />
      </section>

      <section aria-labelledby="charges" className="history-block">
        <h2 id="charges" className="section-title">
          {t('trips.charges')}
        </h2>
        <ul className="history sq">
          {pass.data ? (
            <>
              <li className="history-row">
                <span className="history-main">
                  <span className="history-title">{t(`passTypes.${pass.data.passType}.name`)}</span>
                </span>
                <span className="num history-value">
                  {formatMoney(pass.data.priceCents, pass.data.currency, i18n.language)}
                </span>
              </li>
              <li className="history-row">
                <span className="history-main">
                  <span className="history-title">{t('pass.deposit')}</span>
                  <span className="history-sub">{t('trips.depositHeld')}</span>
                </span>
                <span className="num history-value">
                  {formatMoney(pass.data.depositCents, pass.data.currency, i18n.language)}
                </span>
              </li>
            </>
          ) : (
            <li>
              <EmptyState title={t('trips.noChargesTitle')} />
            </li>
          )}
        </ul>
      </section>
    </div>
  );
}
