import { useTranslation } from 'react-i18next';
import { useHubVehicles } from '../../backend/queries';
import { BatteryMeter, Button, EmptyState, Skeleton } from '../../components/ui/ui';
import { estimatedRangeKm } from '../../lib/format';
import Pagination from '../../components/ui/Pagination';
import { usePagination } from '../../lib/pagination';

interface HubVehiclesProps {
  hubId: string | undefined;
  hubsLoading: boolean;
  startingCode: string | undefined;
  onStart: (code: string) => void;
  onUseCode: () => void;
  /** Without a usable pass, Unlock is not the main action, so it is not drawn as one. */
  quiet?: boolean;
}

function SkeletonRows() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <li key={i} className="bike-row" aria-hidden="true">
          <span className="bike-main">
            <Skeleton width={64} height={14} />
            <Skeleton width={120} height={10} />
          </span>
          <Skeleton width={116} height={44} radius={14} />
        </li>
      ))}
    </>
  );
}

// The bookable bikes at one hub, the fullest battery first (RIA-53). Bikes that cannot be
// unlocked (booked, under repair, or below 20 %, BR12) are only counted. Skeleton rows hold the
// layout while the list loads, and the cached list stays on screen while it refreshes.
export const BIKES_PAGE_SIZE = 8;

export default function HubVehicles({
  hubId,
  hubsLoading,
  startingCode,
  onStart,
  onUseCode,
  quiet = false,
}: HubVehiclesProps) {
  const { t } = useTranslation('guest');
  const vehicles = useHubVehicles(hubId);
  const bookable = (vehicles.data ?? [])
    .filter((vehicle) => vehicle.bookable)
    .sort((a, b) => (b.batteryPct ?? 0) - (a.batteryPct ?? 0));
  const page = usePagination(bookable, BIKES_PAGE_SIZE, hubId ?? '');

  if (!hubId && !hubsLoading) {
    return (
      <EmptyState
        tone="note"
        title={t('bikes.noHubsTitle')}
        action={
          <Button variant="primary" onClick={onUseCode}>
            {t('home.haveCode')}
          </Button>
        }
      >
        {t('bikes.noHubsBody')}
      </EmptyState>
    );
  }

  const all = vehicles.data ?? [];
  const unavailable = all.length - bookable.length;

  return (
    <div className="bike-panel sq">
      {vehicles.isError && (
        <p role="alert" className="form-error bike-error">
          {t('bikes.loadError')}
        </p>
      )}
      <ul className="bike-list" aria-busy={vehicles.isPending || hubsLoading}>
        {(vehicles.isPending || hubsLoading) && <SkeletonRows />}
        {vehicles.isSuccess && all.length === 0 && (
          <li>
            <EmptyState title={t('bikes.emptyTitle')}>{t('bikes.emptyBody')}</EmptyState>
          </li>
        )}
        {page.items.map((vehicle) => (
          <li key={vehicle.code} className="bike-row">
            <span className="bike-main">
              <span className="bike-code num">{vehicle.code}</span>
              <span className="bike-meta">
                <BatteryMeter pct={vehicle.batteryPct} />
                {vehicle.batteryPct !== null && (
                  <span className="num">
                    {t('bikes.range', { km: estimatedRangeKm(vehicle.batteryPct) })}
                  </span>
                )}
              </span>
            </span>
            <Button
              variant={quiet ? 'secondary' : 'primary'}
              onClick={() => onStart(vehicle.code)}
              busy={startingCode === vehicle.code}
              disabled={startingCode !== undefined}
              aria-label={t('bikes.unlockBike', { code: vehicle.code })}
            >
              {t('bikes.unlock')}
            </Button>
          </li>
        ))}
        {unavailable > 0 && (
          <li className="bike-row bike-row-off bike-unavailable">
            {t('bikes.unavailable', { count: unavailable })}
          </li>
        )}
      </ul>
      <Pagination
        page={page.page}
        total={page.total}
        pageSize={BIKES_PAGE_SIZE}
        onChange={page.setPage}
        label={t('home.bikes')}
      />
    </div>
  );
}
