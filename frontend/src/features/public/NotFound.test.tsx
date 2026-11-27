import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

describe('NotFound', () => {
  it('explains an unknown address and links back to the start', async () => {
    await renderApp({ path: '/guest/nowhere/else' });

    expect(screen.getByText('There is no page here')).toBeInTheDocument();
    expect(screen.getByText('The link may be old, or the address has a typo.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Go to the start' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Vila Ria, at your own pace.' }),
    ).toBeInTheDocument();
  });
});
