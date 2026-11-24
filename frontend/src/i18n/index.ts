import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import {
  DEFAULT_LANGUAGE,
  DEFAULT_NAMESPACE,
  NAMESPACES,
  SUPPORTED_LANGUAGES,
  isSupportedLanguage,
} from './config';
import { detectLanguage, storeLanguage } from './language';
import { resources } from './resources';

// UI text lives only in the translation files under src/locales
// (QA Manual, section 3.1). See src/i18n/README.md to add a text.

const initialLanguage = detectLanguage();
document.documentElement.lang = initialLanguage;

void i18n.use(initReactI18next).init({
  resources,
  lng: initialLanguage,
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: [...SUPPORTED_LANGUAGES],
  ns: [...NAMESPACES],
  defaultNS: DEFAULT_NAMESPACE,
  interpolation: { escapeValue: false }, // React already escapes
  // Resources are bundled, so initialize synchronously: components render
  // translated text on the first render, with no update afterwards
  initAsync: false,
});

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
  if (isSupportedLanguage(lng)) storeLanguage(lng);
});

export default i18n;
