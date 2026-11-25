import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import Pagination from './Pagination';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

function renderPagination(page: number, total: number, pageSize = 10) {
  const onChange = vi.fn();
  const result = render(
    <Pagination page={page} total={total} pageSize={pageSize} onChange={onChange} label="Bikes" />,
  );
  return { onChange, ...result };
}

describe('Pagination', () => {
  it('shows nothing when everything fits on one page', () => {
    const { container } = renderPagination(1, 10);

    expect(container).toBeEmptyDOMElement();
  });

  it('says which items are shown out of how many', () => {
    renderPagination(2, 29);

    expect(screen.getByRole('navigation', { name: 'Bikes, pages' })).toBeInTheDocument();
    expect(screen.getByText('11–20 of 29')).toBeInTheDocument();
  });

  it('marks the current page and moves to another page', () => {
    const { onChange } = renderPagination(2, 29);

    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(screen.getByRole('button', { name: 'Page 3' }));

    expect(onChange).toHaveBeenCalledWith(3);
  });

  it('goes to the previous and the next page', () => {
    const { onChange } = renderPagination(2, 29);

    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(onChange.mock.calls).toEqual([[1], [3]]);
  });

  it('cannot go before the first page or after the last', () => {
    renderPagination(1, 29);
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();

    renderPagination(3, 29);
    expect(screen.getAllByRole('button', { name: 'Next page' })[1]).toBeDisabled();
  });

  it('collapses distant pages into a gap', () => {
    renderPagination(5, 100);

    expect(screen.getAllByText('…')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: 'Page 2' })).not.toBeInTheDocument();
  });

  it('counts in Portuguese', async () => {
    await i18n.changeLanguage('pt');
    renderPagination(1, 29);

    expect(screen.getByText('1–10 de 29')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Página seguinte' })).toBeInTheDocument();
  });
});
