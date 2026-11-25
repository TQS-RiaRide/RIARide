import type { ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import { act, renderHook } from '@testing-library/react';
import i18n from '../i18n';
import { NetworkError, ProblemError } from '../backend/problem';
import { errorMessageKey, refusedFields, useErrorText } from './errors';

const problem = (status: number, title = 'Error', fields: string[] = []) =>
  new ProblemError(
    status,
    title,
    null,
    fields.map((name) => ({ name, reason: null })),
  );

describe('errorMessageKey', () => {
  it('prefers the screen key for the status and title', () => {
    const map = { '409:Vehicle not available': 'guest:taken', '409': 'guest:conflict' };

    expect(errorMessageKey(problem(409, 'Vehicle not available'), map)).toBe('guest:taken');
  });

  it('falls back to the screen key for the status when the title differs', () => {
    const map = { '409:Vehicle not available': 'guest:taken', '409': 'guest:conflict' };

    expect(errorMessageKey(problem(409, 'Something else'), map)).toBe('guest:conflict');
  });

  it.each([
    [401, 'ui:errors.sessionExpired'],
    [403, 'ui:errors.forbidden'],
    [404, 'ui:errors.notFound'],
    [503, 'ui:errors.unavailable'],
  ])('gives the general message for status %i', (status, key) => {
    expect(errorMessageKey(problem(status))).toBe(key);
  });

  it('lets the screen override a general status', () => {
    expect(errorMessageKey(problem(404), { '404': 'fleet:noVehicle' })).toBe('fleet:noVehicle');
  });

  it.each([400, 409, 500, 502])('gives the unexpected message for unmapped status %i', (status) => {
    expect(errorMessageKey(problem(status))).toBe('ui:errors.unexpected');
  });

  it('gives the network message when the backend cannot be reached', () => {
    expect(errorMessageKey(new NetworkError())).toBe('ui:errors.network');
  });

  it('lets the screen override the network message', () => {
    expect(errorMessageKey(new NetworkError(), { network: 'guest:offline' })).toBe('guest:offline');
  });

  it('gives the unexpected message for any other error', () => {
    expect(errorMessageKey(new Error('boom'))).toBe('ui:errors.unexpected');
    expect(errorMessageKey('not an error')).toBe('ui:errors.unexpected');
  });

  it('lets the screen override the unexpected message', () => {
    expect(errorMessageKey(undefined, { unexpected: 'guest:oops' })).toBe('guest:oops');
  });
});

describe('refusedFields', () => {
  it('lists the fields a problem refused', () => {
    expect(refusedFields(problem(400, 'Bad Request', ['email', 'stayCode']))).toEqual([
      'email',
      'stayCode',
    ]);
  });

  it('gives no fields for a problem without invalid params', () => {
    expect(refusedFields(problem(422))).toEqual([]);
  });

  it('gives no fields for an error that is not a problem', () => {
    expect(refusedFields(new NetworkError())).toEqual([]);
    expect(refusedFields(null)).toEqual([]);
  });
});

describe('useErrorText', () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
  );

  afterEach(async () => {
    await act(() => i18n.changeLanguage('en'));
  });

  it('translates a general problem in English', async () => {
    await act(() => i18n.changeLanguage('en'));
    const { result } = renderHook(() => useErrorText(), { wrapper });

    const text = result.current(problem(401));

    expect(text).toBe('Your session has ended. Log in again to continue.');
  });

  it('translates the network message in Portuguese', async () => {
    await act(() => i18n.changeLanguage('pt'));
    const { result } = renderHook(() => useErrorText(), { wrapper });

    const text = result.current(new NetworkError());

    expect(text).toBe(i18n.t('ui:errors.network', { lng: 'pt' }));
    expect(text).not.toBe(i18n.t('ui:errors.network', { lng: 'en' }));
  });

  it('translates the key a screen maps and passes the options', async () => {
    await act(() => i18n.changeLanguage('en'));
    const { result } = renderHook(() => useErrorText(), { wrapper });

    const text = result.current(problem(409), { '409': 'ui:battery.value' }, { pct: 15 });

    expect(text).toBe('15 %');
  });
});
