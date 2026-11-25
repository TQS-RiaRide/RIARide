import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../../components/ui/ui';
import { appOrigin } from '../appLink';
import FieldError from './FieldError';
import type { PageProps } from './PageProps';

const ERROR_ID = 'kc-login-error';

export default function Login({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'login.ftl'>) {
  const { t } = useTranslation('login');
  const { realm, url, login, usernameHidden, messagesPerField } = kcContext;
  const [sending, setSending] = useState(false);
  const refused = messagesPerField.existsError('username', 'password');
  const origin = appOrigin(kcContext.url.loginAction);
  const usernameLabel =
    realm.loginWithEmailAllowed && realm.registrationEmailAsUsername
      ? t('logIn.email')
      : t('logIn.username');

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      headerNode={t('logIn.title')}
      displayMessage={!refused}
      displayInfo={origin !== null}
      infoNode={
        <>
          {t('logIn.newGuest')}{' '}
          <a href={`${origin ?? ''}/sign-up`} className="kc-link">
            {t('logIn.signUp')}
          </a>
        </>
      }
    >
      {realm.password && (
        <form
          className="auth-form"
          action={url.loginAction}
          method="post"
          onSubmit={() => setSending(true)}
        >
          {!usernameHidden && (
            <div className="field">
              <label className="field-label" htmlFor="username">
                {usernameLabel}
              </label>
              <input
                id="username"
                name="username"
                className="input sq"
                type="text"
                defaultValue={login.username ?? ''}
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                autoFocus
                aria-invalid={refused || undefined}
                aria-describedby={refused ? ERROR_ID : undefined}
              />
            </div>
          )}
          <div className="field">
            <div className="kc-label-row">
              <label className="field-label" htmlFor="password">
                {t('logIn.password')}
              </label>
              {realm.resetPasswordAllowed && (
                <a href={url.loginResetCredentialsUrl} className="kc-link">
                  {t('logIn.forgot')}
                </a>
              )}
            </div>
            <input
              id="password"
              name="password"
              className="input sq"
              type="password"
              autoComplete="current-password"
              aria-invalid={refused || undefined}
              aria-describedby={refused ? ERROR_ID : undefined}
            />
          </div>
          {realm.rememberMe && !usernameHidden && (
            <label className="check">
              <input type="checkbox" name="rememberMe" defaultChecked={login.rememberMe === 'on'} />
              {t('logIn.rememberMe')}
            </label>
          )}
          {refused && (
            <FieldError
              id={ERROR_ID}
              text={messagesPerField.getFirstError('username', 'password')}
            />
          )}
          <input
            type="hidden"
            name="credentialId"
            value={kcContext.auth.selectedCredential ?? ''}
          />
          <Button type="submit" variant="primary" size="lg" busy={sending} disabled={sending}>
            {sending ? t('logIn.sending') : t('logIn.submit')}
          </Button>
        </form>
      )}
    </Template>
  );
}
