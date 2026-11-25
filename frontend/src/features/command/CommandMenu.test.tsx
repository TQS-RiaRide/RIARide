import { act, fireEvent, screen, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import { IS_MAC } from '../../lib/hooks';
import { renderApp } from '../../testing/renderApp';

// The command menu (⌘K / Ctrl K): go anywhere, find a bike or a hub, or run the page's actions.

const originalScrollIntoView = Element.prototype.scrollIntoView;

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

beforeAll(() => {
  // jsdom does not lay out, so cmdk's scrolling of the selected item does nothing.
  Element.prototype.scrollIntoView = () => undefined;
});

afterAll(() => {
  Element.prototype.scrollIntoView = originalScrollIntoView;
});

beforeEach(async () => {
  await i18n.changeLanguage('en');
  vi.stubGlobal('ResizeObserver', NoResizeObserver);
});

afterEach(async () => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
  await i18n.changeLanguage('en');
});

async function openMenu() {
  await act(async () => {
    fireEvent.keyDown(window, { key: 'k', metaKey: IS_MAC, ctrlKey: !IS_MAC });
  });
  return screen.findByRole('dialog', { name: 'Command menu' });
}

async function search(menu: HTMLElement, text: string) {
  await act(async () => {
    fireEvent.change(within(menu).getByRole('combobox'), { target: { value: text } });
  });
}

async function choose(option: HTMLElement) {
  await act(async () => {
    fireEvent.click(option);
  });
}

describe('CommandMenu', () => {
  it('opens with the keyboard shortcut and lists places, page actions and preferences', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });

    const menu = await openMenu();

    expect(within(menu).getByRole('combobox')).toHaveAttribute(
      'placeholder',
      'Find a bike or a hub, or type a command…',
    );
    expect(
      within(menu).getByRole('option', { name: /^Show bikes with low battery/ }),
    ).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: /^Register bike/ })).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: 'Maintenance tickets' })).toBeInTheDocument();
    expect(await within(menu).findByRole('option', { name: /^EB-401/ })).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: /^Farol da Barra/ })).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: 'Switch to Português' })).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: 'Log out' })).toBeInTheDocument();
  });

  it('finds a bike by its code and shows it on the dashboard', async () => {
    await renderApp({ path: '/maintenance', user: 'operator' });
    await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' });
    const menu = await openMenu();
    await within(menu).findByRole('option', { name: /^EB-401/ });

    await search(menu, 'EB-401');

    expect(within(menu).queryByRole('option', { name: /^EB-101/ })).not.toBeInTheDocument();
    await choose(within(menu).getByRole('option', { name: /^EB-401/ }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Fleet' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'Code or hub' })).toHaveValue('EB-401');
    expect(await screen.findByRole('rowheader', { name: 'EB-401' })).toBeInTheDocument();
    expect(screen.queryByRole('rowheader', { name: 'EB-101' })).not.toBeInTheDocument();
  });

  it('filters the dashboard to a hub chosen in the menu', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });
    const menu = await openMenu();

    await choose(await within(menu).findByRole('option', { name: /^Farol da Barra/ }));

    expect(
      await screen.findByRole('button', { name: /Farol da Barra.*Show every hub/ }),
    ).toBeInTheDocument();
  });

  it('runs a page action and closes', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });
    const menu = await openMenu();
    await search(menu, 'low battery');

    await choose(within(menu).getByRole('option', { name: /^Show bikes with low battery/ }));

    expect(screen.queryByRole('dialog', { name: 'Command menu' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Low battery/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('opens the hub sheet from its page action', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });
    const menu = await openMenu();

    await choose(within(menu).getByRole('option', { name: /^Register hub/ }));

    expect(await screen.findByRole('dialog', { name: 'Register a hub' })).toBeInTheDocument();
  });

  it('goes to another workspace', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    await screen.findByRole('rowheader', { name: 'EB-101' });
    const menu = await openMenu();

    await choose(within(menu).getByRole('option', { name: 'Maintenance tickets' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Maintenance tickets' }),
    ).toBeInTheDocument();
  });

  it('says nothing matches a search with no result', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    const menu = await openMenu();

    await search(menu, 'zzzzqqq');

    expect(within(menu).getByText('No matches')).toBeInTheDocument();
  });

  it('switches the language', async () => {
    await renderApp({ path: '/fleet', user: 'operator' });
    const menu = await openMenu();

    await choose(within(menu).getByRole('option', { name: 'Switch to Português' }));

    expect(document.documentElement.lang).toBe('pt');
  });

  it('logs out', async () => {
    const { client } = await renderApp({ path: '/fleet', user: 'operator' });
    const menu = await openMenu();

    await choose(within(menu).getByRole('option', { name: 'Log out' }));

    expect(client.logout).toHaveBeenCalled();
  });

  it('offers no bikes or hubs to a role without the operator dashboard', async () => {
    await renderApp({ path: '/hotel', user: 'manager' });
    await screen.findByRole('heading', { level: 1, name: 'Hotel backoffice' });

    const menu = await openMenu();

    expect(within(menu).getByRole('option', { name: 'Hotel backoffice' })).toBeInTheDocument();
    expect(within(menu).queryByRole('option', { name: /^EB-/ })).not.toBeInTheDocument();
    expect(within(menu).queryByText('On this page')).not.toBeInTheDocument();
  });

  it('offers no account administration place and only the shortcuts hint', async () => {
    await renderApp({ path: '/hotel', user: 'admin' });
    const menu = await openMenu();

    expect(within(menu).getByRole('option', { name: 'Fleet' })).toBeInTheDocument();
    expect(
      within(menu).queryByRole('option', { name: 'Accounts and roles' }),
    ).not.toBeInTheDocument();
    expect(within(menu).getByText('shortcuts')).toBeInTheDocument();
    expect(within(menu).queryByText('move')).not.toBeInTheDocument();
  });
});
