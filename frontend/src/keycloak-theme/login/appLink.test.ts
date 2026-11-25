import { beforeEach, describe, expect, it } from 'vitest';
import { appOrigin } from './appLink';

beforeEach(() => {
  window.sessionStorage.clear();
  window.history.replaceState(null, '', '/');
});

/** Keycloak's client_data: the redirect URI it accepted, as base64url JSON. */
function clientData(redirectUri: string): string {
  return btoa(JSON.stringify({ ru: redirectUri, rt: 'code' }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

const action = (redirectUri: string) =>
  `http://kc.test/realms/riaride/login-actions/authenticate?client_id=riaride-spa&client_data=${clientData(redirectUri)}`;

describe('appOrigin', () => {
  it('takes the origin of the redirect URI Keycloak accepted and keeps it for the next pages', () => {
    expect(appOrigin(action('http://localhost:5173/fleet?hub=h-1'))).toBe('http://localhost:5173');

    expect(appOrigin(undefined)).toBe('http://localhost:5173');
  });

  it('reads the accepted redirect URI from the page address when the form has none', () => {
    window.history.replaceState(
      null,
      '',
      `/reset?client_data=${clientData('http://localhost:8081/')}`,
    );

    expect(appOrigin(undefined)).toBe('http://localhost:8081');
  });

  it('never trusts the redirect URI the browser asked for, which Keycloak may have refused', () => {
    window.history.replaceState(
      null,
      '',
      `/auth?redirect_uri=${encodeURIComponent('http://evil.test/')}`,
    );

    expect(appOrigin(undefined)).toBeNull();
  });

  it('is unknown before any login page', () => {
    expect(appOrigin(undefined)).toBeNull();
  });

  it('ignores client data that is not a web address or not Keycloak’s JSON', () => {
    expect(appOrigin(action('javascript:alert(1)'))).toBeNull();
    expect(appOrigin('http://kc.test/?client_data=not-base64!')).toBeNull();
    expect(appOrigin(`http://kc.test/?client_data=${btoa('"just a string"')}`)).toBeNull();
    expect(appOrigin('http://[bad')).toBeNull();
  });

  it('ignores a stored value that is not a web address', () => {
    window.sessionStorage.setItem('riaride.appOrigin', 'javascript:alert(1)');

    expect(appOrigin(undefined)).toBeNull();
  });
});
