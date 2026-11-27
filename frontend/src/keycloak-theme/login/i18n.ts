import { i18nBuilder } from 'keycloakify/login';
import type { ThemeName } from '../kc.gen';

// Keycloak's own messages, for the pages drawn by Keycloakify's default components. The pages
// RiaRide draws itself take their text from src/locales/<language>/login.json instead.
const built = i18nBuilder.withThemeName<ThemeName>().build();

export const useI18n = built.useI18n;
export type I18n = typeof built.ofTypeI18n;
