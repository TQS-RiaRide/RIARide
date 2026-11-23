import { describe, expect, it, vi } from 'vitest';
import { STORAGE_KEY, detectLanguage, storeLanguage } from './language';

function storageWith(value: string | null) {
  return { getItem: vi.fn().mockReturnValue(value), setItem: vi.fn() };
}

describe('detectLanguage', () => {
  it('uses the language the user chose before', () => {
    const storage = storageWith('pt');

    expect(detectLanguage({ storage, browserLanguages: ['en-GB'] })).toBe('pt');
    expect(storage.getItem).toHaveBeenCalledWith(STORAGE_KEY);
  });

  it('ignores a stored value that is not a supported language', () => {
    expect(detectLanguage({ storage: storageWith('fr'), browserLanguages: ['pt-PT'] })).toBe('pt');
  });

  it.each([
    [['pt-BR', 'en'], 'pt'],
    [['PT'], 'pt'],
    [['fr-FR', 'en-US'], 'en'],
  ])('uses the first supported browser language from %j', (browserLanguages, expected) => {
    expect(detectLanguage({ storage: storageWith(null), browserLanguages })).toBe(expected);
  });

  it('falls back to English when no browser language is supported', () => {
    expect(detectLanguage({ storage: storageWith(null), browserLanguages: ['de', 'fr'] })).toBe(
      'en',
    );
  });

  it('falls back to English with no storage and no browser languages', () => {
    expect(detectLanguage({})).toBe('en');
  });

  it('still uses the browser languages when storage throws', () => {
    const storage = {
      getItem: vi.fn(() => {
        throw new Error('blocked');
      }),
      setItem: vi.fn(),
    };

    expect(detectLanguage({ storage, browserLanguages: ['pt'] })).toBe('pt');
  });

  it('reads the real browser environment by default', () => {
    window.localStorage.setItem(STORAGE_KEY, 'pt');

    expect(detectLanguage()).toBe('pt');

    window.localStorage.removeItem(STORAGE_KEY);
  });
});

describe('storeLanguage', () => {
  it('remembers the chosen language', () => {
    const storage = storageWith(null);

    storeLanguage('pt', storage);

    expect(storage.setItem).toHaveBeenCalledWith(STORAGE_KEY, 'pt');
  });

  it('ignores a storage that throws', () => {
    const storage = {
      getItem: vi.fn(),
      setItem: vi.fn(() => {
        throw new Error('quota exceeded');
      }),
    };

    expect(() => storeLanguage('en', storage)).not.toThrow();
  });
});
