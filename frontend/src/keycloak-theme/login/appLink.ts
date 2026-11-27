// Keycloak does not know where the app lives. Once it has checked the app's redirect URI against
// the client, it carries it in the `client_data` parameter of the form address it renders into
// the page (kcContext.url.loginAction; {"ru": "<redirect uri>"}, base64url). The origin is taken
// only from there: never from the page's own address, where a crafted link could put any
// client_data or a redirect_uri Keycloak refused. It is kept for the session so later pages, such
// as the error page, which has no form, still link back.

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

type OriginStorage = Pick<Storage, 'getItem' | 'setItem'>;

// Reading sessionStorage throws a SecurityError when the browser blocks site data for Keycloak's
// host. These pages render while it is read, so a throw would leave a blank page.
function sessionStore(): OriginStorage | undefined {
  try {
    return window.sessionStorage;
  } catch {
    return undefined;
  }
}

function remember(storage: OriginStorage | undefined, origin: string): void {
  try {
    storage?.setItem(STORAGE_KEY, origin);
  } catch {
    // A full or blocked storage only means later pages cannot link back.
  }
}

function remembered(storage: OriginStorage | undefined): string | null {
  try {
    return originOf(storage?.getItem(STORAGE_KEY));
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
  storage: OriginStorage | undefined = sessionStore(),
): string | null {
  const checked = redirectOrigin(clientDataOf(formAction));
  if (checked) {
    remember(storage, checked);
    return checked;
  }
  return remembered(storage);
}

/** Full-page navigation, in one place so tests can watch it. */
export const navigation = {
  assign(url: string): void {
    window.location.assign(url);
  },
};
