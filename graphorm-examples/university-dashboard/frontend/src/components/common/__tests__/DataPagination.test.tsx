import { describe, it, expect, vi } from 'vitest';
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { DataPagination } from '../DataPagination';
import { ProtectedField } from '../ProtectedField';
import { PaginationState } from '../../../types/graphorm';

describe('DataPagination Component', () => {
  const defaultPagination: PaginationState = {
    currentPage: 2,
    pageSize: 10,
    totalItems: 45,
    totalPages: 5,
  };

  it('renders correct range and totals information', () => {
    const onPageChange = vi.fn();
    render(
      <DataPagination
        pagination={defaultPagination}
        onPageChange={onPageChange}
        itemLabel="scholars"
      />
    );

    expect(screen.getByText('11–20')).toBeInTheDocument();
    expect(screen.getByText('45')).toBeInTheDocument();
    expect(screen.getByText(/scholars/)).toBeInTheDocument();
  });

  it('handles page navigation buttons correctly', () => {
    const onPageChange = vi.fn();
    render(
      <DataPagination
        pagination={defaultPagination}
        onPageChange={onPageChange}
      />
    );

    const prevBtn = screen.getByLabelText('Go to previous page');
    fireEvent.click(prevBtn);
    expect(onPageChange).toHaveBeenCalledWith(1);

    const nextBtn = screen.getByLabelText('Go to next page');
    fireEvent.click(nextBtn);
    expect(onPageChange).toHaveBeenCalledWith(3);
  });

  it('handles page size selection change', () => {
    const onPageChange = vi.fn();
    const onPageSizeChange = vi.fn();
    render(
      <DataPagination
        pagination={defaultPagination}
        onPageChange={onPageChange}
        onPageSizeChange={onPageSizeChange}
      />
    );

    const select = screen.getByLabelText('Records per page');
    fireEvent.change(select, { target: { value: '25' } });
    expect(onPageSizeChange).toHaveBeenCalledWith(25);
  });
});

describe('ProtectedField Component', () => {
  it('renders value when role is permitted', () => {
    render(
      <ProtectedField
        currentRole="ADMIN"
        requiredRoles={['ADMIN']}
        value={<span>$125,000</span>}
        showBadge={true}
      />
    );

    expect(screen.getByText('$125,000')).toBeInTheDocument();
    expect(screen.getByText('AUTH')).toBeInTheDocument();
  });

  it('renders lock badge when role is not permitted', () => {
    render(
      <ProtectedField
        currentRole="STUDENT"
        requiredRoles={['ADMIN', 'INSTRUCTOR']}
        value={<span>3.95 GPA</span>}
        fallbackText="Restricted GPA"
      />
    );

    expect(screen.queryByText('3.95 GPA')).not.toBeInTheDocument();
    expect(screen.getByText('Restricted GPA')).toBeInTheDocument();
    expect(screen.getByText('@auth')).toBeInTheDocument();
  });
});
