import { useEffect, useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { useHubs, useRegisterVehicle } from '../../backend/queries';
import Sheet from '../../components/ui/Sheet';
import { Button } from '../../components/ui/ui';
import { useErrorText } from '../../lib/errors';

interface RegisterBikeSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultHubId?: string;
}

// Registers an e-bike at a hub with a free slot (RIA-57). "Save and add another" keeps the sheet
// open for unloading a van of bikes in one go.
export default function RegisterBikeSheet({
  open,
  onOpenChange,
  defaultHubId,
}: RegisterBikeSheetProps) {
  const { t } = useTranslation('fleet');
  const hubs = useHubs();
  const register = useRegisterVehicle();
  const errorText = useErrorText();
  const [hubId, setHubId] = useState('');
  const [code, setCode] = useState('');
  const [another, setAnother] = useState(false);
  const ids = { hub: useId(), code: useId(), hint: useId(), error: useId() };
  const withRoom = hubs.data?.filter((h) => h.freeSlots > 0) ?? [];

  useEffect(() => {
    if (open) {
      setCode('');
      setHubId(defaultHubId ?? '');
      register.reset();
    }
    // reset only when the sheet opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const chosenHub = hubId || withRoom[0]?.id || '';

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!chosenHub || !code.trim()) return;
    register.mutate(
      { hubId: chosenHub, code: code.trim().toUpperCase() },
      {
        onSuccess: (vehicle) => {
          toast.success(t('registerBike.done', { code: vehicle.code }));
          if (another) {
            setCode('');
            register.reset();
          } else {
            onOpenChange(false);
          }
        },
      },
    );
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={t('registerBike.title')}>
      <form className="sheet-form" onSubmit={submit} noValidate>
        <div className="field">
          <label className="field-label" htmlFor={ids.hub}>
            {t('registerBike.hub')}
          </label>
          <select
            id={ids.hub}
            className="input sq"
            value={chosenHub}
            onChange={(event) => setHubId(event.target.value)}
            disabled={withRoom.length === 0}
          >
            {withRoom.map((hub) => (
              <option key={hub.id} value={hub.id}>
                {t('registerBike.hubOption', { name: hub.name, count: hub.freeSlots })}
              </option>
            ))}
          </select>
          {hubs.isSuccess && withRoom.length === 0 && (
            <p className="field-hint">{t('registerBike.noRoom')}</p>
          )}
        </div>
        <div className="field">
          <label className="field-label" htmlFor={ids.code}>
            {t('registerBike.code')}
          </label>
          <input
            id={ids.code}
            className="input input-code sq"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder="EB-701"
            maxLength={32}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            aria-describedby={register.isError ? `${ids.hint} ${ids.error}` : ids.hint}
            aria-invalid={register.isError || undefined}
          />
          <p id={ids.hint} className="field-hint">
            {t('registerBike.codeHint')}
          </p>
        </div>
        <label className="check">
          <input
            type="checkbox"
            checked={another}
            onChange={(event) => setAnother(event.target.checked)}
          />
          {t('registerBike.another')}
        </label>
        {register.isError && (
          <p id={ids.error} role="alert" className="form-error">
            {errorText(register.error, {
              '409:Hub full': 'fleet:registerBike.errors.full',
              '409': 'fleet:registerBike.errors.taken',
              '400': 'fleet:registerBike.errors.invalid',
            })}
          </p>
        )}
        <Button
          type="submit"
          variant="primary"
          size="lg"
          busy={register.isPending}
          disabled={register.isPending || !chosenHub || !code.trim()}
        >
          {t('registerBike.submit')}
        </Button>
      </form>
    </Sheet>
  );
}
