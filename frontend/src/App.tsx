import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchHealth, type HealthStatus } from './api';

export default function App() {
  const { t, i18n } = useTranslation();
  const [health, setHealth] = useState<HealthStatus | 'checking'>('checking');

  useEffect(() => {
    fetchHealth().then(setHealth);
  }, []);

  const toggleLanguage = () => i18n.changeLanguage(i18n.language === 'en' ? 'pt' : 'en');

  return (
    <main className="app">
      <header>
        <h1>RiaRide</h1>
        <button type="button" onClick={toggleLanguage}>
          {t('switchLang')}
        </button>
      </header>
      <p className="tagline">{t('tagline')}</p>
      <section aria-live="polite">
        <span>{t('backendStatus')}: </span>
        <strong data-testid="health" className={`status status-${health}`}>
          {t(health)}
        </strong>
      </section>
    </main>
  );
}
