import React from 'react';
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-react';
import { PaginationState } from '../../types/graphorm';

export interface DataPaginationProps {
  pagination?: PaginationState;
  currentPage?: number;
  pageSize?: number;
  totalItems?: number;
  totalPages?: number;
  onPageChange: (newPage: number) => void;
  onPageSizeChange?: (newPageSize: number) => void;
  pageSizeOptions?: number[];
  itemLabel?: string;
  itemName?: string;
  className?: string;
}

export const DataPagination: React.FC<DataPaginationProps> = ({
  pagination,
  currentPage: propCurrentPage,
  pageSize: propPageSize,
  totalItems: propTotalItems,
  totalPages: propTotalPages,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [5, 10, 25, 50],
  itemLabel,
  itemName,
  className = '',
}) => {
  const currentPage = pagination?.currentPage ?? propCurrentPage ?? 1;
  const pageSize = pagination?.pageSize ?? propPageSize ?? 10;
  const totalItems = pagination?.totalItems ?? propTotalItems ?? 0;
  const totalPages =
    pagination?.totalPages ??
    propTotalPages ??
    Math.max(1, Math.ceil(totalItems / pageSize));
  const label = itemLabel || itemName || 'records';

  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  // Generate page numbers with ellipses
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    if (totalPages <= 7) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      if (currentPage <= 4) {
        pages.push(1, 2, 3, 4, 5, '...', totalPages);
      } else if (currentPage >= totalPages - 3) {
        pages.push(1, '...', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
      } else {
        pages.push(1, '...', currentPage - 1, currentPage, currentPage + 1, '...', totalPages);
      }
    }
    return pages;
  };

  return (
    <div
      className={`flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 bg-[#161B22]/90 border border-[#30363D] rounded-xl text-xs select-none ${className}`}
      data-testid="data-pagination"
    >
      {/* Left: Range and Totals */}
      <div className="flex items-center gap-3 text-[#8B949E]">
        <span>
          Showing{' '}
          <strong className="text-[#F0F6FC] font-mono">
            {startItem}–{endItem}
          </strong>{' '}
          of{' '}
          <strong className="text-[#F0F6FC] font-mono">{totalItems}</strong>{' '}
          {label}
        </span>

        {onPageSizeChange && (
          <div className="flex items-center gap-1.5 pl-3 border-l border-[#30363D]">
            <span className="text-[#8B949E]">Per page:</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="bg-[#0D1117] border border-[#30363D] rounded-md px-2 py-0.5 text-xs text-[#F0F6FC] focus:outline-none focus:border-[#388BFD]"
              aria-label="Records per page"
            >
              {pageSizeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Right: Page Controls */}
      <div className="flex items-center gap-1">
        {/* First Page */}
        <button
          onClick={() => onPageChange(1)}
          disabled={currentPage <= 1}
          aria-label="First page"
          className="p-1.5 rounded-lg text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] disabled:opacity-30 disabled:hover:bg-transparent transition-colors"
        >
          <ChevronsLeft className="w-3.5 h-3.5" />
        </button>

        {/* Previous Page */}
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 1}
          aria-label="Go to previous page"
          className="p-1.5 rounded-lg text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] disabled:opacity-30 disabled:hover:bg-transparent transition-colors"
        >
          <ChevronLeft className="w-3.5 h-3.5" />
        </button>

        {/* Page numbers */}
        <div className="flex items-center gap-1 px-1">
          {getPageNumbers().map((p, idx) =>
            typeof p === 'number' ? (
              <button
                key={idx}
                onClick={() => onPageChange(p)}
                className={`min-w-[28px] h-7 px-2 rounded-lg font-mono text-xs transition-colors ${
                  currentPage === p
                    ? 'bg-[#1F6FEB] text-white font-semibold shadow-sm'
                    : 'text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
                aria-current={currentPage === p ? 'page' : undefined}
              >
                {p}
              </button>
            ) : (
              <span key={idx} className="px-1 text-[#484F58]">
                {p}
              </span>
            )
          )}
        </div>

        {/* Next Page */}
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages}
          aria-label="Go to next page"
          className="p-1.5 rounded-lg text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] disabled:opacity-30 disabled:hover:bg-transparent transition-colors"
        >
          <ChevronRight className="w-3.5 h-3.5" />
        </button>

        {/* Last Page */}
        <button
          onClick={() => onPageChange(totalPages)}
          disabled={currentPage >= totalPages}
          aria-label="Last page"
          className="p-1.5 rounded-lg text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] disabled:opacity-30 disabled:hover:bg-transparent transition-colors"
        >
          <ChevronsRight className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};
