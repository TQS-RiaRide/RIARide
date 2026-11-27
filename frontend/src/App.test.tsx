import { act, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from './i18n';
import { requirement } from './test/xray';
import { renderApp } from './testing/renderApp';

// Reset the language before each test, while no component is mounted.
beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

async function chooseLanguage(label: string, language: string) {
  await act(async () => {
    fireEvent.change(screen.getAllByLabelText(label)[0], { target: { value: language } });
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe('App', () => {
  it('shows a visitor the landing page and switches language both ways', async () => {
    await renderApp();

    expect(
      screen.getByRole('heading', { level: 1, name: 'Vila Ria, at your own pace.' }),
    ).toBeInTheDocument();

    await chooseLanguage('Language', 'pt');
    expect(document.documentElement.lang).toBe('pt');
    expect(screen.queryByText('Vila Ria, at your own pace.')).not.toBeInTheDocument();

    await chooseLanguage('Língua', 'en');
    expect(document.documentElement.lang).toBe('en');
    expect(screen.getByText('Vila Ria, at your own pace.')).toBeInTheDocument();
  });

  it('says nothing about the backend in the staff menu while it is online', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('heading', { level: 1, name: 'Fleet' });

    expect(screen.queryByTestId('health')).not.toBeInTheDocument();
    expect(screen.queryByText(/Backend status/)).not.toBeInTheDocument();
  });

  it('shows the backend as unreachable when the health check fails', async () => {
    await renderApp({ path: '/fleet', user: 'operator', health: { ok: false } });

    expect(await screen.findByTestId('health')).toHaveTextContent('Unreachable');
  });

  // Login and roles (RIA-86); the operator's dashboard test is its acceptance criterion.
  describe('login and roles', () => {
    requirement('RIA-86');

    it('sends a visitor who opens a staff page back to the landing page', async () => {
      await renderApp({ path: '/fleet' });

      expect(screen.getByText('Vila Ria, at your own pace.')).toBeInTheDocument();
      expect(screen.queryByRole('navigation', { name: 'Workspaces' })).not.toBeInTheDocument();
    });

    it('takes a logged-in operator to the operator dashboard and not the hotel backoffice', async () => {
      await renderApp({ user: 'operator' });

      expect(await screen.findByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument();
      const menu = screen.getByRole('navigation', { name: 'Workspaces' });
      expect(menu).toHaveTextContent('Fleet');
      expect(menu).not.toHaveTextContent('Hotel backoffice');
    });

    it('tells an operator who opens the hotel backoffice that it is not part of the role', async () => {
      await renderApp({ path: '/hotel', user: 'operator' });

      expect(screen.getByText('This area is not part of your role')).toBeInTheDocument();
    });

    // RIA-92: operators and admins open tickets from the maintenance tickets workspace.
  });

  it('lets an operator open a ticket from the maintenance tickets workspace', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });

    await act(async () => {
      fireEvent.click(screen.getByRole('link', { name: /Maintenance tickets/ }));
    });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Open a ticket' })).toBeInTheDocument();
    expect(screen.getByLabelText('Bike code')).toBeInTheDocument();
  });

  // A technician sees the tickets workspace too, but only operators and admins open tickets.
  it('does not offer a technician the form to open a ticket', async () => {
    await renderApp({ user: 'technician' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Open a ticket' })).not.toBeInTheDocument();
  });

  it('takes a guest to the guest home with the bottom tabs', async () => {
    await renderApp({ user: 'guest' });

    expect(await screen.findByRole('navigation', { name: 'Main' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Trips/ })).toBeInTheDocument();
  });

  it('tags the demo backend in the guest app', async () => {
    await renderApp({ user: 'guest' });

    expect(await screen.findByRole('navigation', { name: 'Main' })).toBeInTheDocument();
    expect(screen.getByText('Demo', { selector: '.demo-tag' })).toBeInTheDocument();
  });

  it('shows a not found page for an unknown address', async () => {
    await renderApp({ path: '/nowhere' });

    expect(screen.getByText('There is no page here')).toBeInTheDocument();
  });
});
