import 'i18next';
import type { resources } from './resources';

// Type-checks translation keys against the English files: a key that does
// not exist fails `npm run build` instead of showing the raw key in the UI.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: (typeof resources)['en'];
  }
}
