import { describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { I18nProvider } from '../../../lib/i18n';
import { ToastProvider, useToast } from '../Toast';

function Page({ undo }: { undo: () => void }) {
  const toast = useToast();
  return (
    <>
      <button type="button" onClick={() => toast({ text: 'Blueberries deleted', undo })}>
        delete
      </button>
      <input aria-label="Search food…" />
    </>
  );
}

function renderPage(undo: () => void) {
  localStorage.setItem('nutricart.locale', 'en');
  render(
    <I18nProvider>
      <ToastProvider>
        <Page undo={undo} />
      </ToastProvider>
    </I18nProvider>,
  );
  act(() => fireEvent.click(screen.getByRole('button', { name: 'delete' })));
}

describe('Toast Undo from the keyboard', () => {
  it('names the shortcut on the Undo button', () => {
    renderPage(() => undefined);
    expect(screen.getByRole('button', { name: 'Undo' })).toHaveAttribute('aria-keyshortcuts', 'Control+Z Meta+Z');
  });

  it('takes Ctrl+Z or ⌘+Z from anywhere on the page, once', () => {
    const undo = vi.fn();
    renderPage(undo);
    act(() => {
      fireEvent.keyDown(document.body, { key: 'z', code: 'KeyZ', ctrlKey: true });
    });
    expect(undo).toHaveBeenCalledTimes(1);
    // The toast is leaving: a second press does nothing more.
    act(() => {
      fireEvent.keyDown(document.body, { key: 'z', code: 'KeyZ', metaKey: true });
    });
    expect(undo).toHaveBeenCalledTimes(1);
  });

  it('works on a Ukrainian layout, where the key reads "я"', () => {
    const undo = vi.fn();
    renderPage(undo);
    act(() => {
      fireEvent.keyDown(document.body, { key: 'я', code: 'KeyZ', metaKey: true });
    });
    expect(undo).toHaveBeenCalledTimes(1);
  });

  it('leaves Ctrl+Z to a text field, and ignores Z without the modifier', () => {
    const undo = vi.fn();
    renderPage(undo);
    const field = screen.getByRole('textbox', { name: 'Search food…' });
    act(() => {
      fireEvent.keyDown(field, { key: 'z', code: 'KeyZ', ctrlKey: true });
      fireEvent.keyDown(document.body, { key: 'z', code: 'KeyZ' });
      fireEvent.keyDown(document.body, { key: 'z', code: 'KeyZ', ctrlKey: true, shiftKey: true });
    });
    expect(undo).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });
});
