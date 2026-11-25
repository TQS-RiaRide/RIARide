import { NetworkError, problemFrom } from './problem';

export type TokenSource = () => Promise<string>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT';
  body?: unknown;
  /** Public endpoints (guest sign-up) are called without a token. */
  anonymous?: boolean;
  headers?: Record<string, string>;
}

/**
 * Calls the API and returns the parsed JSON body, or throws a ProblemError (the backend
 * answered with an error) or a NetworkError (it could not be reached).
 */
export async function requestJson(
  getToken: TokenSource,
  path: string,
  options: RequestOptions = {},
  fetchImpl: typeof fetch = fetch,
): Promise<unknown> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (!options.anonymous) headers.Authorization = `Bearer ${await getToken()}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetchImpl(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new NetworkError();
  }
  if (!response.ok) throw await problemFrom(response);
  if (response.status === 204) return undefined;
  const text = await response.text();
  return text === '' ? undefined : (JSON.parse(text) as unknown);
}

/** A key for one purchase attempt: the same attempt retried reuses it, a new attempt gets a new one. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
