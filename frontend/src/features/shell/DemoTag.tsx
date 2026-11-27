import { useTranslation } from 'react-i18next';
import { useBackend } from '../../backend/BackendContext';

// A quiet tag next to the wordmark while the app runs on the in-memory demo backend.
export default function DemoTag({ tone = 'default' }: { tone?: 'default' | 'on-ink' }) {
  const { t } = useTranslation('shell');
  const { mode } = useBackend();
  if (mode !== 'demo') return null;
  return <span className={`demo-tag demo-tag-${tone}`}>{t('demo')}</span>;
}
