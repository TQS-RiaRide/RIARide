// Errors from the backend are RFC 9457 problem details; a 400 names each refused field in
// "invalid-params". The UI turns a ProblemError into a translated message by its status.

export interface InvalidParam {
  name: string;
  reason: string | null;
}

export class ProblemError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string | null;
  readonly invalidParams: InvalidParam[];
  /** The problem's other members, such as the nearbyHubs of a full hub (RIA-55). */
  readonly properties: Readonly<Record<string, unknown>>;

  constructor(
    status: number,
    title: string,
    detail: string | null,
    invalidParams: InvalidParam[],
    properties: Readonly<Record<string, unknown>> = {},
  ) {
    super(`${status} ${title}${detail ? `: ${detail}` : ''}`);
    this.name = 'ProblemError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.invalidParams = invalidParams;
    this.properties = properties;
  }

  /** The names of the fields the backend refused. */
  get fields(): string[] {
    return this.invalidParams.map((param) => param.name);
  }
}

/** A request that never reached the backend (offline, DNS, CORS). */
export class NetworkError extends Error {
  constructor() {
    super('The backend could not be reached');
    this.name = 'NetworkError';
  }
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}

function parseInvalidParams(value: unknown): InvalidParam[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((param: unknown) => {
    if (typeof param !== 'object' || param === null) return [];
    const { name, reason } = param as Record<string, unknown>;
    return typeof name === 'string' ? [{ name, reason: text(reason) }] : [];
  });
}

/** Reads a failed response as a problem; a body that is not one still gives its status. */
export async function problemFrom(response: Response): Promise<ProblemError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  const record = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {};
  return new ProblemError(
    response.status,
    text(record.title) ?? text(response.statusText) ?? 'Error',
    text(record.detail),
    parseInvalidParams(record['invalid-params']),
    record,
  );
}

/** A hub with a free slot suggested instead of a full one, with its distance from it. */
export interface NearbyHub {
  hubId: string;
  name: string;
  freeSlots: number;
  distanceMeters: number;
}

function isNearbyHub(value: unknown): value is NearbyHub {
  if (typeof value !== 'object' || value === null) return false;
  const { hubId, name, freeSlots, distanceMeters } = value as Record<string, unknown>;
  return (
    typeof hubId === 'string' &&
    typeof name === 'string' &&
    typeof freeSlots === 'number' &&
    typeof distanceMeters === 'number'
  );
}

/** The hubs the backend suggests when the chosen hub is full (RIA-55); none for other errors. */
export function nearbyHubsOf(error: unknown): NearbyHub[] {
  if (!(error instanceof ProblemError)) return [];
  const suggested = error.properties.nearbyHubs;
  if (!Array.isArray(suggested)) return [];
  return suggested.filter(isNearbyHub).map(({ hubId, name, freeSlots, distanceMeters }) => ({
    hubId,
    name,
    freeSlots,
    distanceMeters,
  }));
}

export function isProblem(error: unknown, status?: number): error is ProblemError {
  return error instanceof ProblemError && (status === undefined || error.status === status);
}
