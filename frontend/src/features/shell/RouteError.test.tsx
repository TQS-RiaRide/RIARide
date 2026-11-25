import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import type { Trip } from '../../backend/types';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

// A trip without a status makes the trip screen throw while it renders.
function brokenTripBackend(): Backend {
  return {
    ...createDemoBackend({ latencyMs: 0 }),
    trip: () => Promise.resolve({ id: 'broken' } as Trip),
  };
}

describe('RouteError', () => {
  it('replaces a screen that fails to render and reloads the page on request', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const reload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload });
    await renderApp({ path: '/guest/trips/broken', user: 'guest', backend: brokenTripBackend() });

    expect(await screen.findByText('This screen stopped working')).toBeInTheDocument();
    expect(
      screen.getByText('Reload the page to try again. Nothing you saved was lost.'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Reload' }));

    expect(reload).toHaveBeenCalledOnce();
  });
});
