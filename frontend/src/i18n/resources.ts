import enCommon from '../locales/en/common.json';
import enHealth from '../locales/en/health.json';
import ptCommon from '../locales/pt/common.json';
import ptHealth from '../locales/pt/health.json';
import type { Language, Namespace } from './config';

// Translations are bundled with the app: the UI is small, and bundling
// lets i18next initialize synchronously, with no loading state.
export const resources = {
  en: { common: enCommon, health: enHealth },
  pt: { common: ptCommon, health: ptHealth },
} as const satisfies Record<Language, Record<Namespace, object>>;
