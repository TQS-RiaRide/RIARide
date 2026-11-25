import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { HOTELS } from '../../backend/demoData';
import { useRegisterStay } from '../../backend/queries';
import type { Stay } from '../../backend/types';
import { Button, Icon } from '../../components/ui/ui';
import { refusedFields, useErrorText } from '../../lib/errors';
import { formatDay } from '../../lib/format';

function isoDate(offsetDays: number): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

// At check-in the hotel registers the stay and gets the code the guest signs up with (RIA-50, BR1).
export default function RegisterStayForm() {
  const { t, i18n } = useTranslation('hotel');
  const register = useRegisterStay();
  const errorText = useErrorText();
  const ids = { name: useId(), hotel: useId(), in: useId(), out: useId(), error: useId() };
  const [values, setValues] = useState({
    guestName: '',
    hotelId: HOTELS[0].id as string,
    checkIn: isoDate(0),
    checkOut: isoDate(3),
  });
  const [invalid, setInvalid] = useState<string[]>([]);
  const [created, setCreated] = useState<Stay | null>(null);

  const set = (key: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const local = [
      ...(values.guestName.trim() ? [] : ['guestName']),
      ...(values.checkOut > values.checkIn ? [] : ['checkOut']),
    ];
    setInvalid(local);
    if (local.length > 0) return;
    register.mutate(
      { ...values, guestName: values.guestName.trim() },
      {
        onSuccess: (stay) => {
          setCreated(stay);
          setValues((current) => ({ ...current, guestName: '' }));
        },
        onError: (error) => setInvalid(refusedFields(error)),
      },
    );
  };

  const copy = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      toast.success(t('stay.copied'));
    } catch {
      toast.error(t('stay.copyFailed'));
    }
  };

  const fieldProps = (field: string) => ({
    'aria-invalid': invalid.includes(field) || undefined,
    'aria-describedby': invalid.includes(field) ? ids.error : undefined,
  });

  return (
    <section className="panel sq stay-panel" aria-labelledby="stay-title">
      <header className="panel-head">
        <h2 id="stay-title" className="panel-title">
          {t('stay.title')}
        </h2>
      </header>
      {created && (
        <div className="stay-code-card sq" role="status">
          <p className="stay-code-label">{t('stay.codeFor', { name: created.guestName })}</p>
          <p className="stay-code num">{created.stayCode}</p>
          <p className="stay-code-dates">
            {formatDay(created.checkIn, i18n.language)} →{' '}
            {formatDay(created.checkOut, i18n.language)}
          </p>
          <Button variant="inverse" onClick={() => void copy(created.stayCode)}>
            <Icon name="copy" size={16} />
            {t('stay.copy')}
          </Button>
        </div>
      )}
      <form className="stay-form" onSubmit={submit} noValidate>
        <div className="field">
          <label className="field-label" htmlFor={ids.name}>
            {t('stay.guestName')}
          </label>
          <input
            id={ids.name}
            className="input sq"
            value={values.guestName}
            onChange={set('guestName')}
            autoComplete="off"
            maxLength={120}
            {...fieldProps('guestName')}
          />
        </div>
        <div className="field">
          <label className="field-label" htmlFor={ids.hotel}>
            {t('stay.hotel')}
          </label>
          <select
            id={ids.hotel}
            className="input sq"
            value={values.hotelId}
            onChange={set('hotelId')}
            {...fieldProps('hotelId')}
          >
            {HOTELS.map((hotel) => (
              <option key={hotel.id} value={hotel.id}>
                {hotel.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field-row">
          <div className="field">
            <label className="field-label" htmlFor={ids.in}>
              {t('stay.checkIn')}
            </label>
            <input
              id={ids.in}
              type="date"
              className="input num sq"
              value={values.checkIn}
              onChange={set('checkIn')}
              {...fieldProps('checkIn')}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor={ids.out}>
              {t('stay.checkOut')}
            </label>
            <input
              id={ids.out}
              type="date"
              className="input num sq"
              value={values.checkOut}
              min={values.checkIn}
              onChange={set('checkOut')}
              {...fieldProps('checkOut')}
            />
          </div>
        </div>
        {(invalid.length > 0 || register.isError) && (
          <p id={ids.error} role="alert" className="form-error">
            {invalid[0]
              ? t(`stay.errors.${invalid[0]}`, { defaultValue: t('stay.errors.invalid') })
              : errorText(register.error, {
                  '403': 'hotel:stay.errors.otherHotel',
                  '422': 'hotel:stay.errors.checkOut',
                  '404': 'hotel:stay.errors.hotel',
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
          {t('stay.submit')}
        </Button>
      </form>
    </section>
  );
}
