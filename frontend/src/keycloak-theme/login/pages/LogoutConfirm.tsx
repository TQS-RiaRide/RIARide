import { useTranslation } from 'react-i18next';
import { Button } from '../../../components/ui/ui';
import { appOrigin } from '../appLink';
import type { PageProps } from './PageProps';

export default function LogoutConfirm({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'logout-confirm.ftl'>) {
  const { t } = useTranslation('login');
  const { url, client, logoutConfirm } = kcContext;
  const backTo = client.baseUrl ?? appOrigin(kcContext.url.loginAction);

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      headerNode={t('logoutConfirm.title')}
      displayInfo={!logoutConfirm.skipLink && backTo !== null}
      infoNode={
        <a href={backTo ?? ''} className="kc-link">
          {t('backToApp')}
        </a>
      }
    >
      <p className="auth-lead">{t('logoutConfirm.lead')}</p>
      <form className="auth-form" action={url.logoutConfirmAction} method="post">
        <input type="hidden" name="session_code" value={logoutConfirm.code} />
        <Button type="submit" variant="primary" size="lg" name="confirmLogout" value="doYes">
          {t('logoutConfirm.submit')}
        </Button>
      </form>
    </Template>
  );
}
