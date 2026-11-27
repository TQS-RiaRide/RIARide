import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import {
  LANGUAGE_NAMES,
  SUPPORTED_LANGUAGES,
  isSupportedLanguage,
  type Language,
} from '../i18n/config';
import './language-switcher.css';

// A labelled select: keyboard and screen-reader friendly, and it scales if
// more languages are added. Each option shows the language in its own name.
// `onChange` replaces the switch inside the app, for pages that change language elsewhere
// (Keycloak's login pages reload in the chosen language).
export default function LanguageSwitcher({
  tone = 'ink',
  compact = false,
  onChange,
}: {
  tone?: 'ink' | 'on-ink';
  compact?: boolean;
  onChange?: (language: Language) => void;
}) {
  const { t, i18n } = useTranslation();
  const id = useId();
  const current = isSupportedLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : 'en';

  return (
    <div className={`language-switcher language-${tone} ${compact ? 'language-compact' : ''}`}>
      <label htmlFor={id} className={compact ? 'visually-hidden' : 'language-label'}>
        {t('language.label')}
      </label>
      <select
        id={id}
        className="language-select sq"
        value={current}
        onChange={(event) => {
          const language = event.target.value;
          if (!isSupportedLanguage(language)) return;
          if (onChange) onChange(language);
          else void i18n.changeLanguage(language);
        }}
      >
        {SUPPORTED_LANGUAGES.map((language) => (
          <option key={language} value={language} lang={language}>
            {compact ? language.toUpperCase() : LANGUAGE_NAMES[language]}
          </option>
        ))}
      </select>
    </div>
  );
}
