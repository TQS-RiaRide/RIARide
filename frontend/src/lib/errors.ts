import { useTranslation } from 'react-i18next';
import { NetworkError, ProblemError } from '../backend/problem';

/**
 * Which message a screen shows for a failed call. A screen maps the problems it expects, by
 * "status" or "status:title" (the backend's problem title); everything else gets a general
 * message from the "ui" namespace, so no raw error ever reaches the user.
 */
export type ErrorMap = Partial<Record<string, string>>;

export function errorMessageKey(error: unknown, map: ErrorMap = {}): string {
  if (error instanceof ProblemError) {
    return (
      map[`${error.status}:${error.title}`] ??
      map[String(error.status)] ??
      GENERAL[error.status] ??
      'ui:errors.unexpected'
    );
  }
  if (error instanceof NetworkError) return map.network ?? 'ui:errors.network';
  return map.unexpected ?? 'ui:errors.unexpected';
}

const GENERAL: Record<number, string> = {
  401: 'ui:errors.sessionExpired',
  403: 'ui:errors.forbidden',
  404: 'ui:errors.notFound',
  503: 'ui:errors.unavailable',
};

/** The fields a 400 or 422 refused, so the form can mark them. */
export function refusedFields(error: unknown): string[] {
  return error instanceof ProblemError ? error.fields : [];
}

type LooseT = (key: string, options?: Record<string, unknown>) => string;

/**
 * Translates a failed call into the message errorMessageKey picks. The key is only known at run
 * time, so this is the one place where a translation key is not checked by the compiler; the keys
 * it can return are in the maps of each screen and in GENERAL above.
 */
export function useErrorText(): (
  error: unknown,
  map?: ErrorMap,
  options?: Record<string, unknown>,
) => string {
  const { t } = useTranslation();
  const loose = t as unknown as LooseT;
  return (error, map, options) => loose(errorMessageKey(error, map), options);
}
