import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { LANGUAGE_NAMES, SUPPORTED_LANGUAGES, isSupportedLanguage } from '../i18n/config';

// A labelled select: keyboard and screen-reader friendly, and it scales if
// more languages are added. Each option shows the language in its own name.
export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const id = useId();
  const current = isSupportedLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : 'en';

  return (
    <div className="language-switcher">
      <label htmlFor={id}>{t('language.label')}</label>
      <select
        id={id}
        value={current}
        onChange={(event) => {
          void i18n.changeLanguage(event.target.value);
        }}
      >
        {SUPPORTED_LANGUAGES.map((language) => (
          <option key={language} value={language} lang={language}>
            {LANGUAGE_NAMES[language]}
          </option>
        ))}
      </select>
    </div>
  );
}
