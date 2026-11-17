import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import pt from './locales/pt.json';

// UI text lives only in the translation files under src/locales.
export const resources = {
  en: { translation: en },
  pt: { translation: pt },
} as const;

void i18n.use(initReactI18next).init({
  resources,
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  // Resources are bundled, so initialize synchronously: components render
  // translated text on the first render, with no update afterwards
  initImmediate: false,
});

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
});

export default i18n;
