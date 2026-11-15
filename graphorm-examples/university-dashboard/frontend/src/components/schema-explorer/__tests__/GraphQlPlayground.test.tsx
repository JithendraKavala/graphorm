import "@testing-library/jest-dom/vitest";
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GraphQlPlayground } from '../GraphQlPlayground';

describe('GraphQlPlayground Component', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network offline')));
  });

  it('renders studio header, connection indicator, and demo mode banner when offline', () => {
    render(<GraphQlPlayground />);
    expect(screen.getByText('Interactive GraphQL Query Studio')).toBeInTheDocument();
    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(screen.getByText(/Demo Mode — backend offline, showing simulated responses/i)).toBeInTheDocument();
  });

  it('executes preset query in demo mode and displays simulated JSON response', async () => {
    render(<GraphQlPlayground />);
    const runBtn = screen.getByRole('button', { name: /Run Document/i });
    expect(runBtn).toBeInTheDocument();

    fireEvent.click(runBtn);

    // Should render simulated data with students
    const studentName = await screen.findByText(/Grace Hopper/i);
    expect(studentName).toBeInTheDocument();
  });

  it('switches to SQL Pushdown Plan tab and shows generated plan', async () => {
    render(<GraphQlPlayground />);
    const planTab = screen.getByRole('button', { name: /SQL Pushdown Plan/i });
    fireEvent.click(planTab);

    expect(screen.getByText(/GraphORM Selection-Set Aware SQL Pushdown/i)).toBeInTheDocument();
    expect(screen.getByText(/Zero N\+1 Invariant/i)).toBeInTheDocument();
    expect(screen.getByText(/-- Query 1 \(Root selection with projected columns\):/i)).toBeInTheDocument();
  });
});
