import "@testing-library/jest-dom/vitest";
import React, { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Modal } from '../Modal';

describe('Modal Component Accessibility (A11Y-01)', () => {
  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <Modal isOpen={false} onClose={vi.fn()} title="Test Modal">
        <div>Modal Content</div>
      </Modal>
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders modal dialog with aria-modal and aria-labelledby matching title id', () => {
    render(
      <Modal isOpen={true} onClose={vi.fn()} title="Faculty Profile" subtitle="Department of Computing" badge="Staff">
        <div>Modal Body Content</div>
      </Modal>
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('aria-modal', 'true');

    const labelledBy = dialog.getAttribute('aria-labelledby');
    expect(labelledBy).toBeTruthy();

    const titleElement = screen.getByText('Faculty Profile');
    expect(titleElement).toHaveAttribute('id', labelledBy);
  });

  it('invokes onClose when ESC key is pressed', () => {
    const handleClose = vi.fn();
    render(
      <Modal isOpen={true} onClose={handleClose} title="Keydown Test">
        <div>Content</div>
      </Modal>
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it('invokes onClose when close button is clicked', () => {
    const handleClose = vi.fn();
    render(
      <Modal isOpen={true} onClose={handleClose} title="Close Button Test">
        <div>Content</div>
      </Modal>
    );

    const closeButton = screen.getByRole('button', { name: /close dialog/i });
    fireEvent.click(closeButton);
    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it('restores focus to trigger element upon closing', () => {
    const TestHost = () => {
      const [isOpen, setIsOpen] = useState(false);
      return (
        <div>
          <button id="open-btn" onClick={() => setIsOpen(true)}>
            Open Dialog
          </button>
          <Modal isOpen={isOpen} onClose={() => setIsOpen(false)} title="Focus Restoration Test">
            <button id="inside-btn">Inside</button>
          </Modal>
        </div>
      );
    };

    render(<TestHost />);

    const openBtn = screen.getByText('Open Dialog');
    openBtn.focus();
    expect(document.activeElement).toBe(openBtn);

    // Open modal
    fireEvent.click(openBtn);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    // Close modal
    fireEvent.click(screen.getByRole('button', { name: /close dialog/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    // Focus restored
    expect(document.activeElement).toBe(openBtn);
  });

  it('traps Tab key focus inside the modal dialog', () => {
    render(
      <Modal isOpen={true} onClose={vi.fn()} title="Focus Trap Test">
        <input data-testid="input-1" placeholder="First Field" />
        <button data-testid="submit-btn">Submit</button>
      </Modal>
    );

    const closeBtn = screen.getByRole('button', { name: /close dialog/i });
    const input1 = screen.getByTestId('input-1');
    const submitBtn = screen.getByTestId('submit-btn');

    // Simulate focus on closeBtn (first element)
    closeBtn.focus();
    expect(document.activeElement).toBe(closeBtn);

    // Shift+Tab from first element wraps to last element (submitBtn)
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(submitBtn);

    // Tab from last element wraps back to first element (closeBtn)
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: false });
    expect(document.activeElement).toBe(closeBtn);
  });
});
