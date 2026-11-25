# Translations

The UI is in English and Portuguese ([ADR 0005](../../../docs/architecture/adr/0005-react-frontend.md)), with react-i18next. No UI text is written in a component: every text comes from a translation file (QA Manual, section 3.1).

## Structure

```text
src/locales/
  en/
    common.json    texts shared by the whole app (title, language selector)
    health.json    one file per feature, named after its folder in src/features
  pt/
    common.json    the same files and keys, in Portuguese
    health.json
src/i18n/
  config.ts        languages, their names and the namespaces
  resources.ts     imports every translation file
  i18next.d.ts     types the keys: a key that does not exist fails the build
  language.ts      first language: saved choice, then the browser, then English
  index.ts         sets up i18next; saves the choice and updates <html lang>
```

## Add a text

1. Add the key to `src/locales/en/<namespace>.json` and `src/locales/pt/<namespace>.json`.
2. Use it in the component: `const { t } = useTranslation('<namespace>');` then `t('key')`. In the `common` namespace, `useTranslation()` is enough.

`npm test` fails if a key exists in one language and not in the other, or if a text is empty; `npm run build` fails if the code uses a key that does not exist.

## Add a feature namespace

1. Create `src/locales/en/<feature>.json` and `src/locales/pt/<feature>.json` (`login` belongs to `src/keycloak-theme`, Keycloak's login pages).
2. Add `'<feature>'` to `NAMESPACES` in `config.ts`.
3. Import both files in `resources.ts`.

## Add a language

1. Add its code to `SUPPORTED_LANGUAGES` and its own name to `LANGUAGE_NAMES` in `config.ts`.
2. Create `src/locales/<code>/` with every namespace file, and import them in `resources.ts`.
