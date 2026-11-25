import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { ProblemError } from '../../backend/problem';
import i18n from '../../i18n';
import { fakeAuthClient, renderApp } from '../../testing/renderApp';
import AuthProvider from '../auth/AuthProvider';
import type { DemoAuthClient } from '../auth/demoAuth';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function fill({ code = 'PRAIA-7K4Q', email = 'guest@example.test', password = 'long-enough' }) {
  fireEvent.change(screen.getByLabelText('Stay code'), { target: { value: code } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
}

async function submit() {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
  });
}

function refusing(error: unknown): Backend {
  return {
    ...createDemoBackend({ latencyMs: 0 }),
    signUpGuest: () => Promise.reject(error),
  };
}

describe('SignUpPage', () => {
  it('marks every empty field and names the first one to fix', async () => {
    await renderApp({ path: '/sign-up' });

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid email address.');
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Stay code')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address.',
    );
  });

  it.each([
    [{ email: 'not-an-email' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'ana@example' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'ana@.pt' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'ana@example.' }, 'Email', 'Enter a valid email address.'],
    [{ email: '@example.pt' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'ana silva@example.pt' }, 'Email', 'Enter a valid email address.'],
    [{ password: 'seven77' }, 'Password', 'The password needs at least 8 characters.'],
    [{ code: '   ' }, 'Stay code', 'Enter the stay code from the front desk.'],
  ])('refuses %o before sending anything', async (values, field, message) => {
    const signUpGuest = vi.fn();
    await renderApp({
      path: '/sign-up',
      backend: { ...createDemoBackend({ latencyMs: 0 }), signUpGuest },
    });
    fill(values);

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText(field)).toHaveAttribute('aria-invalid', 'true');
    expect(signUpGuest).not.toHaveBeenCalled();
  });

  it('accepts a password of exactly 8 characters and sends the code in capitals', async () => {
    const signUpGuest = vi.fn().mockResolvedValue(undefined);
    const { client } = await renderApp({
      path: '/sign-up',
      backend: { ...createDemoBackend({ latencyMs: 0 }), signUpGuest },
    });
    fill({ code: ' praia-7k4q ', email: ' guest@example.test ', password: '12345678' });

    await submit();

    expect(signUpGuest).toHaveBeenCalledWith({
      email: 'guest@example.test',
      password: '12345678',
      stayCode: 'PRAIA-7K4Q',
      locale: 'en',
    });
    expect(await screen.findByText('Welcome to RiaRide.')).toBeInTheDocument();
    expect(client.login).toHaveBeenCalledOnce();
  });

  it('signs a new demo guest in as the guest persona', async () => {
    const client: DemoAuthClient = { ...fakeAuthClient(false), signInAs: vi.fn() };
    await act(async () => {
      render(
        <AuthProvider client={client} loadUser={() => Promise.reject(new Error('None'))}>
          <App backend={createDemoBackend({ latencyMs: 0 })} initialPath="/sign-up" />
        </AuthProvider>,
      );
    });
    fill({});

    await submit();

    expect(client.signInAs).toHaveBeenCalledWith('guest');
    expect(client.login).not.toHaveBeenCalled();
  });

  // BR1: only a guest with an active stay can register.
  it('says the stay code is not valid when the backend does not know it', async () => {
    const { client } = await renderApp({ path: '/sign-up' });
    fill({ code: 'PRAIA-0000' });

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'This stay code is not valid. Check it with the front desk.',
    );
    expect(screen.getByLabelText('Stay code')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled();
    expect(client.login).not.toHaveBeenCalled();
  });

  // RIA-51: a code of a stay that has ended is refused with a message of its own.
  it('says the stay has ended when the code belongs to a past stay', async () => {
    await renderApp({
      path: '/sign-up',
      backend: refusing(
        new ProblemError(422, 'Stay already ended', 'The stay of this code has ended.', []),
      ),
    });
    fill({});

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'This stay has already ended, so its code no longer works. Ask the front desk.',
    );
  });

  it('asks to check the fields when Keycloak rejects the account details', async () => {
    await renderApp({
      path: '/sign-up',
      backend: refusing(new ProblemError(422, 'Account details rejected', null, [])),
    });
    fill({});

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent('Check the fields and try again.');
  });

  it('says an account already exists for a taken email', async () => {
    await renderApp({
      path: '/sign-up',
      backend: refusing(new ProblemError(409, 'Conflict', null, [])),
    });
    fill({});

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'There is already an account with this email. Log in instead.',
    );
  });

  it('marks the field a 400 names', async () => {
    await renderApp({
      path: '/sign-up',
      backend: refusing(
        new ProblemError(400, 'Bad request', null, [
          { name: 'password', reason: null },
          { name: 'other', reason: null },
        ]),
      ),
    });
    fill({});

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent(
      'The password needs at least 8 characters.',
    );
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Email')).not.toHaveAttribute('aria-invalid');
  });

  it('asks to check the fields when a 400 names none', async () => {
    await renderApp({
      path: '/sign-up',
      backend: refusing(new ProblemError(400, 'Bad request', null, [])),
    });
    fill({});

    await submit();

    expect(screen.getByRole('alert')).toHaveTextContent('Check the fields and try again.');
  });

  it('offers to log in instead', async () => {
    const { client } = await renderApp({ path: '/sign-up' });

    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(client.login).toHaveBeenCalledOnce();
  });

  it('sends a user who is already signed in to their workspace', async () => {
    await renderApp({ path: '/sign-up', user: 'guest' });

    expect(await screen.findByRole('navigation', { name: 'Main' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Stay code')).not.toBeInTheDocument();
  });
});
