import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createGetKcContextMock } from 'keycloakify/login/KcContext';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import { navigation } from './appLink';
import KcPage from './KcPage';
import type { KcContextExtension, KcContextExtensionPerPage } from './KcContext';
import { followKeycloakLanguage } from './language';

// Keycloak's login pages, drawn with the app's components (RIA-120).

const { getKcContextMock } = createGetKcContextMock({
  kcContextExtension: { themeName: 'riaride', properties: {} } satisfies KcContextExtension,
  kcContextExtensionPerPage: {} satisfies KcContextExtensionPerPage,
  overrides: {
    realm: { displayName: 'RiaRide' },
    locale: {
      currentLanguageTag: 'en',
      supported: [
        { languageTag: 'en', label: 'English', url: 'http://kc.test/en' },
        { languageTag: 'pt', label: 'Português', url: 'http://kc.test/pt' },
      ],
    },
  },
});

type Mock = Parameters<typeof getKcContextMock>[0];

/** Keycloak's client_data: the redirect URI it accepted, as base64url JSON. */
function clientData(redirectUri: string): string {
  return btoa(JSON.stringify({ ru: redirectUri, rt: 'code' })).replace(/=+$/, '');
}

async function renderPage(params: Mock) {
  const kcContext = getKcContextMock(params);
  await followKeycloakLanguage(i18n, kcContext.locale?.currentLanguageTag);
  render(<KcPage kcContext={kcContext} />);
  return screen.findByRole('heading', { level: 1 });
}

beforeEach(async () => {
  await i18n.changeLanguage('en');
  window.sessionStorage.clear();
  window.history.replaceState(null, '', '/');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Login page', () => {
  it('asks for the account and password in the app’s own form', async () => {
    const heading = await renderPage({
      pageId: 'login.ftl',
      overrides: { url: { loginAction: 'http://kc.test/login-actions/authenticate' } },
    });

    expect(heading).toHaveTextContent('Log in');
    expect(document.title).toBe('Log in · RiaRide');
    const form = screen.getByRole('button', { name: 'Log in' }).closest('form');
    expect(form).toHaveAttribute('method', 'post');
    expect(form).toHaveAttribute('action', 'http://kc.test/login-actions/authenticate');
    expect(screen.getByLabelText('Email or username')).toHaveAttribute('name', 'username');
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toBeInTheDocument();
  });

  it('shows the refused login under the fields and marks them invalid', async () => {
    await renderPage({
      pageId: 'login.ftl',
      overrides: {
        message: { type: 'error', summary: 'Invalid username or password.' },
        messagesPerField: {
          existsError: (...fields: string[]) => fields.includes('username'),
          getFirstError: () => 'Invalid username or password.',
        },
      },
    });

    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid username or password.');
    expect(screen.getByLabelText('Email or username')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      'Invalid username or password.',
    );
  });

  it('shows Keycloak’s message for the page when no field is refused', async () => {
    await renderPage({
      pageId: 'login.ftl',
      overrides: { message: { type: 'success', summary: 'Your password was updated.' } },
    });

    expect(screen.getByRole('status')).toHaveTextContent('Your password was updated.');
  });

  it('says it is logging in while the form is sent', async () => {
    await renderPage({ pageId: 'login.ftl' });
    const form = screen.getByRole('button', { name: 'Log in' }).closest('form');

    act(() => {
      fireEvent.submit(form as HTMLFormElement);
    });

    expect(screen.getByRole('button', { name: 'Logging in…' })).toBeDisabled();
  });

  it('links back to the app and to guest sign-up once Keycloak accepted its address', async () => {
    await renderPage({
      pageId: 'login.ftl',
      overrides: {
        url: {
          loginAction: `http://kc.test/login-actions/authenticate?client_data=${clientData('http://localhost:5173/guest')}`,
        },
      },
    });

    expect(screen.getByRole('link', { name: 'RiaRide home' })).toHaveAttribute(
      'href',
      'http://localhost:5173',
    );
    expect(screen.getByRole('link', { name: 'Sign up with your stay code' })).toHaveAttribute(
      'href',
      'http://localhost:5173/sign-up',
    );
  });

  it('offers no sign-up link while the app is unknown', async () => {
    await renderPage({ pageId: 'login.ftl' });

    expect(screen.queryByRole('link', { name: 'Sign up with your stay code' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'RiaRide home' })).toBeNull();
  });

  it('asks for the email when the email is the username, and offers to stay logged in', async () => {
    await renderPage({
      pageId: 'login.ftl',
      overrides: {
        realm: { registrationEmailAsUsername: true, rememberMe: true, resetPasswordAllowed: false },
        login: { rememberMe: 'on' },
      },
    });

    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Keep me logged in')).toBeChecked();
    expect(screen.queryByRole('link', { name: 'Forgot password?' })).toBeNull();
  });

  it('names the account already chosen and lets the user pick another', async () => {
    await renderPage({
      pageId: 'login.ftl',
      overrides: {
        usernameHidden: true,
        auth: { showUsername: true, attemptedUsername: 'ana.silva' },
      },
    });

    expect(screen.getByText('ana.silva')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Use another account' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Email or username')).toBeNull();
  });

  it('reloads the page in the language the user chooses', async () => {
    const assign = vi.spyOn(navigation, 'assign').mockImplementation(() => undefined);
    await renderPage({ pageId: 'login.ftl' });

    fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'pt' } });

    expect(assign).toHaveBeenCalledWith('http://kc.test/pt');
  });

  it('follows the language of the login session', async () => {
    const heading = await renderPage({
      pageId: 'login.ftl',
      overrides: { locale: { currentLanguageTag: 'pt' } },
    });

    expect(heading).toHaveTextContent('Entrar');
    expect(screen.getByLabelText('Palavra-passe')).toBeInTheDocument();
  });
});

describe('Reset password page', () => {
  it('asks for the account and links back to log in', async () => {
    const heading = await renderPage({
      pageId: 'login-reset-password.ftl',
      overrides: {
        realm: { loginWithEmailAllowed: true },
        auth: { attemptedUsername: 'ana.silva' },
      },
    });

    expect(heading).toHaveTextContent('Reset your password');
    expect(screen.getByLabelText('Email or username')).toHaveValue('ana.silva');
    expect(screen.getByRole('button', { name: 'Send link' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to log in' })).toBeInTheDocument();
  });

  it('asks only for the email when it cannot log in with a username', async () => {
    await renderPage({
      pageId: 'login-reset-password.ftl',
      overrides: {
        realm: { loginWithEmailAllowed: false },
        messagesPerField: {
          existsError: () => true,
          get: () => 'Please specify username.',
        },
      },
    });

    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Please specify username.');
  });
});

describe('Update password page', () => {
  it('asks for the new password twice under Keycloak’s field names', async () => {
    const heading = await renderPage({ pageId: 'login-update-password.ftl' });

    expect(heading).toHaveTextContent('Choose a new password');
    expect(screen.getByLabelText('New password')).toHaveAttribute('name', 'password-new');
    expect(screen.getByLabelText('Repeat the password')).toHaveAttribute(
      'name',
      'password-confirm',
    );
    expect(screen.getByLabelText('Log out on my other devices')).toBeChecked();
    expect(screen.queryByRole('button', { name: 'Cancel' })).toBeNull();
  });

  it('shows the refused password and offers to cancel a change the app asked for', async () => {
    await renderPage({
      pageId: 'login-update-password.ftl',
      overrides: {
        isAppInitiatedAction: true,
        messagesPerField: {
          existsError: (...fields: string[]) => fields.includes('password-confirm'),
          getFirstError: () => 'Passwords don’t match.',
        },
      },
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords don’t match.');
    expect(screen.getByLabelText('Repeat the password')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveAttribute('name', 'cancel-aia');
  });
});

describe('Info page', () => {
  it('titles the page with Keycloak’s header and links to the next step', async () => {
    const heading = await renderPage({
      pageId: 'info.ftl',
      overrides: {
        messageHeader: 'Email sent',
        message: { type: 'info', summary: 'Perform the following actions' },
        requiredActions: ['UPDATE_PASSWORD'],
        actionUri: 'http://kc.test/action',
      },
    });

    expect(heading).toHaveTextContent('Email sent');
    expect(screen.getByText(/Perform the following actions: Update Password/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue' })).toHaveAttribute(
      'href',
      'http://kc.test/action',
    );
  });

  it('uses the message as title and links back to the app', async () => {
    const heading = await renderPage({
      pageId: 'info.ftl',
      overrides: {
        messageHeader: undefined,
        actionUri: undefined,
        message: { type: 'success', summary: 'Your account has been updated.' },
        client: { baseUrl: 'http://localhost:5173' },
      },
    });

    expect(heading).toHaveTextContent('Your account has been updated.');
    expect(screen.getByRole('link', { name: 'Back to RiaRide' })).toHaveAttribute(
      'href',
      'http://localhost:5173',
    );
  });

  it('offers no link when Keycloak says to skip it', async () => {
    await renderPage({
      pageId: 'info.ftl',
      overrides: { skipLink: true, client: { baseUrl: 'http://localhost:5173' } },
    });

    expect(screen.queryByRole('link', { name: /Continue|Back to RiaRide/ })).toBeNull();
  });
});

describe('Error page', () => {
  it('shows Keycloak’s error and links back to the app', async () => {
    const heading = await renderPage({
      pageId: 'error.ftl',
      overrides: {
        message: { type: 'error', summary: 'Invalid parameter: redirect_uri' },
        client: { baseUrl: 'http://localhost:5173' },
      },
    });

    expect(heading).toHaveTextContent('Something went wrong');
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid parameter: redirect_uri');
    expect(screen.getByRole('link', { name: 'Back to RiaRide' })).toBeInTheDocument();
  });

  it('never links to the redirect address Keycloak refused', async () => {
    window.history.replaceState(
      null,
      '',
      `/auth?redirect_uri=${encodeURIComponent('http://evil.test/')}`,
    );

    await renderPage({ pageId: 'error.ftl', overrides: { client: { baseUrl: undefined } } });

    expect(screen.queryByRole('link', { name: 'Back to RiaRide' })).toBeNull();
  });
});

describe('Logout confirmation page', () => {
  it('posts the session code with the confirmation', async () => {
    const heading = await renderPage({
      pageId: 'logout-confirm.ftl',
      overrides: {
        logoutConfirm: { code: 'session-code-1' },
        client: { baseUrl: 'http://localhost:5173' },
      },
    });

    expect(heading).toHaveTextContent('Log out?');
    const button = screen.getByRole('button', { name: 'Log out' });
    expect(button).toHaveAttribute('name', 'confirmLogout');
    const form = button.closest('form') as HTMLFormElement;
    expect(within(form).getByDisplayValue('session-code-1')).toHaveAttribute(
      'name',
      'session_code',
    );
    expect(screen.getByRole('link', { name: 'Back to RiaRide' })).toBeInTheDocument();
  });
});

describe('Expired page', () => {
  it('lets the user continue or start again', async () => {
    const heading = await renderPage({ pageId: 'login-page-expired.ftl' });

    expect(heading).toHaveTextContent('This page expired');
    expect(screen.getByRole('link', { name: 'Continue' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Start again' })).toBeInTheDocument();
  });
});

describe('Pages RiaRide does not draw itself', () => {
  it('keep Keycloak’s form in the app’s frame and field styles', async () => {
    await renderPage({ pageId: 'login-otp.ftl' });

    const code = await screen.findByLabelText(/One-time code/i);
    expect(code).toHaveClass('input');
    expect(code.closest('.auth-card')).not.toBeNull();
  });
});
