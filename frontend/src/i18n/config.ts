// Languages and namespaces of the UI (guidelines: Portuguese and English).
// To add a language, add its code here, its name in LANGUAGE_NAMES and a
// folder under src/locales with every namespace file.

export const SUPPORTED_LANGUAGES = ['en', 'pt'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'en';

// Each language is shown in its own name, so a guest can find theirs.
export const LANGUAGE_NAMES: Record<Language, string> = {
  en: 'English',
  pt: 'Português',
};

// One namespace per area of the UI: shared texts in "common", and one per
// feature, named after its folder in src/features.
export const NAMESPACES = [
  'common',
  'health',
  'auth',
  'workspaces',
  'maintenance',
  'ui',
  'shell',
  'command',
  'landing',
  'login',
  'guest',
  'fleet',
  'hotel',
] as const;
export type Namespace = (typeof NAMESPACES)[number];

export const DEFAULT_NAMESPACE: Namespace = 'common';

export function isSupportedLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}
