import { act, fireEvent, screen, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// The staff frame: the workspace menu, "g" shortcuts, "?" for the shortcuts list, and the account.

class NoResizeObserver {
  observe() {
    return undefined;
  }
  unobserve() {
    return undefined;
  }
  disconnect() {
    return undefined;
  }
}

const originalScrollIntoView = Element.prototype.scrollIntoView;

beforeAll(() => {
  // jsdom does not lay out, so the command menu's scrolling of the selected item does nothing.
  Element.prototype.scrollIntoView = () => undefined;
});

afterAll(() => {
  Element.prototype.scrollIntoView = originalScrollIntoView;
});

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.stubGlobal('ResizeObserver', NoResizeObserver);
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

async function press(...keys: string[]) {
  for (const key of keys) {
    await act(async () => {
      fireEvent.keyDown(window, { key });
    });
  }
}

describe('StaffShell', () => {
  it('lists only the workspaces of the role and names the account', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });

    const menu = screen.getByRole('navigation', { name: 'Workspaces' });
    expect(within(menu).getAllByRole('link')).toHaveLength(2);
    expect(within(menu).getByRole('link', { name: 'Fleet' })).toBeInTheDocument();
    expect(within(menu).getByRole('link', { name: /Maintenance tickets/ })).toBeInTheDocument();
    expect(screen.getByText('Rui Matos')).toBeInTheDocument();
    expect(screen.getByText('RM')).toBeInTheDocument();
    expect(screen.getByText('Operator')).toBeInTheDocument();
  });

  it('goes to the maintenance tickets with g m and back to the fleet with g f', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('heading', { level: 1, name: 'Fleet' });

    await press('g', 'm');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();

    await press('g', 'f');
    expect(await screen.findByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument();
  });

  it('ignores a go-to shortcut for a workspace outside the role', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('heading', { level: 1, name: 'Fleet' });

    await press('g', 'h');

    expect(screen.getByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument();
  });

  it('ignores the shortcuts while typing in a field', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    const search = await screen.findByRole('searchbox', { name: 'Code or hub' });

    await act(async () => {
      fireEvent.keyDown(search, { key: 'g' });
      fireEvent.keyDown(search, { key: 'm' });
    });

    expect(screen.getByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument();
  });

  it('lists every shortcut of the page with ?', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    await press('?');

    const help = await screen.findByRole('dialog', { name: 'Keyboard shortcuts' });
    expect(within(help).getByText('Open the command menu')).toBeInTheDocument();
    expect(within(help).getByText('Go to Fleet')).toBeInTheDocument();
    expect(within(help).getByText('Go to Maintenance tickets')).toBeInTheDocument();
    expect(within(help).getByText('On this page')).toBeInTheDocument();
    expect(within(help).getByText('Register bike')).toBeInTheDocument();
    expect(within(help).queryByText('Show bikes with low battery')).not.toBeInTheDocument();
  });

  it('lists no page shortcuts with ? on a page without any', async () => {
    await renderApp({ path: '/admin', user: 'admin' });

    await press('?');

    const help = await screen.findByRole('dialog', { name: 'Keyboard shortcuts' });
    expect(within(help).queryByText('On this page')).not.toBeInTheDocument();
    expect(within(help).getByText('Go to Hotel backoffice')).toBeInTheDocument();
    expect(within(help).queryByText('Go to Accounts and roles')).not.toBeInTheDocument();
  });

  it('keeps account administration out of the menu while /admin still opens', async () => {
    await renderApp({ path: '/admin', user: 'admin' });

    const menu = screen.getByRole('navigation', { name: 'Workspaces' });
    expect(
      within(menu).queryByRole('link', { name: 'Accounts and roles' }),
    ).not.toBeInTheDocument();
    expect(within(menu).getByRole('link', { name: 'Hotel backoffice' })).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Accounts and roles' }),
    ).toBeInTheDocument();
  });

  it('draws no keyboard chips in the side menu', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });

    const side = screen.getByRole('navigation', { name: 'Workspaces' }).closest('aside');
    expect(side?.querySelector('kbd')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Keyboard shortcuts' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Search or run a command' })).toHaveAttribute(
      'aria-keyshortcuts',
    );
  });

  it('tags the demo backend in the side menu', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });

    expect(screen.getByText('Demo')).toBeInTheDocument();
  });

  it('shows no demo tag on the live backend', async () => {
    const backend: Backend = { ...createDemoBackend({ latencyMs: 0 }), mode: 'live' };
    await renderApp({ path: '/fleet', user: 'operator', backend });

    expect(screen.queryByText('Demo')).not.toBeInTheDocument();
  });

  it('opens the command menu from the side menu search', async () => {
    await renderApp({ path: '/admin', user: 'admin' });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Search or run a command/ }));
    });

    expect(await screen.findByRole('dialog', { name: 'Command menu' })).toBeInTheDocument();
  });

  it('logs out from the account button', async () => {
    const { client } = await renderApp({ path: '/fleet', user: 'technician' });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Log out' }));
    });

    expect(client.logout).toHaveBeenCalled();
  });
});
