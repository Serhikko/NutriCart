import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type PointerEvent,
  type ReactNode,
  type SyntheticEvent,
} from 'react';
import { createPortal } from 'react-dom';
import { DESKTOP_QUERY, canAnimate, prefersReducedMotion, spring, useMediaQuery } from '../../lib/motion';

/** From here up a sheet with `dock` is not modal: it sits beside the results as an inspector. */
export const DOCK_QUERY = '(min-width: 1240px)';

type SheetMode = 'phone' | 'center' | 'docked';

interface SheetProps {
  /** Keep the Sheet mounted and drive this: the exit animation needs the Sheet to outlive `open`. */
  open: boolean;
  /** Escape, a tap on the scrim, or a drag down asks for this; the parent sets `open` to false. */
  onClose: () => void;
  /** The dialog's accessible name (the product's name, "New product"). */
  label: string;
  /** At >= 1240 px, render inline as a sticky inspector instead of a modal (Add food's amount panel). */
  dock?: boolean;
  className?: string;
  children?: ReactNode;
}

// html.presenting makes the page recede (layout.css); two sheets may overlap for a moment
// (the form closing as the amount sheet opens), so the class goes only when the last one does.
let presenting = 0;
function startPresenting() {
  presenting += 1;
  document.documentElement.classList.add('presenting');
}
function stopPresenting() {
  presenting = Math.max(0, presenting - 1);
  if (presenting === 0) document.documentElement.classList.remove('presenting');
}

const FOCUSABLE =
  'a[href], area[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), iframe, [contenteditable="true"], [tabindex]:not([tabindex="-1"])';
/** A press on one of these is a tap on a control, never the start of a drag. */
const INTERACTIVE = 'a, button, input, select, textarea, label, video, [contenteditable="true"], [role="button"], [role="radio"], [role="switch"], [role="slider"], [role="checkbox"]';

function focusables(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => !el.closest('[inert], [hidden]'));
}

/** -12 · ln(1 + d/12): pulling the sheet up past its rest gives way less and less. */
const rubberBand = (d: number) => -12 * Math.log1p(-d / 12);

interface DragState {
  id: number;
  x0: number;
  y0: number;
  lastY: number;
  lastT: number;
  /** Downward speed in px/ms, smoothed over the last moves. */
  v: number;
  active: boolean;
  fromHandle: boolean;
}

/**
 * A sheet: a native <dialog> named by `label`, holding a form or a detail.
 *
 * Phone (< 900 px): a floating inset sheet that springs up from below while
 * the page recedes behind it (html.presenting), with a grabber; it can be
 * dragged down to dismiss. 900-1239 px (or any wide screen without `dock`): a
 * centred 520 px dialog over a dimmed, slightly scaled page. >= 1240 px with
 * `dock`: not modal, rendered in place as a sticky inspector beside the
 * results, so several foods can be logged without closing anything.
 *
 * Modal sheets use showModal() where it exists (the top layer escapes the
 * receding page's transform, and the rest of the page goes inert); without it
 * (jsdom, very old browsers) the dialog just gets `open` and a Tab trap. The
 * scrim is our own element so it can fade with the drag; a tap on it closes,
 * as does Escape. Focus moves to the dialog (or an element marked
 * `data-autofocus`, never a field by default: that would raise the phone's
 * keyboard over the sheet) and goes back to the opener afterwards.
 *
 * The content must not carry its own role="dialog": this element is the one
 * dialog, so getByRole('dialog', { name }) finds exactly one.
 */
export function Sheet({ open, onClose, label, dock = false, className, children }: SheetProps) {
  const wide = useMediaQuery(DOCK_QUERY, false);
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const mode: SheetMode = dock && wide ? 'docked' : desktop ? 'center' : 'phone';
  const modal = mode !== 'docked';

  // Rendered while open, and for the length of the exit after `open` goes false.
  const [shown, setShown] = useState(open);
  const [leaving, setLeaving] = useState(false);
  const [entering, setEntering] = useState(open);
  const [dragging, setDragging] = useState(false);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setShown(true);
      setLeaving(false);
      setEntering(true);
    } else if (shown) {
      // Without animations (tests) the dialog goes at once; nothing would ever end the exit.
      if (canAnimate()) setLeaving(true);
      else setShown(false);
    }
  }

  // What the sheet showed last while open, so the exit slides away the content the user saw
  // even when the parent has already cleared it (`{selected && <AmountDialog …/>}`).
  const last = useRef({ label, children });
  if (open) last.current = { label, children };
  const name = open ? label : last.current.label;
  const content = open ? children : last.current.children;

  const dialogRef = useRef<HTMLDialogElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const scrimRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const leavingRef = useRef(leaving);
  leavingRef.current = leaving;
  const drag = useRef<DragState | null>(null);
  const nativeModal = useRef(false);
  const pressedBackdrop = useRef(false);

  // Remember who opened the sheet before anything takes focus from it.
  useLayoutEffect(() => {
    if (!shown) return;
    const active = document.activeElement;
    openerRef.current = active instanceof HTMLElement && active !== document.body ? active : null;
  }, [shown]);

  // Open the native dialog in the right mode; reopen it when the mode changes under it (resize).
  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    if (!shown || !dialog) return;
    if (dialog.open && typeof dialog.close === 'function') dialog.close();
    nativeModal.current = false;
    if (modal && typeof dialog.showModal === 'function') {
      dialog.showModal();
      nativeModal.current = true;
    } else if (!modal && typeof dialog.show === 'function') dialog.show();
    else dialog.setAttribute('open', '');
    const target = dialog.querySelector<HTMLElement>('[data-autofocus]') ?? dialog;
    target.focus({ preventScroll: true });
    return () => {
      nativeModal.current = false;
      if (typeof dialog.close === 'function') {
        if (dialog.open) dialog.close();
      } else dialog.removeAttribute('open');
    };
  }, [shown, modal]);

  // Focus goes back where it came from (declared after the effect above, so it runs after the close).
  useLayoutEffect(() => {
    if (!shown) return;
    return () => {
      const opener = openerRef.current;
      openerRef.current = null;
      if (opener?.isConnected && !opener.closest('[inert]')) opener.focus({ preventScroll: true });
    };
  }, [shown]);

  // The page recedes while a modal sheet is up, and comes back as soon as it starts to leave.
  useLayoutEffect(() => {
    if (!shown || !modal || leaving) return;
    startPresenting();
    return stopPresenting;
  }, [shown, modal, leaving]);

  const finishLeaving = useCallback(() => {
    if (!leavingRef.current) return;
    const dialog = dialogRef.current;
    if (dialog && typeof dialog.close === 'function' && dialog.open) dialog.close();
    setLeaving(false);
    setShown(false);
  }, []);

  // A safety net for the exit: an animation that never ends (a hidden tab, display: none) must not
  // leave an invisible modal dialog over the page.
  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(finishLeaving, prefersReducedMotion() ? 300 : 700);
    return () => clearTimeout(timer);
  }, [leaving, finishLeaving]);

  // Docked: choosing another result swaps the content in place with a short settle.
  const previousName = useRef(name);
  useEffect(() => {
    const before = previousName.current;
    previousName.current = name;
    const body = bodyRef.current;
    if (mode !== 'docked' || leaving || before === name || !body) return;
    // The result just chosen is now the one to go back to when the inspector closes.
    const active = document.activeElement;
    if (active instanceof HTMLElement && active !== document.body && !dialogRef.current?.contains(active)) openerRef.current = active;
    if (!canAnimate() || prefersReducedMotion()) return;
    body.animate(
      [
        { opacity: 0.2, transform: 'translateY(6px)' },
        { opacity: 1, transform: 'none' },
      ],
      { duration: 260, easing: spring('smooth') },
    );
  }, [name, mode, leaving]);

  // The content may outgrow the sheet (the product form on a small phone): then a touch on the
  // body scrolls it and only the grabber drags; when it fits, the whole sheet can be pulled down.
  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    const body = bodyRef.current;
    if (!shown || mode !== 'phone' || !dialog || !body) return;
    const measure = () => dialog.toggleAttribute('data-fits', body.scrollHeight <= body.clientHeight + 1);
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(body);
    for (const child of Array.from(body.children)) observer.observe(child);
    return () => observer.disconnect();
  }, [shown, mode]);

  const requestClose = useCallback(() => {
    if (!leavingRef.current) onCloseRef.current();
  }, []);

  if (!shown) return null;

  // --- events -------------------------------------------------------------

  const onCancel = (event: SyntheticEvent<HTMLDialogElement>) => {
    // Escape on a modal dialog: we close it ourselves, so the exit can animate.
    event.preventDefault();
    requestClose();
  };

  const onNativeClose = () => {
    // The browser closed it without asking (a second Escape with no user activation in between).
    if (!leavingRef.current && dialogRef.current && !dialogRef.current.open) requestClose();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDialogElement>) => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (event.key === 'Escape' && !nativeModal.current) {
      // No native cancel here (docked, or no showModal): Escape still closes.
      event.preventDefault();
      event.stopPropagation();
      requestClose();
      return;
    }
    if (event.key === 'Tab' && modal) {
      const items = focusables(dialog);
      if (items.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const first = items[0];
      const lastItem = items[items.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || active === dialog)) {
        event.preventDefault();
        lastItem.focus();
      } else if (!event.shiftKey && active === lastItem) {
        event.preventDefault();
        first.focus();
      }
    }
  };

  // A click on the ::backdrop of a native modal dialog arrives on the dialog itself, outside its box.
  const outsideBox = (event: MouseEvent<HTMLDialogElement> | PointerEvent<HTMLDialogElement>) => {
    const dialog = dialogRef.current;
    if (!dialog || event.target !== dialog) return false;
    const r = dialog.getBoundingClientRect();
    return event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom;
  };

  const setDrag = (offset: number) => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    dialog.style.setProperty('--drag', `${offset}px`);
    const height = dialog.offsetHeight || 1;
    scrimRef.current?.style.setProperty('--drag-p', String(Math.max(0, Math.min(1, offset / height))));
  };

  const onPointerDown = (event: PointerEvent<HTMLDialogElement>) => {
    pressedBackdrop.current = outsideBox(event);
    if (mode !== 'phone' || leaving || !event.isPrimary || event.button !== 0) return;
    const target = event.target as Element;
    const fromHandle = Boolean(target.closest('.sheet-handle'));
    if (!fromHandle) {
      if (target.closest(INTERACTIVE)) return;
      // Content scrolled down: the gesture belongs to the scroll, not to the sheet.
      if ((bodyRef.current?.scrollTop ?? 0) > 0) return;
    }
    drag.current = {
      id: event.pointerId,
      x0: event.clientX,
      y0: event.clientY,
      lastY: event.clientY,
      lastT: event.timeStamp,
      v: 0,
      active: false,
      fromHandle,
    };
  };

  const onPointerMove = (event: PointerEvent<HTMLDialogElement>) => {
    const d = drag.current;
    if (!d || event.pointerId !== d.id) return;
    const dy = event.clientY - d.y0;
    if (!d.active) {
      const dx = Math.abs(event.clientX - d.x0);
      if (dx > 10 && dx > Math.abs(dy)) {
        drag.current = null; // a sideways gesture (a chip row, text selection)
        return;
      }
      if (dy < -8 && !d.fromHandle) {
        drag.current = null; // pushing the content up is a scroll
        return;
      }
      if (Math.abs(dy) < 6) return;
      d.active = true;
      dialogRef.current?.setPointerCapture?.(event.pointerId);
      setDragging(true);
    }
    const dt = event.timeStamp - d.lastT;
    if (dt > 0) d.v = 0.75 * ((event.clientY - d.lastY) / dt) + 0.25 * d.v;
    d.lastY = event.clientY;
    d.lastT = event.timeStamp;
    setDrag(dy >= 0 ? dy : rubberBand(dy));
  };

  const settle = () => {
    setDragging(false);
    setDrag(0); // the transition on .sheet.is-phone springs it back
  };

  const onPointerUp = (event: PointerEvent<HTMLDialogElement>) => {
    const d = drag.current;
    drag.current = null;
    if (!d || event.pointerId !== d.id || !d.active) return;
    const dy = event.clientY - d.y0;
    const height = dialogRef.current?.offsetHeight ?? 400;
    const flicked = d.v > 0.55 && dy > 24;
    if (dy > Math.min(160, height * 0.33) || flicked) {
      // Leave from where the finger let go: the exit keyframe starts at the current offset.
      setDragging(false);
      requestClose();
      // A parent that keeps the sheet open (nothing to close to) gets it back.
      setTimeout(() => {
        if (!leavingRef.current && dialogRef.current) setDrag(0);
      }, 80);
    } else settle();
  };

  const onPointerCancel = (event: PointerEvent<HTMLDialogElement>) => {
    const d = drag.current;
    drag.current = null;
    if (d?.active && event.pointerId === d.id) settle();
  };

  const onClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (modal && pressedBackdrop.current && outsideBox(event)) requestClose();
    pressedBackdrop.current = false;
  };

  const onAnimationEnd = (event: SyntheticEvent<HTMLDialogElement>) => {
    if (event.target !== dialogRef.current) return;
    if (leavingRef.current) finishLeaving();
    else setEntering(false);
  };

  // --- markup ---------------------------------------------------------------

  const classes = ['sheet', `is-${mode}`];
  if (entering && !leaving) classes.push('is-entering');
  if (leaving) classes.push('is-leaving');
  if (dragging) classes.push('is-dragging');
  if (className) classes.push(className);

  const dialog = (
    <dialog
      ref={dialogRef}
      className={classes.join(' ')}
      aria-label={name}
      aria-modal={modal ? true : undefined}
      tabIndex={-1}
      onCancel={onCancel}
      onClose={onNativeClose}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onClick={onClick}
      onAnimationEnd={onAnimationEnd}
    >
      {mode === 'phone' && (
        <div className="sheet-handle" aria-hidden="true">
          <span className="grabber" />
        </div>
      )}
      <div className="sheet-body" ref={bodyRef}>
        {content}
      </div>
    </dialog>
  );

  // The scrim lives on <body>, outside the receding stage, so it covers the whole screen (the
  // sidebar too). Under a native modal dialog it is inert and taps reach the dialog's backdrop.
  const scrim =
    modal && typeof document !== 'undefined'
      ? createPortal(
          <div ref={scrimRef} className={leaving ? 'scrim is-leaving' : 'scrim'} aria-hidden="true" onClick={requestClose} />,
          document.body,
        )
      : null;

  return (
    <>
      {scrim}
      {dialog}
    </>
  );
}
