// Keycloak does not know where the app lives. Once it has checked the app's redirect URI against
// the client, it carries it in the `client_data` parameter of its own form and page addresses
// ({"ru": "<redirect uri>"}, base64url). Its origin is taken from there, never from the
// redirect_uri the browser asked for, which may be the one Keycloak just refused, and is kept for
// the session so later pages still link back.

const STORAGE_KEY = 'riaride.appOrigin';

function originOf(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.origin : null;
  } catch {
    return null;
  }
}

function clientDataOf(address: string | undefined): string | null {
  if (!address) return null;
  try {
    return new URL(address, window.location.href).searchParams.get('client_data');
  } catch {
    return null;
  }
}

function redirectOrigin(clientData: string | null): string | null {
  if (!clientData) return null;
  try {
    const json = atob(clientData.replace(/-/g, '+').replace(/_/g, '/'));
    const data: unknown = JSON.parse(json);
    return typeof data === 'object' && data !== null && 'ru' in data ? originOf(data.ru) : null;
  } catch {
    return null;
  }
}

/**
 * Origin of the RiaRide app that sent the user to Keycloak, or null when it is unknown.
 * `formAction` is the address the page's form posts to (kcContext.url.loginAction).
 */
export function appOrigin(
  formAction: string | undefined,
  storage: Storage = window.sessionStorage,
): string | null {
  const checked =
    redirectOrigin(clientDataOf(formAction)) ?? redirectOrigin(clientDataOf(window.location.href));
  if (checked) {
    storage.setItem(STORAGE_KEY, checked);
    return checked;
  }
  return originOf(storage.getItem(STORAGE_KEY));
}

/** Full-page navigation, in one place so tests can watch it. */
export const navigation = {
  assign(url: string): void {
    window.location.assign(url);
  },
};
