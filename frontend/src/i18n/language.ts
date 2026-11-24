import { DEFAULT_LANGUAGE, isSupportedLanguage, type Language } from './config';

export const STORAGE_KEY = 'riaride.language';

// The parts of `window` that language detection reads, so tests can pass
// their own values.
export interface LanguageEnvironment {
  storage?: Pick<Storage, 'getItem' | 'setItem'>;
  browserLanguages?: readonly string[];
}

function browserEnvironment(): LanguageEnvironment {
  return {
    storage: safeStorage(),
    browserLanguages: typeof navigator === 'undefined' ? [] : navigator.languages,
  };
}

// localStorage can be missing or throw (private mode, blocked cookies).
function safeStorage(): Storage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.localStorage;
  } catch {
    return undefined;
  }
}

/**
 * The language to start with: the one the user chose before, otherwise the
 * first browser language we support ("pt-PT" and "pt-BR" count as "pt"),
 * otherwise English.
 */
export function detectLanguage(env: LanguageEnvironment = browserEnvironment()): Language {
  try {
    const stored = env.storage?.getItem(STORAGE_KEY);
    if (isSupportedLanguage(stored)) return stored;
  } catch {
    // Unreadable storage: fall back to the browser languages.
  }
  for (const tag of env.browserLanguages ?? []) {
    const base = tag.toLowerCase().split('-')[0];
    if (isSupportedLanguage(base)) return base;
  }
  return DEFAULT_LANGUAGE;
}

/** Remembers the user's choice for the next visit; failures are ignored. */
export function storeLanguage(
  language: Language,
  storage: LanguageEnvironment['storage'] = safeStorage(),
): void {
  try {
    storage?.setItem(STORAGE_KEY, language);
  } catch {
    // Not remembering the choice is acceptable.
  }
}
