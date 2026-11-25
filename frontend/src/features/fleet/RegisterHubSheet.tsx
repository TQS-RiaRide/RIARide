import { useEffect, useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { HOTELS } from '../../backend/demoData';
import { useRegisterHub } from '../../backend/queries';
import type { HubKind } from '../../backend/types';
import Sheet from '../../components/ui/Sheet';
import { Button } from '../../components/ui/ui';
import { refusedFields, useErrorText } from '../../lib/errors';

interface RegisterHubSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const EMPTY = {
  name: '',
  kind: 'TOURIST_SPOT' as HubKind,
  hotel: HOTELS[0].name as string,
  latitude: '',
  longitude: '',
  capacity: '8',
};

function toNumber(value: string): number {
  return Number(value.replace(',', '.'));
}

// Registers a hub (RIA-56): a hotel's or a tourist spot's, where it is, and how many slots it has.
export default function RegisterHubSheet({ open, onOpenChange }: RegisterHubSheetProps) {
  const { t } = useTranslation('fleet');
  const register = useRegisterHub();
  const errorText = useErrorText();
  const [values, setValues] = useState(EMPTY);
  const [invalid, setInvalid] = useState<string[]>([]);
  const ids = {
    name: useId(),
    kind: useId(),
    hotel: useId(),
    lat: useId(),
    lng: useId(),
    cap: useId(),
    error: useId(),
  };

  useEffect(() => {
    if (open) {
      setValues(EMPTY);
      setInvalid([]);
      register.reset();
    }
    // reset only when the sheet opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = (key: keyof typeof EMPTY) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const local = [
      ...(values.name.trim() ? [] : ['name']),
      ...(Number.isFinite(toNumber(values.latitude)) && values.latitude !== '' ? [] : ['latitude']),
      ...(Number.isFinite(toNumber(values.longitude)) && values.longitude !== ''
        ? []
        : ['longitude']),
      ...(Number.isInteger(toNumber(values.capacity)) && toNumber(values.capacity) >= 1
        ? []
        : ['capacity']),
    ];
    setInvalid(local);
    if (local.length > 0) return;
    register.mutate(
      {
        name: values.name.trim(),
        kind: values.kind,
        hotel: values.kind === 'HOTEL' ? values.hotel : null,
        latitude: toNumber(values.latitude),
        longitude: toNumber(values.longitude),
        capacity: toNumber(values.capacity),
      },
      {
        onSuccess: (hub) => {
          onOpenChange(false);
          toast.success(t('registerHub.done', { name: hub.name, count: hub.capacity }));
        },
        onError: (error) => setInvalid(refusedFields(error)),
      },
    );
  };

  const fieldProps = (field: string) => ({
    'aria-invalid': invalid.includes(field) || undefined,
    'aria-describedby': invalid.includes(field) ? ids.error : undefined,
  });
  const firstInvalid = invalid[0];

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={t('registerHub.title')}>
      <form className="sheet-form" onSubmit={submit} noValidate>
        <div className="field">
          <label className="field-label" htmlFor={ids.name}>
            {t('registerHub.name')}
          </label>
          <input
            id={ids.name}
            className="input sq"
            value={values.name}
            onChange={set('name')}
            maxLength={120}
            {...fieldProps('name')}
          />
        </div>
        <div className="field-row">
          <div className="field">
            <label className="field-label" htmlFor={ids.kind}>
              {t('registerHub.kind')}
            </label>
            <select id={ids.kind} className="input sq" value={values.kind} onChange={set('kind')}>
              <option value="TOURIST_SPOT">{t('registerHub.kinds.TOURIST_SPOT')}</option>
              <option value="HOTEL">{t('registerHub.kinds.HOTEL')}</option>
            </select>
          </div>
          {values.kind === 'HOTEL' && (
            <div className="field">
              <label className="field-label" htmlFor={ids.hotel}>
                {t('registerHub.hotel')}
              </label>
              <select
                id={ids.hotel}
                className="input sq"
                value={values.hotel}
                onChange={set('hotel')}
                {...fieldProps('hotel')}
              >
                {HOTELS.map((hotel) => (
                  <option key={hotel.id} value={hotel.name}>
                    {hotel.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
        <div className="field-row">
          <div className="field">
            <label className="field-label" htmlFor={ids.lat}>
              {t('registerHub.latitude')}
            </label>
            <input
              id={ids.lat}
              className="input num sq"
              inputMode="decimal"
              placeholder="40.6405"
              value={values.latitude}
              onChange={set('latitude')}
              {...fieldProps('latitude')}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor={ids.lng}>
              {t('registerHub.longitude')}
            </label>
            <input
              id={ids.lng}
              className="input num sq"
              inputMode="decimal"
              placeholder="-8.6538"
              value={values.longitude}
              onChange={set('longitude')}
              {...fieldProps('longitude')}
            />
          </div>
        </div>
        <div className="field field-narrow">
          <label className="field-label" htmlFor={ids.cap}>
            {t('registerHub.capacity')}
          </label>
          <input
            id={ids.cap}
            className="input num sq"
            inputMode="numeric"
            value={values.capacity}
            onChange={set('capacity')}
            {...fieldProps('capacity')}
          />
        </div>
        {(firstInvalid || register.isError) && (
          <p id={ids.error} role="alert" className="form-error">
            {register.isError && !firstInvalid
              ? errorText(register.error, { '409': 'fleet:registerHub.errors.taken' })
              : t(`registerHub.errors.${firstInvalid}`, {
                  defaultValue: t('registerHub.errors.invalid'),
                })}
          </p>
        )}
        <Button
          type="submit"
          variant="primary"
          size="lg"
          busy={register.isPending}
          disabled={register.isPending}
        >
          {t('registerHub.submit')}
        </Button>
      </form>
    </Sheet>
  );
}
