import { useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { useBackend } from '../../backend/BackendContext';
import { isProblem, NetworkError } from '../../backend/problem';
import { keys, useTickets } from '../../backend/queries';
import { useShortcuts } from '../../lib/hooks';
import { useAuth } from '../auth/useAuth';
import { usePageCommands } from '../command/commands';
import { TicketError, type OpenedTicket, type OpenTicketRequest } from './api';
import OpenTicketForm from './OpenTicketForm';
import TicketList from './TicketList';
import './maintenance.css';

// The roles that may open a maintenance ticket (RIA-92); the backend refuses the others.
const TICKET_OPENERS = ['OPERATOR', 'ADMIN'];

// Maps the backend adapter's errors to the form's error kinds, so the form keeps one contract.
function asTicketError(error: unknown): TicketError {
  if (error instanceof TicketError) return error;
  if (isProblem(error, 400))
    return new TicketError(
      'invalid',
      error.fields.filter(
        (f): f is 'vehicleCode' | 'description' => f === 'vehicleCode' || f === 'description',
      ),
    );
  if (isProblem(error, 401)) return new TicketError('sessionExpired');
  if (isProblem(error, 403)) return new TicketError('forbidden');
  if (isProblem(error, 404)) return new TicketError('notFound');
  if (isProblem(error, 409)) return new TicketError('retired');
  if (error instanceof NetworkError) return new TicketError('unexpected');
  return new TicketError('unexpected');
}

export default function TicketsPage() {
  const { t } = useTranslation('maintenance');
  const { t: tw } = useTranslation('workspaces');
  const backend = useBackend();
  const client = useQueryClient();
  const { state } = useAuth();
  const [params] = useSearchParams();
  const tickets = useTickets();
  const codeRef = useRef<HTMLInputElement>(null);
  const canOpen =
    state.status === 'authenticated' &&
    state.user.roles.some((role) => TICKET_OPENERS.includes(role));

  const submit = async (_token: string, request: OpenTicketRequest): Promise<OpenedTicket> => {
    try {
      return await backend.openTicket(request);
    } catch (error) {
      throw asTicketError(error);
    }
  };

  // The form says the ticket was opened in its own status line; this only refreshes the lists.
  const onOpened = () => {
    void client.invalidateQueries({ queryKey: keys.tickets });
    void client.invalidateQueries({ queryKey: keys.fleet });
  };

  useShortcuts(canOpen ? [{ keys: 't', run: () => codeRef.current?.focus() }] : [], canOpen);
  usePageCommands(
    'tickets',
    canOpen
      ? [
          {
            id: 'open-ticket',
            label: t('openTicket.heading'),
            shortcut: 't',
            run: () => codeRef.current?.focus(),
          },
        ]
      : [],
  );

  return (
    <div className="tickets-page">
      <header className="page-head">
        <div>
          <h1 className="page-title display">{tw('MAINTENANCE_TICKETS.title')}</h1>
        </div>
      </header>
      <div className={canOpen ? 'tickets-layout' : 'page-grid'}>
        {/* The h1 already names the list, so the panel has no visible heading of its own. */}
        <section aria-label={t('list.title')} className="panel sq tickets-panel">
          <TicketList tickets={tickets.data} loading={tickets.isPending} />
        </section>
        {canOpen && (
          <OpenTicketForm
            getToken={async () => ''}
            submit={submit}
            initialCode={params.get('code') ?? ''}
            onOpened={onOpened}
            codeRef={codeRef}
          />
        )}
      </div>
    </div>
  );
}
