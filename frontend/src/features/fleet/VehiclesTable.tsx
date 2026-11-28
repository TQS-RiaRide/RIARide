import { useId, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';
import {
  MIN_BOOKABLE_BATTERY,
  type FleetVehicle,
  type Hub,
  type VehicleStatus,
} from '../../backend/types';
import {
  BatteryMeter,
  EmptyState,
  Icon,
  Panel,
  Skeleton,
  StatusPill,
} from '../../components/ui/ui';
import VehicleActions from './VehicleActions';
import Pagination from '../../components/ui/Pagination';
import { paginate } from '../../lib/pagination';

export type StatusFilter = 'ALL' | VehicleStatus | 'LOW_BATTERY';

// Every chip is always shown, in this order, so the row never shifts when a count drops to zero.
export const FILTERS: StatusFilter[] = [
  'ALL',
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'MAINTENANCE',
  'RETIRED',
  'LOW_BATTERY',
];

export function filterVehicles(
  vehicles: readonly FleetVehicle[],
  { query, status, hubId }: { query: string; status: StatusFilter; hubId: string | null },
): FleetVehicle[] {
  const needle = query.trim().toLowerCase();
  return vehicles.filter((vehicle) => {
    if (hubId && vehicle.hubId !== hubId) return false;
    if (
      status === 'LOW_BATTERY' &&
      !(vehicle.batteryPct !== null && vehicle.batteryPct < MIN_BOOKABLE_BATTERY)
    )
      return false;
    if (status !== 'ALL' && status !== 'LOW_BATTERY' && vehicle.status !== status) return false;
    if (needle && !`${vehicle.code} ${vehicle.hubName ?? ''}`.toLowerCase().includes(needle))
      return false;
    return true;
  });
}

interface VehiclesTableProps {
  vehicles: FleetVehicle[] | undefined;
  loading: boolean;
  query: string;
  status: StatusFilter;
  hub: Hub | undefined;
  searchRef: RefObject<HTMLInputElement | null>;
  onQuery: (query: string) => void;
  onStatus: (status: StatusFilter) => void;
  onClearHub: () => void;
  onChangeStatus: (vehicle: FleetVehicle, status: VehicleStatus) => void;
  onOpenTicket: (vehicle: FleetVehicle) => void;
  page: number;
  onPage: (page: number) => void;
}

export const FLEET_PAGE_SIZE = 12;

// Every bike with its state, battery and hub. Filters live in the URL so a view can be shared.
export default function VehiclesTable(props: VehiclesTableProps) {
  const { vehicles, loading, query, status, hub, searchRef } = props;
  const { t } = useTranslation('fleet');
  const searchId = useId();
  const shown = vehicles ? filterVehicles(vehicles, { query, status, hubId: hub?.id ?? null }) : [];
  const page = paginate(shown, props.page, FLEET_PAGE_SIZE);
  const count = (filter: StatusFilter) =>
    vehicles
      ? filterVehicles(vehicles, { query: '', status: filter, hubId: hub?.id ?? null }).length
      : 0;

  return (
    <Panel
      title={
        <>
          {t('table.title')} <span className="num panel-count">{shown.length}</span>
        </>
      }
      titleId="bikes-title"
      actions={
        <div className="table-search">
          <label htmlFor={searchId} className="visually-hidden">
            {t('table.search')}
          </label>
          <Icon name="search" size={16} />
          <input
            id={searchId}
            ref={searchRef}
            className="input table-search-input"
            type="search"
            value={query}
            placeholder={t('table.search')}
            onChange={(event) => props.onQuery(event.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
        </div>
      }
    >
      <div className="filter-row" role="toolbar" aria-label={t('table.filters')}>
        {FILTERS.map((filter) => {
          const pressed = status === filter;
          const filterCount = count(filter);
          const isEmpty = filter !== 'ALL' && !loading && filterCount === 0;
          return (
            <button
              key={filter}
              type="button"
              className={`filter-chip ${isEmpty ? 'is-empty' : ''}`}
              aria-pressed={pressed}
              disabled={isEmpty && !pressed}
              onClick={() => props.onStatus(filter)}
            >
              {t(`table.filter.${filter}`)}
              {filter !== 'ALL' && <span className="num">{filterCount}</span>}
            </button>
          );
        })}
        {hub && (
          <button
            type="button"
            className="filter-chip filter-chip-hub"
            aria-pressed="true"
            onClick={props.onClearHub}
          >
            {hub.name}
            <Icon name="close" size={14} />
            <span className="visually-hidden">{t('table.clearHub')}</span>
          </button>
        )}
      </div>
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">{t('table.bike')}</th>
              <th scope="col">{t('table.hub')}</th>
              <th scope="col">{t('table.status')}</th>
              <th scope="col">{t('table.battery')}</th>
              <th scope="col">
                <span className="visually-hidden">{t('table.actions')}</span>
              </th>
            </tr>
          </thead>
          <tbody aria-busy={loading}>
            {loading &&
              [0, 1, 2, 3, 4].map((i) => (
                <tr key={i} aria-hidden="true">
                  <td>
                    <Skeleton width={60} height={12} />
                  </td>
                  <td>
                    <Skeleton width={110} height={12} />
                  </td>
                  <td>
                    <Skeleton width={84} height={20} radius={10} />
                  </td>
                  <td>
                    <Skeleton width={70} height={12} />
                  </td>
                  <td />
                </tr>
              ))}
            {page.items.map((vehicle) => (
              <tr
                key={vehicle.code}
                className={vehicle.status === 'RETIRED' ? 'row-muted' : undefined}
              >
                <th scope="row" className="num cell-code">
                  {vehicle.code}
                </th>
                <td>
                  {vehicle.hubName ?? (vehicle.status === 'IN_USE' ? t('table.onTrip') : '—')}
                </td>
                <td>
                  <StatusPill status={vehicle.status} />
                </td>
                <td>
                  <BatteryMeter pct={vehicle.batteryPct} />
                </td>
                <td className="cell-actions">
                  <VehicleActions
                    vehicle={vehicle}
                    onChangeStatus={props.onChangeStatus}
                    onOpenTicket={props.onOpenTicket}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination
          page={page.page}
          total={shown.length}
          pageSize={FLEET_PAGE_SIZE}
          onChange={props.onPage}
          label={t('table.title')}
        />
        {!loading && shown.length === 0 && (
          <EmptyState title={vehicles?.length ? t('table.noMatchTitle') : t('table.emptyTitle')}>
            {vehicles?.length ? t('table.noMatchBody') : t('table.emptyBody')}
          </EmptyState>
        )}
      </div>
    </Panel>
  );
}
