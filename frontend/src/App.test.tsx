import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import App from './App';
import i18n from './i18n';

// Reset the language before each test, while no component is mounted.
// Doing it in afterEach would re-render a still-mounted App outside act.
beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('shows the backend as online and switches language both ways', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }),
    );
    // The health check resolves after the first render, so flush it inside act
    await act(async () => {
      render(<App />);
    });

    expect(screen.getByText('Online')).toBeInTheDocument();

    // changeLanguage is asynchronous: wait for i18next to emit languageChanged
    // and for React to re-render, all inside act
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Português' }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(screen.getByText('Disponível')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('pt');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'English' }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });
});
