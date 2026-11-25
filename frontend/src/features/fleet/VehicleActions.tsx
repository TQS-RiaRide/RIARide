import * as Menu from '@radix-ui/react-dropdown-menu';
import { useTranslation } from 'react-i18next';
import { SETTABLE_STATUSES, type FleetVehicle, type VehicleStatus } from '../../backend/types';
import { Icon } from '../../components/ui/ui';

interface VehicleActionsProps {
  vehicle: FleetVehicle;
  onChangeStatus: (vehicle: FleetVehicle, status: VehicleStatus) => void;
  onOpenTicket: (vehicle: FleetVehicle) => void;
}

/** Which manual changes the lifecycle allows from a status (RIA-91); the backend has the last word. */
export function allowedChanges(status: VehicleStatus): VehicleStatus[] {
  if (status === 'RETIRED') return [];
  return SETTABLE_STATUSES.filter(
    (next) => next !== status && !(status === 'IN_USE' && next === 'RETIRED'),
  );
}

// The row menu. Its "Change status" submenu keeps a safe triangle: moving the pointer diagonally
// from the item to the submenu does not close it on the way (Radix pointer grace area).
export default function VehicleActions({
  vehicle,
  onChangeStatus,
  onOpenTicket,
}: VehicleActionsProps) {
  const { t } = useTranslation('fleet');
  const { t: tui } = useTranslation('ui');
  const changes = allowedChanges(vehicle.status);
  const canChange = vehicle.id !== null && changes.length > 0;

  return (
    <Menu.Root>
      <Menu.Trigger
        className="icon-btn hit-slop sq"
        aria-label={t('table.actionsFor', { code: vehicle.code })}
      >
        <Icon name="more" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Content className="menu sq" align="end" sideOffset={6} collisionPadding={12}>
          <Menu.Sub>
            <Menu.SubTrigger className="menu-item" disabled={!canChange}>
              {t('table.changeStatus')}
              <span className="menu-chevron">
                <Icon name="chevron" size={16} />
              </span>
            </Menu.SubTrigger>
            <Menu.Portal>
              <Menu.SubContent
                className="menu sq"
                sideOffset={4}
                alignOffset={-6}
                collisionPadding={12}
              >
                {changes.map((status) => (
                  <Menu.Item
                    key={status}
                    className="menu-item"
                    onSelect={() => onChangeStatus(vehicle, status)}
                  >
                    <span
                      className={`menu-dot menu-dot-${status.toLowerCase()}`}
                      aria-hidden="true"
                    />
                    {tui(`vehicleStatus.${status}`)}
                  </Menu.Item>
                ))}
              </Menu.SubContent>
            </Menu.Portal>
          </Menu.Sub>
          <Menu.Item
            className="menu-item"
            disabled={vehicle.status === 'RETIRED'}
            onSelect={() => onOpenTicket(vehicle)}
          >
            {t('table.openTicket')}
          </Menu.Item>
        </Menu.Content>
      </Menu.Portal>
    </Menu.Root>
  );
}
