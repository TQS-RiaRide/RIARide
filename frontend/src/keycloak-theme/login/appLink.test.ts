import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { appOrigin } from './appLink';
import { requirement } from '../../test/xray';

requirement('RIA-189');

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

  it('never trusts client data in the page address, which anyone can write into a link', () => {
    window.history.replaceState(
      null,
      '',
      `/login-actions/authenticate?client_data=${clientData('http://evil.test/')}`,
    );

    expect(appOrigin(undefined)).toBeNull();
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

  describe('when the browser refuses session storage', () => {
    const refusing = () => {
      throw new DOMException('Site data is blocked', 'SecurityError');
    };

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('still takes the origin from client data when reading the storage throws', () => {
      vi.spyOn(window, 'sessionStorage', 'get').mockImplementation(refusing);

      expect(appOrigin(action('http://localhost:5173/'))).toBe('http://localhost:5173');
      expect(appOrigin(undefined)).toBeNull();
    });

    it('still takes the origin from client data when the storage is full', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new DOMException('Quota exceeded', 'QuotaExceededError');
      });

      expect(appOrigin(action('http://localhost:5173/'))).toBe('http://localhost:5173');
    });

    it('is unknown when the stored value cannot be read', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(refusing);

      expect(appOrigin(undefined)).toBeNull();
    });
  });

  it('ignores a stored value that is not a web address', () => {
    window.sessionStorage.setItem('riaride.appOrigin', 'javascript:alert(1)');

    expect(appOrigin(undefined)).toBeNull();
  });
});
