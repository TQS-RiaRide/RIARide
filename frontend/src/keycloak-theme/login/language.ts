import type { i18n as I18next } from 'i18next';
import { isSupportedLanguage } from '../../i18n/config';

/**
 * Keycloak renders its messages in the language of the login session, so the app's texts on the
 * same page follow it. Called before the first render.
 */
export async function followKeycloakLanguage(
  i18n: I18next,
  languageTag: string | undefined,
): Promise<void> {
  if (isSupportedLanguage(languageTag) && i18n.language !== languageTag) {
    await i18n.changeLanguage(languageTag);
  }
}
