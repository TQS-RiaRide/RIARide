import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router';
import { toast } from 'sonner';
import { PATHS } from '../../app/paths';
import { useBackend } from '../../backend/BackendContext';
import { Button } from '../../components/ui/ui';
import { isSupportedLanguage } from '../../i18n/config';
import { refusedFields, useErrorText } from '../../lib/errors';
import { useAuth } from '../auth/useAuth';
import Wordmark from '../shell/Wordmark';
import './public.css';

type Field = 'email' | 'password' | 'stayCode';

const MIN_PASSWORD = 8;

// A guest signs up with an email, a password and the stay code the front desk gave them (BR1).
// The code field accepts one-time-code autofill, so a code that arrives by SMS fills itself.
export default function SignUpPage() {
  const { t, i18n } = useTranslation('landing');
  const backend = useBackend();
  const errorText = useErrorText();
  const { state, login, signInAs } = useAuth();
  const ids = {
    email: useId(),
    password: useId(),
    passwordHint: useId(),
    code: useId(),
    codeHint: useId(),
    error: useId(),
  };
  const [values, setValues] = useState({ email: '', password: '', stayCode: '' });
  const [invalid, setInvalid] = useState<Field[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  if (state.status === 'authenticated') return <Navigate to={PATHS.home} replace />;

  const localCheck = (): Field[] => [
    ...(/^\S+@\S+\.\S+$/.test(values.email.trim()) ? [] : (['email'] as const)),
    ...(values.password.length >= MIN_PASSWORD ? [] : (['password'] as const)),
    ...(values.stayCode.trim() ? [] : (['stayCode'] as const)),
  ];

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = localCheck();
    setInvalid(fields);
    if (fields.length > 0) {
      setError(t(`signUpForm.errors.${fields[0]}`));
      return;
    }
    setSending(true);
    setError(null);
    try {
      await backend.signUpGuest({
        email: values.email.trim(),
        password: values.password,
        stayCode: values.stayCode.trim().toUpperCase(),
        locale: isSupportedLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : 'en',
      });
      toast.success(t('signUpForm.created'));
      if (signInAs) signInAs('guest');
      else login();
    } catch (failure) {
      const refused = refusedFields(failure).filter((f): f is Field =>
        ['email', 'password', 'stayCode'].includes(f),
      );
      setInvalid(refused);
      setError(
        errorText(failure, {
          '422:Invalid stay code': 'landing:signUpForm.errors.stayCodeUnknown',
          '409': 'landing:signUpForm.errors.taken',
          '422': 'landing:signUpForm.errors.stayCodeUnknown',
          '400': refused[0]
            ? `landing:signUpForm.errors.${refused[0]}`
            : 'landing:signUpForm.errors.invalid',
        }),
      );
    } finally {
      setSending(false);
    }
  };

  const set = (field: Field) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [field]: event.target.value }));
  const describedBy = (field: Field, hint?: string) =>
    [hint, invalid.includes(field) ? ids.error : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <main className="auth-page">
      <Link to={PATHS.home} className="auth-back" viewTransition>
        <Wordmark />
      </Link>
      <section className="auth-card sq" aria-labelledby="sign-up-title">
        <h1 id="sign-up-title" className="auth-title display">
          {t('signUpForm.title')}
        </h1>
        <form className="auth-form" onSubmit={(event) => void onSubmit(event)} noValidate>
          <div className="field">
            <label className="field-label" htmlFor={ids.code}>
              {t('signUpForm.stayCode')}
            </label>
            <input
              id={ids.code}
              className="input input-code sq"
              name="stayCode"
              value={values.stayCode}
              onChange={set('stayCode')}
              autoComplete="one-time-code"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={32}
              aria-invalid={invalid.includes('stayCode') || undefined}
              aria-describedby={describedBy('stayCode', ids.codeHint)}
            />
            <p id={ids.codeHint} className="field-hint">
              {t('signUpForm.stayCodeHint')}
            </p>
          </div>
          <div className="field">
            <label className="field-label" htmlFor={ids.email}>
              {t('signUpForm.email')}
            </label>
            <input
              id={ids.email}
              className="input sq"
              type="email"
              name="email"
              value={values.email}
              onChange={set('email')}
              autoComplete="email"
              inputMode="email"
              spellCheck={false}
              maxLength={254}
              aria-invalid={invalid.includes('email') || undefined}
              aria-describedby={describedBy('email')}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor={ids.password}>
              {t('signUpForm.password')}
            </label>
            <input
              id={ids.password}
              className="input sq"
              type="password"
              name="password"
              value={values.password}
              onChange={set('password')}
              autoComplete="new-password"
              minLength={MIN_PASSWORD}
              maxLength={128}
              aria-invalid={invalid.includes('password') || undefined}
              aria-describedby={describedBy('password', ids.passwordHint)}
            />
            <p id={ids.passwordHint} className="field-hint">
              {t('signUpForm.passwordHint', { count: MIN_PASSWORD })}
            </p>
          </div>
          {error && (
            <p id={ids.error} role="alert" className="form-error">
              {error}
            </p>
          )}
          <Button type="submit" variant="primary" size="lg" busy={sending} disabled={sending}>
            {sending ? t('signUpForm.sending') : t('signUpForm.submit')}
          </Button>
        </form>
      </section>
      <p className="auth-alt">
        {t('signUpForm.haveAccount')}{' '}
        <button type="button" className="landing-staff-link" onClick={login}>
          {t('signUpForm.logIn')}
        </button>
      </p>
    </main>
  );
}
