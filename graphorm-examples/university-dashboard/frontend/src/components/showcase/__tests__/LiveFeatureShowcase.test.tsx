import "@testing-library/jest-dom/vitest";
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LiveFeatureShowcase } from '../LiveFeatureShowcase';

describe('LiveFeatureShowcase Component', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network offline')));
  });

  it('renders header banner and feature tabs correctly', () => {
    render(<LiveFeatureShowcase />);
    expect(screen.getByText('GraphORM Interactive Feature Lab')).toBeInTheDocument();
    expect(screen.getByText('Relay Keyset Cursor Pagination')).toBeInTheDocument();
    expect(screen.getByText('@version Locking')).toBeInTheDocument();
    expect(screen.getByText('Idempotent Upsert')).toBeInTheDocument();
  });

  it('switches between feature tabs interactively', () => {
    render(<LiveFeatureShowcase />);
    
    // Switch to @version Locking
    const versionTab = screen.getByText('@version Locking');
    fireEvent.click(versionTab);
    expect(screen.getByText('@version Optimistic Concurrency Control')).toBeInTheDocument();
    expect(screen.getByText(/WHERE id = \? AND version = \?/i)).toBeInTheDocument();

    // Switch to Idempotent Upsert
    const upsertTab = screen.getByText('Idempotent Upsert');
    fireEvent.click(upsertTab);
    expect(screen.getByText('Idempotent Upsert Operations')).toBeInTheDocument();
    expect(screen.getAllByText(/ON CONFLICT \("email"\) DO UPDATE SET/i).length).toBeGreaterThanOrEqual(1);

    // Switch to Batch Insert
    const batchTab = screen.getByText('Batch Insert');
    fireEvent.click(batchTab);
    expect(screen.getByText('Atomic Multi-Row Batch Operations')).toBeInTheDocument();

    // Switch to Realtime CDC
    const realtimeTab = screen.getByText('CDC & @@notify');
    fireEvent.click(realtimeTab);
    expect(screen.getByText('Change Data Capture (@@notify & SSE)')).toBeInTheDocument();

    // Switch to Multi-Tenant
    const tenantTab = screen.getByText('Multi-Tenant');
    fireEvent.click(tenantTab);
    expect(screen.getByText('Multi-Tenant Partition Isolation (@tenantId)')).toBeInTheDocument();

    // Switch to L2 Cache
    const cacheTab = screen.getByText('L2 @cached');
    fireEvent.click(cacheTab);
    expect(screen.getByText('L2 @cached View Models & Mutation Eviction')).toBeInTheDocument();

    // Switch to RBAC
    const rbacTab = screen.getByText('Security @auth');
    fireEvent.click(rbacTab);
    expect(screen.getByText('Declarative Field-Level Security (@auth)')).toBeInTheDocument();
  });

  it('triggers upsert execution without error', async () => {
    render(<LiveFeatureShowcase />);
    const upsertTab = screen.getByText('Idempotent Upsert');
    fireEvent.click(upsertTab);

    const executeBtn = screen.getByText(/Execute Idempotent Upsert/i);
    expect(executeBtn).toBeInTheDocument();
    fireEvent.click(executeBtn);

    // Record should be displayed after execution
    const successBadge = await screen.findByText(/Upsert Succeeded/i);
    expect(successBadge).toBeInTheDocument();
  });
});
