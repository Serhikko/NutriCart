import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Icon } from './Icon';
import { useI18n } from '../../lib/i18n';

export interface ToastInput {
  text: string;
  icon?: 'check' | 'info';
  /** Shows an Undo button and keeps the toast up longer (5 s instead of 2.6 s). */
  undo?: () => void;
}

type ShowToast = (toast: ToastInput) => void;

// Outside a provider (a page rendered on its own in a test) toasts are silently dropped.
const ToastContext = createContext<ShowToast>(() => undefined);

/** Shows a toast: an ink capsule above the tab bar whose Ember check draws on. One at a time. */
export function useToast(): ShowToast {
  return useContext(ToastContext);
}

interface ShownToast extends ToastInput {
  id: number;
  leaving: boolean;
}

/** Where Ctrl/⌘+Z belongs to the field (its own text undo), not to the toast. */
function isTextField(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false;
  if (el.isContentEditable || el instanceof HTMLTextAreaElement) return true;
  if (!(el instanceof HTMLInputElement)) return false;
  return !['button', 'checkbox', 'radio', 'range', 'color', 'file', 'image', 'reset', 'submit'].includes(el.type);
}

const VISIBLE_MS = 2600;
const VISIBLE_WITH_UNDO_MS = 5000;
const LEAVE_MS = 260;

/**
 * Holds the current toast and renders it in a polite live region, so screen
 * readers hear "Added to Dinner · 161 kcal" without losing their place. The
 * toast stays while the pointer is over it or focus is inside it (so Undo can
 * be reached), then leaves. While an Undo is on offer, Ctrl+Z / ⌘+Z takes it
 * from anywhere outside a text field: the region is the last thing on the
 * page, too many Tab presses away for a keyboard to reach in 5 s.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const [toast, setToast] = useState<ShownToast | null>(null);
  const [held, setHeld] = useState(false);
  const seq = useRef(0);

  const show = useCallback<ShowToast>((input) => {
    seq.current += 1;
    setHeld(false);
    setToast({ ...input, id: seq.current, leaving: false });
  }, []);

  const dismiss = useCallback((id: number) => {
    setToast((current) => (current && current.id === id && !current.leaving ? { ...current, leaving: true } : current));
  }, []);

  useEffect(() => {
    if (!toast || toast.leaving || held) return;
    const timer = setTimeout(() => dismiss(toast.id), toast.undo ? VISIBLE_WITH_UNDO_MS : VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [toast, held, dismiss]);

  const undoable = toast && toast.undo && !toast.leaving ? toast : null;
  useEffect(() => {
    if (!undoable) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.shiftKey || event.altKey) return;
      // The Z key in any layout (it is "я" on a Ukrainian keyboard).
      if (event.code !== 'KeyZ' && event.key.toLowerCase() !== 'z') return;
      if (event.defaultPrevented || isTextField(event.target)) return;
      event.preventDefault();
      undoable.undo?.();
      dismiss(undoable.id);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [undoable, dismiss]);

  useEffect(() => {
    if (!toast?.leaving) return;
    const id = toast.id;
    const timer = setTimeout(() => setToast((current) => (current && current.id === id ? null : current)), LEAVE_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && (
          <div
            key={toast.id}
            className={toast.leaving ? 'toast out' : 'toast'}
            onPointerEnter={() => setHeld(true)}
            onPointerLeave={() => setHeld(false)}
            onFocus={() => setHeld(true)}
            onBlur={() => setHeld(false)}
          >
            <Icon name={toast.icon ?? 'check'} className={toast.icon === 'info' ? undefined : 't-check'} />
            <span className="toast-text">{toast.text}</span>
            {toast.undo && (
              <button
                type="button"
                className="toast-undo"
                aria-keyshortcuts="Control+Z Meta+Z"
                onClick={() => {
                  toast.undo?.();
                  dismiss(toast.id);
                }}
              >
                {t('toast.undo')}
              </button>
            )}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}
