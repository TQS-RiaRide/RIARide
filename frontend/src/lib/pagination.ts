import { useState } from 'react';

// Long lists are cut into pages. The page number is clamped, so a page that no longer exists
// (after a filter, or an item removed) shows the last one instead of an empty list.

export interface Page<T> {
  items: T[];
  page: number;
  pageCount: number;
  /** 1-based position of the first item shown, 0 for an empty list. */
  from: number;
  to: number;
  total: number;
}

export function pageCountOf(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

export function clampPage(page: number, pageCount: number): number {
  const whole = Number.isFinite(page) ? Math.floor(page) : 1;
  return Math.min(Math.max(whole, 1), pageCount);
}

export function paginate<T>(items: readonly T[], page: number, pageSize: number): Page<T> {
  const total = items.length;
  const pageCount = pageCountOf(total, pageSize);
  const current = clampPage(page, pageCount);
  const start = (current - 1) * pageSize;
  const shown = items.slice(start, start + pageSize);
  return {
    items: shown,
    page: current,
    pageCount,
    from: total === 0 ? 0 : start + 1,
    to: start + shown.length,
    total,
  };
}

export type PageItem = number | 'gap';

const ALL_PAGES_UP_TO = 7;

/** The page buttons to draw: the ends, the current page and its neighbours, gaps between. */
export function pageItems(page: number, pageCount: number): PageItem[] {
  if (pageCount <= ALL_PAGES_UP_TO) {
    return Array.from({ length: pageCount }, (_, i) => i + 1);
  }
  const wanted = new Set([1, pageCount, page - 1, page, page + 1]);
  const pages = [...wanted].filter((p) => p >= 1 && p <= pageCount).sort((a, b) => a - b);
  return pages.flatMap((p, i): PageItem[] => (i > 0 && p - pages[i - 1] > 1 ? ['gap', p] : [p]));
}

/**
 * A page kept in component state, back on page one whenever `resetKey` changes (a new hub, a new
 * filter). Lists whose page belongs in the address read and write it there instead.
 */
export function usePagination<T>(
  items: readonly T[],
  pageSize: number,
  resetKey: string = '',
): Page<T> & { setPage: (page: number) => void } {
  const [state, setState] = useState({ page: 1, key: resetKey });
  const page = state.key === resetKey ? state.page : 1;
  if (state.key !== resetKey) setState({ page: 1, key: resetKey });
  return {
    ...paginate(items, page, pageSize),
    setPage: (next: number) => setState({ page: next, key: resetKey }),
  };
}
