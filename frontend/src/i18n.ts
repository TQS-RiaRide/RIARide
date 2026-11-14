import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// Translations for the walking skeleton. Move them to one JSON file per
// language (for example, src/locales/en.json) as the UI grows.
export const resources = {
  en: {
    translation: {
      tagline: 'E-bikes for guests of the Marés hotels in Vila Ria',
      backendStatus: 'Backend status',
      checking: 'Checking…',
      up: 'Online',
      down: 'Unreachable',
      switchLang: 'Português',
    },
  },
  pt: {
    translation: {
      tagline: 'Bicicletas elétricas para hóspedes dos hotéis Marés em Vila Ria',
      backendStatus: 'Estado do backend',
      checking: 'A verificar…',
      up: 'Disponível',
      down: 'Indisponível',
      switchLang: 'English',
    },
  },
} as const;

i18n.use(initReactI18next).init({
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
