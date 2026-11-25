import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../../components/ui/ui';
import FieldError from './FieldError';
import type { PageProps } from './PageProps';

const ERROR_ID = 'kc-reset-error';

export default function LoginResetPassword({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'login-reset-password.ftl'>) {
  const { t } = useTranslation('login');
  const { realm, url, auth, messagesPerField } = kcContext;
  const [sending, setSending] = useState(false);
  const refused = messagesPerField.existsError('username');

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      headerNode={t('resetPassword.title')}
      displayMessage={!refused}
      displayInfo
      infoNode={
        <a href={url.loginUrl} className="kc-link">
          {t('backToLogIn')}
        </a>
      }
    >
      <p className="auth-lead">{t('resetPassword.lead')}</p>
      <form
        className="auth-form"
        action={url.loginAction}
        method="post"
        onSubmit={() => setSending(true)}
      >
        <div className="field">
          <label className="field-label" htmlFor="username">
            {realm.loginWithEmailAllowed ? t('logIn.username') : t('logIn.email')}
          </label>
          <input
            id="username"
            name="username"
            className="input sq"
            type="text"
            defaultValue={auth.attemptedUsername ?? ''}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            autoFocus
            aria-invalid={refused || undefined}
            aria-describedby={refused ? ERROR_ID : undefined}
          />
        </div>
        {refused && <FieldError id={ERROR_ID} text={messagesPerField.get('username')} />}
        <Button type="submit" variant="primary" size="lg" busy={sending} disabled={sending}>
          {t('resetPassword.submit')}
        </Button>
      </form>
    </Template>
  );
}
