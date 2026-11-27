import { useTranslation } from 'react-i18next';
import type { Hub } from '../../backend/types';
import { EmptyState, Panel, Skeleton } from '../../components/ui/ui';
import { occupancyLevel, suggestRedistribution } from './occupancy';

interface HubOccupancyProps {
  hubs: Hub[] | undefined;
  loading: boolean;
  selectedHubId: string | null;
  onSelect: (hubId: string | null) => void;
  onRegisterHub: () => void;
}

// How full each hub is, and which bikes to move (RIA-120). A row filters the
// bike table to that hub.
export default function HubOccupancy({
  hubs,
  loading,
  selectedHubId,
  onSelect,
  onRegisterHub,
}: HubOccupancyProps) {
  const { t } = useTranslation('fleet');
  const suggestion = hubs ? suggestRedistribution(hubs) : null;

  return (
    <Panel title={t('hubs.title')} titleId="hubs-title">
      <ul className="occupancy" aria-busy={loading}>
        {loading &&
          [0, 1, 2, 3].map((i) => (
            <li key={i} className="occupancy-row" aria-hidden="true">
              <Skeleton width={110} height={12} />
              <Skeleton width="100%" height={10} radius={5} />
              <Skeleton width={40} height={12} />
            </li>
          ))}
        {hubs?.map((hub) => {
          const level = occupancyLevel(hub);
          const pct = hub.capacity > 0 ? Math.min(hub.dockedBikes / hub.capacity, 1) : 0;
          const selected = hub.id === selectedHubId;
          return (
            <li key={hub.id}>
              <button
                type="button"
                className={`occupancy-row occupancy-${level} ${selected ? 'is-selected' : ''}`}
                aria-pressed={selected}
                onClick={() => onSelect(selected ? null : hub.id)}
              >
                <span className="occupancy-name">
                  {hub.name}
                  {level !== 'ok' && (
                    <span className="occupancy-flag">{t(`hubs.level.${level}`)}</span>
                  )}
                </span>
                <span className="occupancy-track" aria-hidden="true">
                  <span className="occupancy-fill" style={{ transform: `scaleX(${pct})` }} />
                </span>
                <span
                  className="num occupancy-count"
                  aria-label={t('hubs.countLabel', {
                    docked: hub.dockedBikes,
                    capacity: hub.capacity,
                  })}
                >
                  {hub.dockedBikes}/{hub.capacity}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {hubs && hubs.length === 0 && (
        <EmptyState
          tone="note"
          title={t('hubs.emptyTitle')}
          action={
            <button type="button" className="btn btn-secondary btn-md sq" onClick={onRegisterHub}>
              {t('actions.registerHub')}
            </button>
          }
        >
          {t('hubs.emptyBody')}
        </EmptyState>
      )}
      {suggestion && (
        <p className="occupancy-tip">
          {t('hubs.suggestion', {
            count: suggestion.bikes,
            from: suggestion.from.name,
            to: suggestion.to.name,
          })}
        </p>
      )}
    </Panel>
  );
}
