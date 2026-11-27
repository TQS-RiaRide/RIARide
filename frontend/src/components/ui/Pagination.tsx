import { useTranslation } from 'react-i18next';
import { clampPage, pageCountOf, pageItems } from '../../lib/pagination';
import { Icon } from './ui';

interface PaginationProps {
  page: number;
  total: number;
  pageSize: number;
  onChange: (page: number) => void;
  /** What is being paged, for the navigation's accessible name ("Bikes, pages"). */
  label: string;
}

// Previous / page numbers / next, with "11–20 of 29". Draws nothing when one page holds it all.
export default function Pagination({ page, total, pageSize, onChange, label }: PaginationProps) {
  const { t, i18n } = useTranslation('ui');
  const pageCount = pageCountOf(total, pageSize);
  if (pageCount <= 1) return null;

  const current = clampPage(page, pageCount);
  const from = (current - 1) * pageSize + 1;
  const to = Math.min(current * pageSize, total);
  const number = (n: number) => n.toLocaleString(i18n.language);

  return (
    <nav className="pagination" aria-label={t('pagination.label', { what: label })}>
      <p className="pagination-range num">
        {t('pagination.range', { from: number(from), to: number(to), total: number(total) })}
      </p>
      <div className="pagination-pages">
        <button
          type="button"
          className="pagination-btn sq"
          onClick={() => onChange(current - 1)}
          disabled={current === 1}
          aria-label={t('pagination.previous')}
        >
          <span className="pagination-flip">
            <Icon name="chevron" size={16} />
          </span>
        </button>
        {pageItems(current, pageCount).map((item, index) =>
          item === 'gap' ? (
            <span key={`gap-${index}`} className="pagination-gap" aria-hidden="true">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              className="pagination-btn num sq"
              aria-current={item === current ? 'page' : undefined}
              aria-label={t('pagination.page', { page: item })}
              onClick={() => onChange(item)}
            >
              {number(item)}
            </button>
          ),
        )}
        <button
          type="button"
          className="pagination-btn sq"
          onClick={() => onChange(current + 1)}
          disabled={current === pageCount}
          aria-label={t('pagination.next')}
        >
          <Icon name="chevron" size={16} />
        </button>
      </div>
    </nav>
  );
}
