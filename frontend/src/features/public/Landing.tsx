import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { PATHS } from '../../app/paths';
import { BASE_PASS_PRICES } from '../../backend/liveBackend';
import { Icon } from '../../components/ui/ui';
import LanguageSwitcher from '../../components/LanguageSwitcher';
import { formatMoney } from '../../lib/format';
import { useAuth } from '../auth/useAuth';
import { cheapestPrice } from '../guest/prices';
import Wordmark from '../shell/Wordmark';
import RiaMap from './RiaMap';
import './public.css';

export default function Landing() {
  const { t, i18n } = useTranslation('landing');
  const { t: tAuth } = useTranslation('auth');
  const { state, login } = useAuth();
  const cheapest = cheapestPrice(BASE_PASS_PRICES);

  return (
    <div className="landing">
      <header className="landing-top">
        <Wordmark />
        <div className="landing-top-actions">
          <LanguageSwitcher compact />
          <button type="button" className="btn btn-ghost btn-md sq" onClick={login}>
            {tAuth('logIn')}
          </button>
        </div>
      </header>

      <main className="landing-main">
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="landing-copy">
            <h1 id="landing-title" className="landing-title display">
              {t('title')}
            </h1>
            <p className="landing-lead">{t('lead')}</p>
            {state.status === 'error' && (
              <p role="alert" className="form-error">
                {tAuth('error')}
              </p>
            )}
            <div className="landing-ctas">
              <Link to={PATHS.signUp} viewTransition className="btn btn-primary btn-lg sq">
                {t('signUp')}
              </Link>
              <button type="button" className="btn btn-secondary btn-lg sq" onClick={login}>
                {t('haveAccount')}
              </button>
            </div>
            <p className="landing-fine">{t('fine')}</p>
          </div>
          <div className="landing-art" aria-hidden="true">
            <RiaMap />
          </div>
        </section>

        <ul className="landing-facts" aria-label={t('factsLabel')}>
          <li>
            <Icon name="bed" />
            <span>
              <strong>{t('facts.code.title')}</strong>
            </span>
          </li>
          <li>
            <Icon name="card" />
            <span>
              <strong>
                {t('facts.pass.title', {
                  price: cheapest
                    ? formatMoney(cheapest.priceCents, cheapest.currency, i18n.language)
                    : '',
                })}
              </strong>
              {t('facts.pass.body')}
            </span>
          </li>
          <li>
            <Icon name="hub" />
            <span>
              <strong>{t('facts.hubs.title')}</strong>
              {t('facts.hubs.body')}
            </span>
          </li>
        </ul>
      </main>

      <footer className="landing-foot">
        <span>{t('staff')}</span>
        <button type="button" className="landing-staff-link" onClick={login}>
          {t('staffLink')}
        </button>
      </footer>
    </div>
  );
}
