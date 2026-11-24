import { describe, expect, it } from 'vitest';
import { NAMESPACES, SUPPORTED_LANGUAGES } from './config';
import { resources } from './resources';

// Every language must translate every text: a key missing in Portuguese
// would silently fall back to English.

function flatten(tree: object, prefix = ''): Record<string, unknown> {
  return Object.entries(tree).reduce<Record<string, unknown>>((flat, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object') {
      Object.assign(flat, flatten(value as object, path));
    } else {
      flat[path] = value;
    }
    return flat;
  }, {});
}

describe.each(NAMESPACES)('translations in the "%s" namespace', (namespace) => {
  const english = flatten(resources.en[namespace]);

  it.each(SUPPORTED_LANGUAGES)('%s has exactly the same keys as English', (language) => {
    const keys = Object.keys(flatten(resources[language][namespace])).sort();

    expect(keys).toEqual(Object.keys(english).sort());
  });

  it.each(SUPPORTED_LANGUAGES)('%s has no empty texts', (language) => {
    const empty = Object.entries(flatten(resources[language][namespace]))
      .filter(([, text]) => typeof text !== 'string' || text.trim() === '')
      .map(([key]) => key);

    expect(empty).toEqual([]);
  });
});

it('every namespace has a file for every language', () => {
  for (const language of SUPPORTED_LANGUAGES) {
    expect(Object.keys(resources[language]).sort()).toEqual([...NAMESPACES].sort());
  }
});
