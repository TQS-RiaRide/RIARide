import { kcSanitize } from 'keycloakify/lib/kcSanitize';
import type { TemplateProps } from 'keycloakify/login/TemplateProps';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '../../components/LanguageSwitcher';
import Wordmark from '../../features/shell/Wordmark';
import { appOrigin, navigation } from './appLink';
import type { I18n } from './i18n';
import type { KcContext } from './KcContext';
import '../../features/public/public.css';
import './login.css';

const MESSAGE_CLASS = {
  error: 'form-error',
  warning: 'form-note',
  info: 'form-note',
  success: 'form-success',
} as const;

// Every Keycloak page in the frame of the app's own sign-up page: the wordmark, one card with
// the title, Keycloak's message for the page, and the form. The language menu reloads the page
// in the chosen language, since Keycloak renders its messages on the server.
export default function Template({
  kcContext,
  i18n,
  headerNode,
  children,
  displayMessage = true,
  displayInfo = false,
  infoNode,
  socialProvidersNode,
  documentTitle,
}: TemplateProps<KcContext, I18n>) {
  const { t } = useTranslation('login');
  const { message, locale, auth, url, isAppInitiatedAction } = kcContext;
  const origin = appOrigin(kcContext.url.loginAction);
  const title =
    documentTitle ??
    (typeof headerNode === 'string'
      ? t('documentTitle', { title: headerNode, realm: kcContext.realm.displayName })
      : kcContext.realm.displayName);

  useEffect(() => {
    document.title = title;
  }, [title]);

  const showMessage =
    displayMessage &&
    message !== undefined &&
    (message.type !== 'warning' || isAppInitiatedAction !== true);

  return (
    <div className="kc-page">
      {locale && locale.supported.length > 1 && (
        <div className="kc-top">
          <LanguageSwitcher
            compact
            onChange={(language) => {
              const target = locale.supported.find((l) => l.languageTag === language);
              if (target) navigation.assign(target.url);
            }}
          />
        </div>
      )}
      <main className="auth-page">
        {origin ? (
          <a href={origin} className="auth-back" aria-label={t('home')}>
            <Wordmark />
          </a>
        ) : (
          <span className="auth-back">
            <Wordmark />
          </span>
        )}
        <section className="auth-card sq" aria-labelledby="kc-page-title">
          {auth?.showUsername && !auth.showResetCredentials && (
            <p className="kc-user">
              <span className="kc-user-name">{auth.attemptedUsername}</span>
              <a href={url.loginRestartFlowUrl} className="kc-link">
                {t('restart')}
              </a>
            </p>
          )}
          <h1 id="kc-page-title" className="auth-title display">
            {headerNode}
          </h1>
          {showMessage && (
            <p
              role={message.type === 'error' ? 'alert' : 'status'}
              className={MESSAGE_CLASS[message.type]}
              dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
            />
          )}
          {children}
          {auth?.showTryAnotherWayLink && (
            <form action={url.loginAction} method="post">
              <input type="hidden" name="tryAnotherWay" value="on" />
              <button type="submit" className="kc-link kc-link-button">
                {i18n.msgStr('doTryAnotherWay')}
              </button>
            </form>
          )}
          {socialProvidersNode}
        </section>
        {displayInfo && infoNode && <div className="auth-alt">{infoNode}</div>}
      </main>
    </div>
  );
}
