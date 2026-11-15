import "@testing-library/jest-dom/vitest";
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SchemaInspector } from '../SchemaInspector';

describe('SchemaInspector Component', () => {
  it('renders declarative schema DSL and AST hierarchy header', () => {
    render(<SchemaInspector />);
    expect(screen.getByText('Declarative Schema DSL')).toBeInTheDocument();
    expect(screen.getByText('Parsed Schema AST Tree Hierarchy')).toBeInTheDocument();
  });

  it('renders all model and view model nodes in the AST tree', () => {
    render(<SchemaInspector />);
    // Check models
    expect(screen.getAllByText('Department').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Instructor').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Student').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Course').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Enrollment').length).toBeGreaterThanOrEqual(1);

    // Check view models
    expect(screen.getAllByText('DepartmentStats').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('StudentDashboard').length).toBeGreaterThanOrEqual(1);
  });

  it('displays field details, types, and directives', () => {
    render(<SchemaInspector />);
    expect(screen.getAllByText('@unique').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/@auth\(roles: \["ADMIN"\]\)/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('@aggregate(count: "e.id")')).toBeInTheDocument();
  });

  it('supports expand all and collapse all controls', () => {
    render(<SchemaInspector />);
    const collapseBtn = screen.getByRole('button', { name: /Collapse All/i });
    fireEvent.click(collapseBtn);

    // Node contents should be collapsed
    expect(screen.queryByText('@aggregate(count: "e.id")')).not.toBeInTheDocument();

    const expandBtn = screen.getByRole('button', { name: /Expand All/i });
    fireEvent.click(expandBtn);

    expect(screen.getByText('@aggregate(count: "e.id")')).toBeInTheDocument();
  });
});
