import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import {
  openTicket,
  TicketError,
  type OpenedTicket,
  type OpenTicketRequest,
  type TicketErrorKind,
  type TicketField,
} from './api';

interface OpenTicketFormProps {
  getToken: () => Promise<string>;
  submit?: (token: string, request: OpenTicketRequest) => Promise<OpenedTicket>;
}

interface Failure {
  kind: TicketErrorKind;
  fields: TicketField[];
}

// Deadlines are shown in Vila Ria's time, whatever the operator's device says.
const VILA_RIA_TIME_ZONE = 'Europe/Lisbon';

function formatDeadline(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: VILA_RIA_TIME_ZONE,
  }).format(new Date(iso));
}

// An operator opens a maintenance ticket for a faulty bike (RIA-92). The bike leaves service at
// once, or, when it is on a trip, when the trip ends; the answer says which, and the deadline.
export default function OpenTicketForm({ getToken, submit = openTicket }: OpenTicketFormProps) {
  const { t, i18n } = useTranslation('maintenance');
  const headingId = useId();
  const codeId = useId();
  const codeHintId = useId();
  const descriptionId = useId();
  const errorId = useId();
  const [vehicleCode, setVehicleCode] = useState('');
  const [description, setDescription] = useState('');
  const [sending, setSending] = useState(false);
  const [opened, setOpened] = useState<OpenedTicket | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSending(true);
    setOpened(null);
    setFailure(null);
    let token: string;
    try {
      token = await getToken();
    } catch {
      // The session cannot be refreshed: retrying fails every time, as after a 401.
      setFailure({ kind: 'sessionExpired', fields: [] });
      setSending(false);
      return;
    }
    try {
      const ticket = await submit(token, { vehicleCode, description });
      setOpened(ticket);
      setVehicleCode('');
      setDescription('');
    } catch (error) {
      setFailure(
        error instanceof TicketError
          ? { kind: error.kind, fields: error.fields }
          : { kind: 'unexpected', fields: [] },
      );
    } finally {
      setSending(false);
    }
  };

  const isInvalid = (field: TicketField) => failure?.fields.includes(field) ?? false;
  // A refused field is described by the error, so a screen reader that comes back to it says why.
  const describedBy = (field: TicketField, ...others: string[]) =>
    [...others, ...(isInvalid(field) ? [errorId] : [])].join(' ') || undefined;

  const failureMessage = (current: Failure) => {
    if (current.kind !== 'invalid') return t(`openTicket.errors.${current.kind}`);
    const [field] = current.fields;
    return field ? t(`openTicket.errors.${field}`) : t('openTicket.errors.invalid');
  };

  return (
    <section aria-labelledby={headingId} className="open-ticket">
      <h3 id={headingId}>{t('openTicket.heading')}</h3>
      <p>{t('openTicket.intro')}</p>
      <form onSubmit={(event) => void onSubmit(event)} noValidate>
        <label htmlFor={codeId}>{t('openTicket.vehicleCode')}</label>
        <input
          id={codeId}
          name="vehicleCode"
          value={vehicleCode}
          onChange={(event) => setVehicleCode(event.target.value)}
          maxLength={32}
          autoComplete="off"
          aria-describedby={describedBy('vehicleCode', codeHintId)}
          aria-invalid={isInvalid('vehicleCode') ? 'true' : undefined}
        />
        <p id={codeHintId} className="field-hint">
          {t('openTicket.vehicleCodeHint')}
        </p>
        <label htmlFor={descriptionId}>{t('openTicket.description')}</label>
        <textarea
          id={descriptionId}
          name="description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={500}
          rows={3}
          aria-describedby={describedBy('description')}
          aria-invalid={isInvalid('description') ? 'true' : undefined}
        />
        <button type="submit" className="primary" disabled={sending}>
          {sending ? t('openTicket.sending') : t('openTicket.submit')}
        </button>
      </form>
      {failure && (
        <p id={errorId} role="alert" className="form-error">
          {failureMessage(failure)}
        </p>
      )}
      {opened && (
        <p role="status" className="form-success">
          {t('openTicket.opened', { code: opened.vehicleCode })}{' '}
          {opened.maintenancePending ? t('openTicket.pending') : t('openTicket.inMaintenance')}{' '}
          {t('openTicket.deadline', { deadline: formatDeadline(opened.slaDueAt, i18n.language) })}
        </p>
      )}
    </section>
  );
}
