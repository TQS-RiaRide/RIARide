import { useId, useState, type FormEvent, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/ui/ui';
import { formatDateTime } from '../../lib/format';
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
  /** A bike code to start with, for example when coming from the fleet table. */
  initialCode?: string;
  onOpened?: (ticket: OpenedTicket) => void;
  codeRef?: RefObject<HTMLInputElement | null>;
}

interface Failure {
  kind: TicketErrorKind;
  fields: TicketField[];
}

// An operator opens a maintenance ticket for a faulty bike (RIA-92). The bike leaves service at
// once, or, when it is on a trip, when the trip ends; the answer says which, and the deadline.
export default function OpenTicketForm({
  getToken,
  submit = openTicket,
  initialCode = '',
  onOpened,
  codeRef,
}: OpenTicketFormProps) {
  const { t, i18n } = useTranslation('maintenance');
  const headingId = useId();
  const codeId = useId();
  const codeHintId = useId();
  const descriptionId = useId();
  const errorId = useId();
  const [vehicleCode, setVehicleCode] = useState(initialCode);
  const [description, setDescription] = useState('');
  const [sending, setSending] = useState(false);
  const [opened, setOpened] = useState<OpenedTicket | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSending(true);
    setOpened(null);
    setFailure(null);
    try {
      const ticket = await submit(await getToken(), { vehicleCode, description });
      setOpened(ticket);
      setVehicleCode('');
      setDescription('');
      onOpened?.(ticket);
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
    <section aria-labelledby={headingId} className="open-ticket panel sq">
      <header className="open-ticket-head">
        <h2 id={headingId} className="panel-title">
          {t('openTicket.heading')}
        </h2>
        <p className="open-ticket-intro">{t('openTicket.intro')}</p>
      </header>
      <form className="open-ticket-form" onSubmit={(event) => void onSubmit(event)} noValidate>
        <div className="field">
          <label className="field-label" htmlFor={codeId}>
            {t('openTicket.vehicleCode')}
          </label>
          <input
            id={codeId}
            ref={codeRef}
            className="input input-code sq"
            name="vehicleCode"
            value={vehicleCode}
            onChange={(event) => setVehicleCode(event.target.value)}
            maxLength={32}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            aria-describedby={describedBy('vehicleCode', codeHintId)}
            aria-invalid={isInvalid('vehicleCode') ? 'true' : undefined}
          />
          <p id={codeHintId} className="field-hint">
            {t('openTicket.vehicleCodeHint')}
          </p>
        </div>
        <div className="field">
          <label className="field-label" htmlFor={descriptionId}>
            {t('openTicket.description')}
          </label>
          <textarea
            id={descriptionId}
            className="input sq"
            name="description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={500}
            rows={3}
            aria-describedby={describedBy('description')}
            aria-invalid={isInvalid('description') ? 'true' : undefined}
          />
          <p className="field-hint num counter" aria-hidden="true">
            {description.length}/500
          </p>
        </div>
        {failure && (
          <p id={errorId} role="alert" className="form-error">
            {failureMessage(failure)}
          </p>
        )}
        {opened && (
          <p role="status" className="form-success">
            {t('openTicket.opened', { code: opened.vehicleCode })}{' '}
            {opened.maintenancePending ? t('pending') : t('openTicket.inMaintenance')}{' '}
            {t('openTicket.deadline', { deadline: formatDateTime(opened.slaDueAt, i18n.language) })}
          </p>
        )}
        <Button type="submit" variant="primary" size="lg" disabled={sending} busy={sending}>
          {sending ? t('openTicket.sending') : t('openTicket.submit')}
        </Button>
      </form>
    </section>
  );
}
