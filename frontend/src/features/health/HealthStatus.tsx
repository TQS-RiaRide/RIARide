import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchHealth, type HealthStatus as Status } from './api';
import './health.css';

// Says when the backend cannot be reached; says nothing while it is up or still being checked.
// Texts come from the "health" namespace (src/locales/*/health.json).
export default function HealthStatus() {
  const { t } = useTranslation('health');
  const [status, setStatus] = useState<Status | 'checking'>('checking');

  useEffect(() => {
    // Ignore a result that arrives after unmount (or after StrictMode's
    // extra mount and unmount in development)
    let ignore = false;
    void fetchHealth().then((result) => {
      if (!ignore) setStatus(result);
    });
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <p aria-live="polite" className="health">
      {status === 'down' && (
        <>
          <span className="health-dot health-down" aria-hidden="true" />
          <span>{t('label')}: </span>
          <strong data-testid="health" className="status status-down">
            {t('down')}
          </strong>
        </>
      )}
    </p>
  );
}
