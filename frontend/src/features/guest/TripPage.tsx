import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { toast } from 'sonner';
import { PATHS } from '../../app/paths';
import { useHubs, useTrip } from '../../backend/queries';
import type { Trip } from '../../backend/types';
import { Button, EmptyState, Icon, Skeleton } from '../../components/ui/ui';
import { formatClock, formatDuration, formatTime } from '../../lib/format';
import { useNow } from '../../lib/hooks';
import EndTripSheet from './EndTripSheet';
import './guest.css';

export const INCLUDED_SECONDS = 3 * 3600; // BR7

function elapsedSeconds(trip: Trip, now: number): number {
  const from = Date.parse(trip.startedAt ?? trip.requestedAt);
  const to = trip.endedAt ? Date.parse(trip.endedAt) : now;
  return Math.max(0, (to - from) / 1000);
}

// One trip, from the unlock to the return. While telemetry has not confirmed the unlock the page
// polls every second (BR13); then it shows a running clock against the 3 h included (BR7).
export default function TripPage() {
  const { tripId } = useParams();
  const { t, i18n } = useTranslation('guest');
  const trip = useTrip(tripId);
  const hubs = useHubs();
  const now = useNow(1000);
  const [endOpen, setEndOpen] = useState(false);

  if (trip.isPending) {
    return (
      <div className="trip-page" aria-busy="true">
        <Skeleton width={160} height={14} />
        <Skeleton width="100%" height={180} radius={22} />
      </div>
    );
  }

  if (trip.isError || !trip.data) {
    return (
      <EmptyState
        title={t('trip.notFound')}
        action={<Link to={PATHS.guest}>{t('trip.backToBikes')}</Link>}
      >
        {t('trip.notFoundBody')}
      </EmptyState>
    );
  }

  const current = trip.data;
  const seconds = elapsedSeconds(current, now);
  const overtime = Math.max(0, seconds - INCLUDED_SECONDS);
  const endHub = hubs.data?.find((h) => h.id === current.endHubId);

  return (
    <div className="trip-page">
      <Link to={PATHS.guest} viewTransition className="back-link">
        <Icon name="back" size={18} />
        {t('trip.backToBikes')}
      </Link>

      <section
        className={`trip-card trip-${current.status.toLowerCase()} sq`}
        aria-labelledby="trip-title"
      >
        <div className="trip-card-head">
          <h1 id="trip-title" className="trip-title">
            <span className="trip-status-chip">
              <span className="trip-status-dot" aria-hidden="true" />
              {t(`trip.status.${current.status}`)}
            </span>
            <span className="num trip-bike" style={{ viewTransitionName: 'trip-bike' }}>
              {current.vehicleCode}
            </span>
          </h1>
        </div>

        {current.status === 'STARTING' && (
          <div className="trip-unlocking" role="status">
            <span className="unlock-ring" aria-hidden="true" />
            <p>{t('trip.unlocking')}</p>
          </div>
        )}

        {current.status === 'FAILED' && (
          <p role="alert" className="trip-failed">
            {t('trip.failed', { code: current.vehicleCode })}
          </p>
        )}

        {(current.status === 'IN_USE' || current.status === 'ENDED') && (
          <>
            <p
              className="trip-clock num"
              aria-label={t('trip.elapsedLabel', {
                time: formatDuration(seconds / 60, i18n.language),
              })}
            >
              {formatClock(seconds)}
            </p>
            <div className="trip-track" aria-hidden="true">
              <span
                className="trip-track-fill"
                style={{ transform: `scaleX(${Math.min(seconds / INCLUDED_SECONDS, 1)})` }}
              />
            </div>
            <p className="trip-note">
              {overtime > 0
                ? t('trip.overtime', { time: formatDuration(overtime / 60, i18n.language) })
                : t('trip.included', {
                    time: formatDuration((INCLUDED_SECONDS - seconds) / 60, i18n.language),
                    total: formatDuration(INCLUDED_SECONDS / 60, i18n.language),
                  })}
            </p>
          </>
        )}

        {current.status === 'ENDED' && current.endedAt && (
          <p className="trip-ended" role="status">
            {t('trip.endedAt', {
              hub: endHub?.name ?? t('trip.aHub'),
              time: formatTime(current.endedAt, i18n.language),
            })}
          </p>
        )}
      </section>

      {current.status === 'IN_USE' && (
        <Button
          variant="primary"
          size="lg"
          className="trip-end-btn"
          onClick={() => setEndOpen(true)}
        >
          {t('trip.end')}
        </Button>
      )}

      <EndTripSheet
        open={endOpen}
        onOpenChange={setEndOpen}
        tripId={current.id}
        onEnded={(hubName) => toast.success(t('trip.endedToast', { hub: hubName }))}
      />
    </div>
  );
}
