import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { PATHS } from '../../app/paths';
import { useChangeVehicleStatus, useFleet, useHubs, useTickets } from '../../backend/queries';
import type { FleetVehicle, VehicleStatus } from '../../backend/types';
import { Button } from '../../components/ui/ui';
import { useErrorText } from '../../lib/errors';
import { formatRelative } from '../../lib/format';
import { useNow, useShortcuts } from '../../lib/hooks';
import { usePageCommands } from '../command/commands';
import { useAuth } from '../auth/useAuth';
import TicketList from '../maintenance/TicketList';
import HubOccupancy from './HubOccupancy';
import RegisterBikeSheet from './RegisterBikeSheet';
import RegisterHubSheet from './RegisterHubSheet';
import VehiclesTable, { type StatusFilter } from './VehiclesTable';
import { allowedChanges } from './VehicleActions';
import './fleet.css';

const STATUS_FILTERS: StatusFilter[] = [
  'ALL',
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'MAINTENANCE',
  'RETIRED',
  'LOW_BATTERY',
];

function asStatusFilter(value: string | null): StatusFilter {
  return STATUS_FILTERS.find((filter) => filter === value) ?? 'ALL';
}

// The operator's morning view (scenario 2): hub occupancy, tickets by SLA deadline, and every bike.
export default function FleetPage() {
  const { t, i18n } = useTranslation('fleet');
  const { t: tui } = useTranslation('ui');
  const errorText = useErrorText();
  const { state } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const fleet = useFleet();
  const hubs = useHubs();
  const tickets = useTickets();
  const changeStatus = useChangeVehicleStatus();
  const now = useNow(5000);
  const searchRef = useRef<HTMLInputElement>(null);
  const [hubOpen, setHubOpen] = useState(false);
  const [bikeOpen, setBikeOpen] = useState(false);

  const canOpenTickets =
    state.status === 'authenticated' && state.user.workspaces.includes('MAINTENANCE_TICKETS');
  const query = params.get('q') ?? '';
  const status = asStatusFilter(params.get('status'));
  const hubId = params.get('hub');
  const hub = hubs.data?.find((h) => h.id === hubId);

  const page = Number(params.get('page') ?? '1');

  // A new filter starts again on the first page of what it finds.
  const setParam = (key: string, value: string | null) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value === null || value === '' || value === 'ALL') next.delete(key);
        else next.set(key, value);
        if (key !== 'page') next.delete('page');
        return next;
      },
      { replace: true, preventScrollReset: true },
    );

  const change = (vehicle: FleetVehicle, next: VehicleStatus, isUndo = false) => {
    if (!vehicle.id) return;
    const previous = vehicle.status;
    changeStatus.mutate(
      { vehicleId: vehicle.id, status: next },
      {
        onSuccess: () => {
          if (isUndo) {
            toast(t('status.restored', { code: vehicle.code }));
            return;
          }
          const canUndo = allowedChanges(next).includes(previous);
          toast(t('status.changed', { code: vehicle.code, status: tui(`vehicleStatus.${next}`) }), {
            action: canUndo
              ? {
                  label: t('status.undo'),
                  onClick: () => change({ ...vehicle, status: next }, previous, true),
                }
              : undefined,
          });
        },
        onError: (error) =>
          toast.error(
            errorText(
              error,
              { '409': 'fleet:status.refused', '404': 'fleet:status.notFound' },
              { code: vehicle.code },
            ),
          ),
      },
    );
  };

  const openTicketFor = (vehicle: FleetVehicle) =>
    void navigate(`${PATHS.maintenance}?code=${encodeURIComponent(vehicle.code)}`, {
      viewTransition: true,
    });

  useShortcuts([
    { keys: '/', run: () => searchRef.current?.focus() },
    { keys: 'b', run: () => setBikeOpen(true) },
    { keys: 'n', run: () => setHubOpen(true) },
  ]);

  usePageCommands('fleet', [
    {
      id: 'register-bike',
      label: t('actions.registerBike'),
      shortcut: 'b',
      run: () => setBikeOpen(true),
    },
    {
      id: 'register-hub',
      label: t('actions.registerHub'),
      shortcut: 'n',
      run: () => setHubOpen(true),
    },
    {
      id: 'search-bikes',
      label: t('actions.search'),
      shortcut: '/',
      run: () => searchRef.current?.focus(),
    },
    {
      id: 'low-battery',
      label: t('actions.lowBattery'),
      run: () => setParam('status', 'LOW_BATTERY'),
    },
  ]);

  const updatedAt = fleet.dataUpdatedAt ? new Date(fleet.dataUpdatedAt).toISOString() : null;

  return (
    <div className="fleet-page">
      <header className="page-head">
        <div>
          <h1 className="page-title display">{t('title')}</h1>
          <p className="page-sub">
            <span
              className={`updated-dot ${fleet.isFetching ? 'is-fetching' : ''}`}
              aria-hidden="true"
            />
            <span aria-live="polite">
              {fleet.isFetching && !fleet.isPending
                ? t('refreshing')
                : updatedAt
                  ? t('updated', { when: formatRelative(updatedAt, now, i18n.language) })
                  : t('loading')}
            </span>
          </p>
        </div>
        <div className="page-actions">
          <Button onClick={() => setHubOpen(true)} shortcut="n">
            {t('actions.registerHub')}
          </Button>
          <Button variant="primary" onClick={() => setBikeOpen(true)} shortcut="b">
            {t('actions.registerBike')}
          </Button>
        </div>
      </header>

      <div className="page-grid">
        <div className="page-grid-2">
          <HubOccupancy
            hubs={hubs.data}
            loading={hubs.isPending}
            selectedHubId={hubId}
            onSelect={(id) => setParam('hub', id)}
            onRegisterHub={() => setHubOpen(true)}
          />
          <section aria-labelledby="sla-title" className="panel sq">
            <header className="panel-head">
              <h2 id="sla-title" className="panel-title">
                {t('tickets.title')}
              </h2>
              {canOpenTickets && (
                <div className="panel-actions">
                  <Link to={PATHS.maintenance} viewTransition className="panel-link">
                    {t('tickets.all')}
                  </Link>
                </div>
              )}
            </header>
            <TicketList tickets={tickets.data?.slice(0, 4)} loading={tickets.isPending} compact />
          </section>
        </div>

        <VehiclesTable
          vehicles={fleet.data}
          loading={fleet.isPending}
          query={query}
          status={status}
          hub={hub}
          searchRef={searchRef}
          onQuery={(value) => setParam('q', value)}
          onStatus={(value) => setParam('status', value)}
          onClearHub={() => setParam('hub', null)}
          onChangeStatus={change}
          onOpenTicket={openTicketFor}
          page={page}
          onPage={(next) => setParam('page', next === 1 ? null : String(next))}
        />
      </div>

      <RegisterHubSheet open={hubOpen} onOpenChange={setHubOpen} />
      <RegisterBikeSheet
        open={bikeOpen}
        onOpenChange={setBikeOpen}
        defaultHubId={hubId ?? undefined}
      />
    </div>
  );
}
