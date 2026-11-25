import { afterEach, describe, expect, it } from 'vitest';
import i18n from '../../i18n';
import { followKeycloakLanguage } from './language';

afterEach(async () => {
  await i18n.changeLanguage('en');
});

describe('followKeycloakLanguage', () => {
  it('switches the texts to the language of the login session', async () => {
    await followKeycloakLanguage(i18n, 'pt');

    expect(i18n.language).toBe('pt');
  });

  it('keeps the current language when Keycloak uses one the app does not have', async () => {
    await followKeycloakLanguage(i18n, 'de');
    await followKeycloakLanguage(i18n, undefined);

    expect(i18n.language).toBe('en');
  });
});
