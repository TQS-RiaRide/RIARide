import DefaultPage from 'keycloakify/login/DefaultPage';
import type { ClassKey } from 'keycloakify/login/TemplateProps';
import { lazy, Suspense } from 'react';
import { useI18n } from './i18n';
import type { KcContext } from './KcContext';
import Template from './Template';

const UserProfileFormFields = lazy(() => import('keycloakify/login/UserProfileFormFields'));
const Login = lazy(() => import('./pages/Login'));
const LoginResetPassword = lazy(() => import('./pages/LoginResetPassword'));
const LoginUpdatePassword = lazy(() => import('./pages/LoginUpdatePassword'));
const Info = lazy(() => import('./pages/Info'));
const ErrorPage = lazy(() => import('./pages/Error'));
const LogoutConfirm = lazy(() => import('./pages/LogoutConfirm'));
const LoginPageExpired = lazy(() => import('./pages/LoginPageExpired'));

// Pages RiaRide does not draw itself (one-time codes, terms, profile updates) keep Keycloak's
// markup, dressed in the app's classes instead of PatternFly's.
export const CLASSES: Partial<Record<ClassKey, string>> = {
  kcFormClass: 'auth-form',
  kcFormGroupClass: 'field',
  kcLabelClass: 'field-label',
  kcInputClass: 'input sq',
  kcInputErrorMessageClass: 'form-error',
  kcInputHelperTextBeforeClass: 'field-hint',
  kcInputHelperTextAfterClass: 'field-hint',
  kcButtonClass: 'btn sq',
  kcButtonPrimaryClass: 'btn-primary',
  kcButtonDefaultClass: 'btn-secondary',
  kcButtonLargeClass: 'btn-lg',
  kcButtonBlockClass: 'kc-button-block',
  kcFormButtonsClass: 'kc-actions',
  kcFormOptionsClass: 'kc-options',
  kcCheckboxInputClass: 'kc-checkbox',
  kcAlertClass: 'form-note',
};

export default function KcPage({ kcContext }: { kcContext: KcContext }) {
  const { i18n } = useI18n({ kcContext });

  const common = { i18n, Template, doUseDefaultCss: false, classes: CLASSES } as const;

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case 'login.ftl':
            return <Login kcContext={kcContext} {...common} />;
          case 'login-reset-password.ftl':
            return <LoginResetPassword kcContext={kcContext} {...common} />;
          case 'login-update-password.ftl':
            return <LoginUpdatePassword kcContext={kcContext} {...common} />;
          case 'info.ftl':
            return <Info kcContext={kcContext} {...common} />;
          case 'error.ftl':
            return <ErrorPage kcContext={kcContext} {...common} />;
          case 'logout-confirm.ftl':
            return <LogoutConfirm kcContext={kcContext} {...common} />;
          case 'login-page-expired.ftl':
            return <LoginPageExpired kcContext={kcContext} {...common} />;
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                {...common}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword
              />
            );
        }
      })()}
    </Suspense>
  );
}
