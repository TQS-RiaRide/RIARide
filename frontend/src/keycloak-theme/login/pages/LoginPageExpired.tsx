import { useTranslation } from 'react-i18next';
import type { PageProps } from './PageProps';

export default function LoginPageExpired({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'login-page-expired.ftl'>) {
  const { t } = useTranslation('login');
  const { url } = kcContext;

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      headerNode={t('expired.title')}
    >
      <p className="auth-lead">{t('expired.lead')}</p>
      <div className="kc-actions">
        <a href={url.loginAction} className="btn btn-primary btn-lg sq kc-button-link">
          {t('continue')}
        </a>
        <a href={url.loginRestartFlowUrl} className="btn btn-secondary btn-lg sq kc-button-link">
          {t('expired.restart')}
        </a>
      </div>
    </Template>
  );
}
