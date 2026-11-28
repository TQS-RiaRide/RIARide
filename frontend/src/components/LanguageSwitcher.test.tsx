import { beforeEach, describe, expect, it } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import LanguageSwitcher from './LanguageSwitcher';
import i18n from '../i18n';
import { STORAGE_KEY } from '../i18n/language';

beforeEach(async () => {
  await i18n.changeLanguage('en');
  window.localStorage.clear();
});

describe('LanguageSwitcher', () => {
  it('offers each language in its own name, with the current one selected', () => {
    render(<LanguageSwitcher />);

    const select = screen.getByLabelText('Language');
    expect(select).toHaveValue('en');
    expect(screen.getByRole('option', { name: 'English' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Português' })).toBeInTheDocument();
  });

  it('switches the UI language, the page language and remembers the choice', async () => {
    render(<LanguageSwitcher />);

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'pt' } });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(screen.getByLabelText('Língua')).toHaveValue('pt');
    expect(document.documentElement.lang).toBe('pt');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('pt');
  });
});

describe('LanguageSwitcher with its own change handler', () => {
  it('hands the chosen language over and leaves the UI language alone', () => {
    const chosen: string[] = [];
    render(<LanguageSwitcher compact onChange={(language) => chosen.push(language)} />);

    fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'pt' } });

    expect(chosen).toEqual(['pt']);
    expect(i18n.language).toBe('en');
  });
});
