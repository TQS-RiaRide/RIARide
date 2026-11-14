import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import App from './App';
import { fetchHealth } from './api';
import i18n from './i18n';

// Reset the language before each test, while no component is mounted.
// Doing it in afterEach would re-render a still-mounted App outside act.
beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchHealth', () => {
  it('returns up when the backend reports UP', async () => {
    const fakeFetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) });
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('up');
    expect(fakeFetch).toHaveBeenCalledWith('/api/v1/health');
  });

  it('returns down when the backend answers with an error', async () => {
    const fakeFetch = vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) });
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('down');
  });

  it('returns down when the backend is unreachable', async () => {
    const fakeFetch = vi.fn().mockRejectedValue(new Error('connection refused'));
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('down');
  });
});

describe('App', () => {
  it('shows the backend as online and switches language', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }));
    // The health check resolves after the first render, so flush it inside act
    await act(async () => {
      render(<App />);
    });

    expect(screen.getByText('Online')).toBeInTheDocument();

    // changeLanguage is asynchronous, so let React finish re-rendering inside act
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Português' }));
    });
    expect(screen.getByText('Disponível')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('pt');
  });
});
