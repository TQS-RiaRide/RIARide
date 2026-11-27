import { kcSanitize } from 'keycloakify/lib/kcSanitize';
import { useTranslation } from 'react-i18next';
import { appOrigin } from '../appLink';
import type { PageProps } from './PageProps';

export default function ErrorPage({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'error.ftl'>) {
  const { t } = useTranslation('login');
  const { message, client, skipLink } = kcContext;
  const backTo = client?.baseUrl ?? appOrigin(kcContext.url.loginAction);

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      displayMessage={false}
      headerNode={t('error.title')}
    >
      <p
        role="alert"
        className="form-error"
        dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
      />
      {!skipLink && backTo && (
        <a href={backTo} className="btn btn-secondary btn-lg sq kc-button-link">
          {t('backToApp')}
        </a>
      )}
    </Template>
  );
}
