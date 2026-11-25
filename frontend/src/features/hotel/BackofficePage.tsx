import { useTranslation } from 'react-i18next';
import { HOTELS } from '../../backend/demoData';
import { usePassPrices, useStays } from '../../backend/queries';
import { EmptyState, Panel, Skeleton } from '../../components/ui/ui';
import { formatDay, formatMoney } from '../../lib/format';
import KpiPanel from './KpiPanel';
import RegisterStayForm from './RegisterStayForm';
import './hotel.css';
import Pagination from '../../components/ui/Pagination';
import { usePagination } from '../../lib/pagination';

// The hotel group's backoffice (scenario 3): register stays at check-in, follow the service's KPIs
// against the SLA, and see the base prices of the passes.
export const STAYS_PAGE_SIZE = 6;

export default function BackofficePage() {
  const { t, i18n } = useTranslation('hotel');
  const { t: tg } = useTranslation('guest');
  const stays = useStays();
  const prices = usePassPrices();
  const hotelName = (id: string) => HOTELS.find((h) => h.id === id)?.name ?? '—';
  const staysPage = usePagination(stays.data ?? [], STAYS_PAGE_SIZE);

  return (
    <div className="hotel-page">
      <header className="page-head">
        <div>
          <h1 className="page-title display">{t('title')}</h1>
        </div>
      </header>

      <div className="hotel-layout">
        <RegisterStayForm />
        <Panel title={t('stays.title')} titleId="stays-title">
          <ul className="stays" aria-busy={stays.isPending}>
            {stays.isPending &&
              [0, 1, 2].map((i) => (
                <li key={i} className="stay-row" aria-hidden="true">
                  <Skeleton width={140} height={14} />
                  <Skeleton width={90} height={14} />
                </li>
              ))}
            {stays.isSuccess && stays.data.length === 0 && (
              <li>
                <EmptyState title={t('stays.emptyTitle')} />
              </li>
            )}
            {staysPage.items.map((stay) => (
              <li key={stay.id} className="stay-row">
                <span className="stay-main">
                  <span className="stay-name">{stay.guestName}</span>
                  <span className="stay-sub">
                    {hotelName(stay.hotelId)} · {formatDay(stay.checkIn, i18n.language)} →{' '}
                    {formatDay(stay.checkOut, i18n.language)}
                  </span>
                </span>
                <span className="num stay-code-small">{stay.stayCode}</span>
              </li>
            ))}
          </ul>
          <Pagination
            page={staysPage.page}
            total={staysPage.total}
            pageSize={STAYS_PAGE_SIZE}
            onChange={staysPage.setPage}
            label={t('stays.title')}
          />
        </Panel>
      </div>

      <KpiPanel />

      <Panel title={t('prices.title')} titleId="prices-title">
        <dl className="prices">
          {prices.data?.map((price) => (
            <div key={price.passType} className="price-row">
              <dt>
                {tg(`passTypes.${price.passType}.name`)}
                <span className="price-rule">{tg(`passTypes.${price.passType}.rule`)}</span>
              </dt>
              <dd className="num">
                {formatMoney(price.priceCents, price.currency, i18n.language)}
              </dd>
            </div>
          ))}
          {prices.data?.[0] && (
            <div className="price-row">
              <dt>{t('prices.deposit')}</dt>
              <dd className="num">
                {formatMoney(prices.data[0].depositCents, prices.data[0].currency, i18n.language)}
              </dd>
            </div>
          )}
        </dl>
      </Panel>
    </div>
  );
}
