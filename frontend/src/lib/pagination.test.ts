import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { pageItems, paginate, usePagination } from './pagination';

const numbers = (count: number) => Array.from({ length: count }, (_, i) => i + 1);

describe('paginate', () => {
  it('returns the first page of a long list', () => {
    const page = paginate(numbers(29), 1, 10);

    expect(page).toEqual({
      items: numbers(10),
      page: 1,
      pageCount: 3,
      from: 1,
      to: 10,
      total: 29,
    });
  });

  it('returns a short last page', () => {
    const page = paginate(numbers(29), 3, 10);

    expect(page.items).toEqual([21, 22, 23, 24, 25, 26, 27, 28, 29]);
    expect(page).toMatchObject({ from: 21, to: 29 });
  });

  it('fills exactly one page when the list is the page size', () => {
    expect(paginate(numbers(10), 1, 10)).toMatchObject({ pageCount: 1, from: 1, to: 10 });
  });

  it('starts a second page at one item over the page size', () => {
    expect(paginate(numbers(11), 2, 10)).toMatchObject({ pageCount: 2, items: [11], from: 11 });
  });

  it('keeps one empty page for an empty list', () => {
    expect(paginate([], 1, 10)).toEqual({
      items: [],
      page: 1,
      pageCount: 1,
      from: 0,
      to: 0,
      total: 0,
    });
  });

  it('moves a page past the end back to the last page, for example after a filter', () => {
    expect(paginate(numbers(12), 5, 10)).toMatchObject({ page: 2, items: [11, 12] });
  });

  it('treats a page below one, or not a whole number, as page one', () => {
    expect(paginate(numbers(12), 0, 10).page).toBe(1);
    expect(paginate(numbers(12), Number.NaN, 10).page).toBe(1);
    expect(paginate(numbers(12), 1.7, 10).page).toBe(1);
  });
});

describe('pageItems', () => {
  it('lists every page when there are seven or fewer', () => {
    expect(pageItems(1, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('shows the first, the last and the neighbours of the current page', () => {
    expect(pageItems(5, 10)).toEqual([1, 'gap', 4, 5, 6, 'gap', 10]);
  });

  it('leaves no gap where the neighbours reach the ends', () => {
    expect(pageItems(1, 10)).toEqual([1, 2, 'gap', 10]);
    expect(pageItems(3, 10)).toEqual([1, 2, 3, 4, 'gap', 10]);
    expect(pageItems(10, 10)).toEqual([1, 'gap', 9, 10]);
  });
});

describe('usePagination', () => {
  it('starts on the first page and moves to another', () => {
    const { result } = renderHook(() => usePagination(numbers(25), 10));

    act(() => result.current.setPage(3));

    expect(result.current).toMatchObject({ page: 3, items: [21, 22, 23, 24, 25] });
  });

  it('goes back to the first page when the reset key changes', () => {
    const { result, rerender } = renderHook(({ key }) => usePagination(numbers(25), 10, key), {
      initialProps: { key: 'hub-a' },
    });
    act(() => result.current.setPage(2));

    rerender({ key: 'hub-b' });

    expect(result.current.page).toBe(1);
  });
});
