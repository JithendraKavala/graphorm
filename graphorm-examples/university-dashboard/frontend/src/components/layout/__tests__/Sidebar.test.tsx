import '@testing-library/jest-dom/vitest';
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Sidebar } from '../Sidebar';
import { ThemeProvider } from '../../../contexts/ThemeContext';

describe('Sidebar Theme Toggle', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
  });

  it('renders theme toggle with Light mode in dark theme and switches to Dark mode on click', () => {
    render(
      <ThemeProvider>
        <Sidebar
          activeTab="dashboard"
          setActiveTab={vi.fn()}
          dataSourceMode="graphql"
          setDataSourceMode={vi.fn()}
          isConnected={true}
          onOpenCustomizer={vi.fn()}
        />
      </ThemeProvider>
    );

    // Initial dark mode: shows button with "Light mode" label
    const toggleButton = screen.getByRole('button', { name: /light mode/i });
    expect(toggleButton).toBeInTheDocument();
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    // Clicking switches to light mode
    fireEvent.click(toggleButton);

    expect(screen.getByRole('button', { name: /dark mode/i })).toBeInTheDocument();
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('graphorm-theme')).toBe('light');

    // Clicking again switches back to dark mode
    const darkModeButton = screen.getByRole('button', { name: /dark mode/i });
    fireEvent.click(darkModeButton);

    expect(screen.getByRole('button', { name: /light mode/i })).toBeInTheDocument();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('graphorm-theme')).toBe('dark');
  });
});
