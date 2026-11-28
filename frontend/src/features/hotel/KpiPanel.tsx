import { useTranslation } from 'react-i18next';
import { useKpis } from '../../backend/queries';
import { EmptyState, Icon, Panel, Skeleton } from '../../components/ui/ui';
import { formatPercent, intlLocale } from '../../lib/format';

// Business KPIs and technical metrics for the hotel group (RIA-122, RIA-123). Availability is
// drawn against the SLA target line; trips per day as plain bars with the latest day in orange.
export default function KpiPanel() {
  const { t, i18n } = useTranslation('hotel');
  const kpis = useKpis();
  const data = kpis.data;

  if (kpis.isPending) {
    return (
      <Panel title={t('kpis.title')} titleId="kpi-title">
        <div className="kpi-grid" aria-busy="true">
          <Skeleton width="100%" height={140} radius={12} />
          <Skeleton width="100%" height={140} radius={12} />
        </div>
      </Panel>
    );
  }

  if (!data) {
    return (
      <Panel title={t('kpis.title')} titleId="kpi-title">
        <EmptyState tone="note" title={t('kpis.unavailableTitle')}>
          {t('kpis.unavailableBody')}
        </EmptyState>
      </Panel>
    );
  }

  const maxTrips = Math.max(...data.tripsPerDay, 1);
  const average = Math.round(
    data.tripsPerDay.reduce((sum, n) => sum + n, 0) / Math.max(data.tripsPerDay.length, 1),
  );

  return (
    <Panel title={t('kpis.title')} titleId="kpi-title">
      <div className="kpi-grid">
        <div className="kpi-block">
          <h3 className="kpi-heading">{t('kpis.availability')}</h3>
          <ul className="sla-bars">
            {data.availability.map((row) => {
              const below = row.availabilityPct < data.slaTargetPct;
              return (
                <li key={row.hotel} className={`sla-row ${below ? 'sla-below' : ''}`}>
                  <span>{row.hotel}</span>
                  <span className="sla-track" aria-hidden="true">
                    <span
                      className="sla-fill"
                      style={{ transform: `scaleX(${row.availabilityPct / 100})` }}
                    />
                    <span className="sla-target" style={{ left: `${data.slaTargetPct}%` }} />
                  </span>
                  <span className="num sla-value">
                    {below && (
                      <span className="sla-flag" title={t('kpis.belowSla')}>
                        <Icon name="arrowDown" size={14} />
                        <span className="visually-hidden">{t('kpis.belowSla')}: </span>
                      </span>
                    )}
                    {formatPercent(row.availabilityPct, i18n.language)}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
        <div className="kpi-block">
          <div className="kpi-heading-row">
            <h3 className="kpi-heading">{t('kpis.tripsPerDay')}</h3>
            <span className="num kpi-caption">{t('kpis.average', { count: average })}</span>
          </div>
          {data.tripsPerDay.length > 0 && (
            <div
              className="trip-bars"
              role="img"
              aria-label={t('kpis.tripsLabel', {
                days: data.tripsPerDay.length,
                min: Math.min(...data.tripsPerDay),
                max: maxTrips,
              })}
            >
              {data.tripsPerDay.map((value, index) => (
                <span
                  key={index}
                  className={`trip-bar ${index === data.tripsPerDay.length - 1 ? 'trip-bar-today' : ''}`}
                  style={{ transform: `scaleY(${value / maxTrips})` }}
                />
              ))}
            </div>
          )}
          <dl className="kpi-stats">
            <div>
              <dt>{t('kpis.utilization')}</dt>
              <dd className="num">{formatPercent(data.utilizationPct, i18n.language)}</dd>
            </div>
            <div>
              <dt>{t('kpis.repair')}</dt>
              <dd className="num">{t('kpis.hours', { count: data.hoursToRepair })}</dd>
            </div>
            <div>
              <dt>{t('kpis.passes')}</dt>
              <dd className="num">{data.passesSold}</dd>
            </div>
          </dl>
        </div>
      </div>
      <dl className="tech-metrics">
        <div>
          <dt>{t('kpis.apiP95')}</dt>
          <dd className="num">{data.apiP95Ms} ms</dd>
        </div>
        <div>
          <dt>{t('kpis.errors')}</dt>
          <dd className="num">{formatPercent(data.errorRatePct, i18n.language, 1)}</dd>
        </div>
        <div>
          <dt>{t('kpis.telemetryLag')}</dt>
          <dd className="num">
            {t('kpis.seconds', {
              value: data.telemetryLagSeconds.toLocaleString(intlLocale(i18n.language)),
            })}
          </dd>
        </div>
      </dl>
    </Panel>
  );
}
