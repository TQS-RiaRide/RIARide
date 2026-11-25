export interface OpenTicketRequest {
  vehicleCode: string;
  description: string;
}

export type BikeState = 'MAINTENANCE' | 'IN_USE';

export interface OpenedTicket {
  id: string;
  vehicleCode: string;
  status: string;
  description: string;
  openedAt: string;
  slaDueAt: string;
  /** In maintenance, or still in use and flagged until its trip ends (maintenancePending). */
  vehicleStatus: BikeState;
  maintenancePending: boolean;
}

export type TicketField = keyof OpenTicketRequest;

export type TicketErrorKind = 'invalid' | 'notFound' | 'retired' | 'forbidden' | 'unexpected';

/** Why a ticket could not be opened; for 'invalid', the form fields the backend refused. */
export class TicketError extends Error {
  readonly kind: TicketErrorKind;
  readonly fields: TicketField[];

  constructor(kind: TicketErrorKind, fields: TicketField[] = []) {
    super(`The ticket could not be opened (${kind})`);
    this.name = 'TicketError';
    this.kind = kind;
    this.fields = fields;
  }
}

const TICKETS = '/api/v1/tickets';
const FIELDS: readonly TicketField[] = ['vehicleCode', 'description'];
const BIKE_STATES: readonly BikeState[] = ['MAINTENANCE', 'IN_USE'];

function isTicketField(value: unknown): value is TicketField {
  return typeof value === 'string' && (FIELDS as readonly string[]).includes(value);
}

function isBikeState(value: unknown): value is BikeState {
  return typeof value === 'string' && (BIKE_STATES as readonly string[]).includes(value);
}

// The body comes from outside the app, so it is checked before use.
function parseTicket(body: unknown): OpenedTicket {
  if (typeof body !== 'object' || body === null) {
    throw new TicketError('unexpected');
  }
  const ticket = body as Record<string, unknown>;
  const texts = ['id', 'vehicleCode', 'status', 'description', 'openedAt', 'slaDueAt'] as const;
  if (
    !texts.every((key) => typeof ticket[key] === 'string') ||
    !isBikeState(ticket.vehicleStatus) ||
    typeof ticket.maintenancePending !== 'boolean'
  ) {
    throw new TicketError('unexpected');
  }
  return {
    id: ticket.id as string,
    vehicleCode: ticket.vehicleCode as string,
    status: ticket.status as string,
    description: ticket.description as string,
    openedAt: ticket.openedAt as string,
    slaDueAt: ticket.slaDueAt as string,
    vehicleStatus: ticket.vehicleStatus,
    maintenancePending: ticket.maintenancePending,
  };
}

// RFC 9457: a 400 names the refused fields in "invalid-params".
async function invalidFields(response: Response): Promise<TicketField[]> {
  try {
    const problem: unknown = await response.json();
    const params =
      typeof problem === 'object' && problem !== null
        ? (problem as Record<string, unknown>)['invalid-params']
        : undefined;
    if (!Array.isArray(params)) return [];
    return params
      .map((param: unknown) =>
        typeof param === 'object' && param !== null
          ? (param as Record<string, unknown>).name
          : undefined,
      )
      .filter(isTicketField);
  } catch {
    return [];
  }
}

async function errorFor(response: Response): Promise<TicketError> {
  switch (response.status) {
    case 400:
      return new TicketError('invalid', await invalidFields(response));
    case 401:
    case 403:
      return new TicketError('forbidden');
    case 404:
      return new TicketError('notFound');
    case 409:
      return new TicketError('retired');
    default:
      return new TicketError('unexpected');
  }
}

/** An operator opens a maintenance ticket for a faulty bike (RIA-92). */
export async function openTicket(
  token: string,
  request: OpenTicketRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<OpenedTicket> {
  let response: Response;
  try {
    response = await fetchImpl(TICKETS, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });
  } catch {
    throw new TicketError('unexpected');
  }
  if (!response.ok) {
    throw await errorFor(response);
  }
  try {
    return parseTicket(await response.json());
  } catch {
    throw new TicketError('unexpected');
  }
}
