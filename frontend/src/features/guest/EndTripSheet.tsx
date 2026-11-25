import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useEndTrip, useHubs } from '../../backend/queries';
import Sheet from '../../components/ui/Sheet';
import { Button, Icon, Skeleton } from '../../components/ui/ui';
import { useErrorText } from '../../lib/errors';

interface EndTripSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tripId: string;
  onEnded: (hubName: string) => void;
}

// A trip ends only at a hub with a free slot (BR10). Full hubs stay in the list, disabled, so the
// guest sees why; hubs with room come first.
export default function EndTripSheet({ open, onOpenChange, tripId, onEnded }: EndTripSheetProps) {
  const { t } = useTranslation('guest');
  const hubs = useHubs();
  const end = useEndTrip();
  const errorText = useErrorText();
  const [hubId, setHubId] = useState<string | null>(null);

  const ordered = [...(hubs.data ?? [])].sort(
    (a, b) => Number(b.freeSlots > 0) - Number(a.freeSlots > 0) || a.name.localeCompare(b.name),
  );
  const chosen = ordered.find((h) => h.id === hubId && h.freeSlots > 0);

  useEffect(() => {
    if (open) end.reset();
    // reset only when the sheet opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = () => {
    if (!chosen) return;
    end.mutate(
      { tripId, hubId: chosen.id },
      {
        onSuccess: () => {
          onOpenChange(false);
          onEnded(chosen.name);
        },
      },
    );
  };

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('end.title')}
      description={t('end.description')}
      footer={
        <Button
          variant="primary"
          size="lg"
          className="sheet-cta"
          onClick={submit}
          disabled={!chosen || end.isPending}
          busy={end.isPending}
        >
          {chosen ? t('end.submitAt', { hub: chosen.name }) : t('end.pick')}
        </Button>
      }
    >
      <div role="radiogroup" aria-label={t('end.pick')} className="choice-list">
        {hubs.isPending &&
          [0, 1, 2].map((i) => (
            <div key={i} className="choice" aria-hidden="true">
              <span className="choice-main">
                <Skeleton width={140} height={14} />
              </span>
            </div>
          ))}
        {ordered.map((hub) => {
          const full = hub.freeSlots === 0;
          const selected = hub.id === chosen?.id;
          return (
            <button
              key={hub.id}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={full}
              className="choice sq"
              onClick={() => setHubId(hub.id)}
            >
              <span className="choice-main">
                <span className="choice-title">{hub.name}</span>
                <span className={`choice-sub ${full ? 'choice-sub-warn' : ''}`}>
                  {full ? t('end.full') : t('end.free', { count: hub.freeSlots })}
                </span>
              </span>
              {selected && (
                <span className="choice-check">
                  <Icon name="check" size={20} />
                </span>
              )}
            </button>
          );
        })}
        {hubs.isSuccess && ordered.length === 0 && <p className="field-hint">{t('end.noHubs')}</p>}
      </div>
      {end.isError && (
        <p role="alert" className="form-error end-error">
          {errorText(end.error, {
            '409:Hub full': 'guest:end.errors.full',
            '409': 'guest:end.errors.notRunning',
            '404': 'guest:end.errors.unknownHub',
          })}
        </p>
      )}
    </Sheet>
  );
}
