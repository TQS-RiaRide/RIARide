import { useTranslation } from 'react-i18next';
import type { Pass } from '../../backend/types';
import { Button, Skeleton } from '../../components/ui/ui';
import { formatMoney } from '../../lib/format';

interface PassCardProps {
  pass: Pass | null | undefined;
  loading: boolean;
  onBuy: () => void;
  /** The cheapest pass price, already formatted, for the buy button. */
  fromPrice: string | undefined;
}

// The guest's pass at a glance: which one, whether it is active, and the deposit held (BR4, BR5).
export default function PassCard({ pass, loading, onBuy, fromPrice }: PassCardProps) {
  const { t, i18n } = useTranslation('guest');

  if (loading) {
    return (
      <section
        className="pass-card pass-card-loading sq"
        aria-busy="true"
        aria-label={t('pass.label')}
      >
        <Skeleton width={90} height={12} />
        <Skeleton width={180} height={24} />
        <Skeleton width="70%" height={12} />
      </section>
    );
  }

  if (!pass || pass.status === 'CLOSED') {
    return (
      <section className="pass-card pass-card-empty sq" aria-labelledby="pass-empty-title">
        <h2 id="pass-empty-title" className="pass-name display">
          {pass ? t('pass.status.CLOSED.title') : t('pass.noneTitle')}
        </h2>
        <p className="pass-rule">{t('pass.noneBody')}</p>
        <Button variant="inverse" size="lg" onClick={onBuy}>
          {fromPrice ? t('pass.buyFrom', { price: fromPrice }) : t('pass.buy')}
        </Button>
      </section>
    );
  }

  return (
    <section
      className="pass-card sq"
      aria-labelledby="pass-title"
      style={{ viewTransitionName: 'pass-card' }}
    >
      <svg className="pass-waves" aria-hidden="true" width="240" height="130" viewBox="0 0 240 130">
        <path d="M0 74C44 44 76 100 120 70S196 44 240 66" />
        <path d="M0 94C44 64 76 120 120 90S196 64 240 86" />
        <path d="M0 114C44 84 76 140 120 110S196 84 240 106" />
      </svg>
      <div className="pass-head">
        <p className="pass-kind">{t(`passTypes.${pass.passType}.name`)}</p>
        <h2 id="pass-title" className="pass-name display">
          {t(`pass.status.${pass.status}.title`)}
        </h2>
      </div>
      <p className="pass-rule">
        {t(`pass.status.${pass.status}.body`, { rule: t(`passTypes.${pass.passType}.rule`) })}
      </p>
      <div className="pass-deposit">
        <span>{t('pass.deposit')}</span>
        <span className="num">{formatMoney(pass.depositCents, pass.currency, i18n.language)}</span>
      </div>
    </section>
  );
}
