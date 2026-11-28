import { kcSanitize } from 'keycloakify/lib/kcSanitize';
import { useTranslation } from 'react-i18next';
import { appOrigin } from '../appLink';
import type { PageProps } from './PageProps';

// A message from Keycloak (an email sent, an account updated), and where to go next.
export default function Info({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'info.ftl'>) {
  const { t } = useTranslation('login');
  const { messageHeader, message, requiredActions, skipLink, pageRedirectUri, actionUri, client } =
    kcContext;
  const actions = requiredActions?.map((action) => i18n.advancedMsgStr(`requiredAction.${action}`));
  const summary = actions?.length ? `${message.summary}: ${actions.join(', ')}` : message.summary;
  const backTo = pageRedirectUri ?? client.baseUrl ?? appOrigin(kcContext.url.loginAction);
  const next = actionUri ? { href: actionUri, label: t('continue') } : undefined;
  const back = backTo ? { href: backTo, label: t('backToApp') } : undefined;
  const link = skipLink ? undefined : (next ?? back);

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      displayMessage={false}
      headerNode={
        <span
          dangerouslySetInnerHTML={{
            __html: kcSanitize(messageHeader ? i18n.advancedMsgStr(messageHeader) : summary),
          }}
        />
      }
    >
      {messageHeader && (
        <p className="auth-lead" dangerouslySetInnerHTML={{ __html: kcSanitize(summary) }} />
      )}
      {link && (
        <a href={link.href} className="btn btn-primary btn-lg sq kc-button-link">
          {link.label}
        </a>
      )}
    </Template>
  );
}
