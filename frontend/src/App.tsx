import { useTranslation } from 'react-i18next';
import LanguageSwitcher from './components/LanguageSwitcher';
import HealthStatus from './features/health/HealthStatus';

export default function App() {
  const { t } = useTranslation();

  return (
    <main className="app">
      <header>
        <h1>{t('appName')}</h1>
        <LanguageSwitcher />
      </header>
      <p className="tagline">{t('tagline')}</p>
      <HealthStatus />
    </main>
  );
}
