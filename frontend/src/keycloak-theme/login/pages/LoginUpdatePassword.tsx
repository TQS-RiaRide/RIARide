import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../../components/ui/ui';
import FieldError from './FieldError';
import type { PageProps } from './PageProps';

const ERROR_ID = 'kc-update-password-error';

export default function LoginUpdatePassword({
  kcContext,
  i18n,
  Template,
  doUseDefaultCss,
  classes,
}: PageProps<'login-update-password.ftl'>) {
  const { t } = useTranslation('login');
  const { url, messagesPerField, isAppInitiatedAction } = kcContext;
  const [sending, setSending] = useState(false);
  const refused = messagesPerField.existsError('password', 'password-confirm');
  const describedBy = refused ? ERROR_ID : undefined;

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      headerNode={t('updatePassword.title')}
      displayMessage={!refused}
    >
      <form
        className="auth-form"
        action={url.loginAction}
        method="post"
        onSubmit={() => setSending(true)}
      >
        <div className="field">
          <label className="field-label" htmlFor="password-new">
            {t('updatePassword.password')}
          </label>
          <input
            id="password-new"
            name="password-new"
            className="input sq"
            type="password"
            autoComplete="new-password"
            aria-invalid={messagesPerField.existsError('password') || undefined}
            aria-describedby={describedBy}
          />
        </div>
        <div className="field">
          <label className="field-label" htmlFor="password-confirm">
            {t('updatePassword.confirm')}
          </label>
          <input
            id="password-confirm"
            name="password-confirm"
            className="input sq"
            type="password"
            autoComplete="new-password"
            aria-invalid={messagesPerField.existsError('password-confirm') || undefined}
            aria-describedby={describedBy}
          />
        </div>
        <label className="check">
          <input type="checkbox" name="logout-sessions" value="on" defaultChecked />
          {t('updatePassword.logoutOthers')}
        </label>
        {refused && (
          <FieldError
            id={ERROR_ID}
            text={messagesPerField.getFirstError('password', 'password-confirm')}
          />
        )}
        <div className="kc-actions">
          <Button type="submit" variant="primary" size="lg" busy={sending} disabled={sending}>
            {t('updatePassword.submit')}
          </Button>
          {isAppInitiatedAction && (
            <Button type="submit" size="lg" name="cancel-aia" value="true">
              {t('updatePassword.cancel')}
            </Button>
          )}
        </div>
      </form>
    </Template>
  );
}
