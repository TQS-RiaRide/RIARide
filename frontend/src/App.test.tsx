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
  window.localStorage.clear();
});

async function renderApp(health: { ok: boolean; status?: string }) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: health.ok, json: async () => ({ status: health.status }) }),
  );
  // The health check resolves after the first render, so flush it inside act
  await act(async () => {
    render(<App />);
  });
}

async function chooseLanguage(label: string, language: string) {
  // changeLanguage is asynchronous: wait for i18next to emit languageChanged
  // and for React to re-render, all inside act
  await act(async () => {
    fireEvent.change(screen.getByLabelText(label), { target: { value: language } });
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe('App', () => {
  it('shows the backend as online and switches language both ways', async () => {
    await renderApp({ ok: true, status: 'UP' });

    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(screen.getByText('Backend status:')).toBeInTheDocument();

    await chooseLanguage('Language', 'pt');
    expect(screen.getByText('Disponível')).toBeInTheDocument();
    expect(screen.getByText('Estado do backend:')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('pt');

    await chooseLanguage('Língua', 'en');
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('shows the backend as unreachable when the health check fails', async () => {
    await renderApp({ ok: false });

    expect(screen.getByText('Unreachable')).toBeInTheDocument();
  });
});
