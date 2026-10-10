import { useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { intlTag, useI18n } from '../../lib/i18n';
import { canAnimate, prefersReducedMotion, spring } from '../../lib/motion';

/**
 * Every number that changes on screen (DESIGN.md §4.4, the mock's digits() and
 * setDigits()). There is no count-up anywhere: a number either arrives digit by
 * digit (the first open) or hands off only the digits that changed, so it never
 * shows an intermediate value, a leading zero or two digits stacked.
 *
 * - The formatted value is the element's only text (.sr, for screen readers,
 *   copying and tests); the visible glyphs are drawn by CSS from data-d in
 *   aria-hidden cells, so the number never reads or copies twice.
 * - Each digit sits in a window about cap height with soft top and bottom edges;
 *   its side padding is cancelled by a negative margin, so no glyph is clipped.
 * - Enter: each digit slides up .55em and fades in, `step` ms apart (CSS .a-digit);
 *   a separator has no window to slide in, so it fades in place with the digit before it.
 * - Change: in each changed cell the old digit leaves against the direction of
 *   the change and is gone by 28% of the run; the new one is invisible until
 *   25%, then arrives on the spring. 520 ms, 40 ms apart. If the number of
 *   digits or separators changes, the whole number enters again instead. A change
 *   that comes within one hand-off of the last (a held stepper, fast typing) swaps
 *   at once, so the number never lags behind the value.
 *   Web Animations; skipped without them (jsdom) or under reduced motion.
 * - Gradient: one Ember gradient across the whole number though each cell is
 *   clipped on its own: the number's width (--W) and each glyph's offset (--x)
 *   are measured after layout and on resize.
 */
export interface DigitsProps {
  value: number;
  /** Formats the number; by default whole numbers in the page language (1,230 / 1 230). */
  format?: (n: number) => string;
  /** Digits arrive one by one (the first open). */
  enter?: boolean;
  /** ms before the entrance, or before the hand-off from `from`, starts. */
  delay?: number;
  /** ms before the hand-off when the value changes later (typing, a delete, a live update); 0 by default. */
  changeDelay?: number;
  /** ms between digits on the entrance. */
  step?: number;
  /** Ember gradient text (large numerals only: Week average, sheet kcal). */
  gradient?: boolean;
  /**
   * The value it showed before it mounted (the moment back from Add): it
   * appears with `from` and hands off to `value` after `delay`. Ignored with `enter`.
   */
  from?: number;
  className?: string;
}

type Vars = Record<`--${string}`, string | number>;
const vars = (v: Vars) => v as CSSProperties;

const isDigit = (ch: string) => ch >= '0' && ch <= '9';
/** Two texts with the same shape differ only in digits, so they can hand off cell by cell. */
const shapeOf = (text: string) => text.replace(/[0-9]/g, '0');

// The hand-off timing (DESIGN.md §4.4): one linear timeline with an easing per keyframe.
const CHANGE_MS = 520;
const CHANGE_STAGGER_MS = 40;
const OUT_GONE_AT = 0.28;
const IN_FROM = 0.25;
const OUT_TRAVEL_EM = 0.4;
const IN_TRAVEL_EM = 0.5;

/** The page language for Intl, also outside an I18nProvider (a component rendered on its own). */
function useNumberTag(): string {
  try {
    return useI18n().tag;
  } catch {
    return intlTag(typeof document !== 'undefined' ? document.documentElement.lang : '');
  }
}

function easeIn(): string {
  if (typeof document === 'undefined' || typeof getComputedStyle !== 'function') return 'ease-in';
  return getComputedStyle(document.documentElement).getPropertyValue('--ease-in').trim() || 'ease-in';
}

/**
 * The owner's tracking (a numeral's -0.03em), applied as a margin between cells
 * instead of as letter-spacing inside them: a glyph box narrower than its ink
 * would cut the ink off a gradient numeral (background-clip: text paints only
 * inside the box). Kept in em, so a font size that follows the viewport needs
 * no new measurement.
 */
function applyTracking(root: HTMLElement): void {
  if (typeof getComputedStyle !== 'function') return;
  const style = getComputedStyle(root);
  const spacing = parseFloat(style.letterSpacing);
  const size = parseFloat(style.fontSize);
  const em = Number.isFinite(spacing) && size > 0 ? spacing / size : 0;
  const next = `${Number(em.toFixed(4))}em`;
  if (root.style.getPropertyValue('--dtrack') !== next) root.style.setProperty('--dtrack', next);
}

/** Glyph offsets for the gradient, in the number's own (untransformed) pixels. */
function paintGradient(root: HTMLElement): void {
  const width = root.offsetWidth;
  if (!width) return;
  const box = root.getBoundingClientRect();
  // A parent mid-entrance may be scaled; measure in layout pixels, not on-screen ones.
  const scale = box.width / width || 1;
  root.style.setProperty('--W', `${width}px`);
  for (const el of root.querySelectorAll<HTMLElement>('.d-cells > .dc > i, .d-cells > .dsep')) {
    const x = (el.getBoundingClientRect().left - box.left) / scale;
    el.style.setProperty('--x', `${x.toFixed(2)}px`);
  }
}

export function Digits({
  value,
  format,
  enter = false,
  delay = 0,
  changeDelay = 0,
  step = 60,
  gradient = false,
  from,
  className,
}: DigitsProps) {
  const tag = useNumberTag();
  const whole = useMemo(() => new Intl.NumberFormat(tag, { maximumFractionDigits: 0 }), [tag]);
  const fmt = format ?? ((n: number) => whole.format(n));
  const text = fmt(value);
  const fromText = from != null && !enter ? fmt(from) : null;

  // A new shape (another digit count, another separator) enters as a whole: new cells. The
  // delay of an arrival is fixed when it starts, so a later prop change cannot replay it.
  const [entry, setEntry] = useState(() => ({
    shape: shapeOf(text),
    n: 0,
    arriving: enter || (fromText != null && shapeOf(fromText) !== shapeOf(text)),
    base: delay,
  }));
  let current = entry;
  if (entry.shape !== shapeOf(text)) {
    current = { shape: shapeOf(text), n: entry.n + 1, arriving: true, base: changeDelay };
    setEntry(current);
  }

  const rootRef = useRef<HTMLSpanElement>(null);
  const last = useRef<{ text: string; value: number } | null>(null);
  const running = useRef<Animation[]>([]);
  // When the text last changed after the first layout (performance.now()).
  const lastChange = useRef(Number.NEGATIVE_INFINITY);

  // Runs when the text changes (and once on mount), with this render's props.
  useLayoutEffect(() => {
    const root = rootRef.current;
    const prev = last.current;
    last.current = { text, value };
    if (!root) return;
    if (prev == null) applyTracking(root);
    if (gradient) paintGradient(root);

    let oldText: string;
    let dir: number;
    if (prev == null) {
      if (fromText == null || from == null || fromText === text) return;
      oldText = fromText;
      dir = value >= from ? 1 : -1;
    } else {
      if (prev.text === text) return;
      oldText = prev.text;
      dir = value >= prev.value ? 1 : -1;
    }
    // Changes that come faster than a hand-off (a held stepper repeats every 85 ms, fast
    // typing) swap at once: a hand-off restarted on every step keeps the new digit hidden for
    // its first quarter, so the number would show the step before for as long as the changes
    // came. Fast means while the last hand-off is still in flight, or within one hand-off of
    // the last change even when that one swapped at once (or every other step would animate).
    const now = performance.now();
    const rapid =
      running.current.some((a) => a.playState === 'running') ||
      (prev != null && now - lastChange.current < CHANGE_MS);
    if (prev != null) lastChange.current = now;
    // A hand-off still running from the last change ends now: one old digit per cell at most.
    for (const a of running.current) a.cancel();
    running.current = [];
    for (const out of root.querySelectorAll('i.out')) out.remove();
    if (shapeOf(oldText) !== shapeOf(text)) return; // entering again instead (new cells)
    if (rapid || !canAnimate() || prefersReducedMotion()) return;

    const oldChars = [...oldText];
    const newChars = [...text];
    const cells = root.querySelectorAll<HTMLElement>('.d-cells > .dc');
    const wait = prev == null ? delay : changeDelay;
    const arrive = spring('snappy');
    const leave = easeIn();
    let k = 0;
    newChars.forEach((ch, i) => {
      if (!isDigit(ch)) return;
      const cell = cells[k];
      const index = k++;
      if (!cell || oldChars[i] === ch) return;
      const cur = cell.querySelector<HTMLElement>('i:not(.out)');
      if (!cur) return;
      // The old digit, as a sibling the cell clips: same style (gradient offset included).
      const out = cur.cloneNode(false) as HTMLElement;
      out.className = 'out';
      out.dataset.d = oldChars[i];
      cell.appendChild(out);
      const options = { duration: CHANGE_MS, delay: wait + index * CHANGE_STAGGER_MS, easing: 'linear' };
      const away = `translateY(${-OUT_TRAVEL_EM * dir}em)`;
      const near = `translateY(${IN_TRAVEL_EM * dir}em)`;
      try {
        const o = out.animate(
          [
            { transform: 'none', opacity: 1, easing: leave },
            { transform: away, opacity: 0, offset: OUT_GONE_AT },
            { transform: away, opacity: 0 },
          ],
          { ...options, fill: 'both' },
        );
        o.finished.then(() => out.remove(), () => out.remove());
        const n = cur.animate(
          [
            { transform: near, opacity: 0 },
            { transform: near, opacity: 0, offset: IN_FROM, easing: arrive },
            { transform: 'none', opacity: 1 },
          ],
          { ...options, fill: 'backwards' },
        );
        running.current.push(o, n);
      } catch {
        // An engine that rejects the easing: the new digit simply stands there.
        out.remove();
      }
    });
    // Only a new text starts a hand-off; `from` matters on the first layout alone.
  }, [text]);

  // Follow the owner's tracking and keep the gradient across the whole number when the
  // number's width changes (font swap, a breakpoint, resize).
  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const repaint = () => {
      applyTracking(root);
      if (gradient) paintGradient(root);
    };
    let off: () => void;
    if (typeof ResizeObserver === 'function') {
      const ro = new ResizeObserver(repaint);
      ro.observe(root);
      off = () => ro.disconnect();
    } else {
      window.addEventListener('resize', repaint);
      off = () => window.removeEventListener('resize', repaint);
    }
    let live = true;
    document.fonts?.ready.then(() => live && repaint(), () => undefined);
    return () => {
      live = false;
      off();
    };
  }, [gradient]);

  useLayoutEffect(
    () => () => {
      for (const a of running.current) a.cancel();
      running.current = [];
    },
    [],
  );

  // On an arrival every glyph slides up in turn; a separator goes with the digit before it,
  // so a lone comma never waits on screen for its digits.
  let k = 0;
  const arrival = (index: number) => (current.arriving ? vars({ '--d': current.base + index * step }) : undefined);
  const arriveClass = current.arriving ? 'a-digit' : undefined;
  const cells = [...text].map((ch, i) =>
    isDigit(ch) ? (
      <span className="dc" key={i}>
        <i className={arriveClass} style={arrival(k++)} data-d={ch} />
      </span>
    ) : (
      <span className={arriveClass ? `dsep ${arriveClass}` : 'dsep'} key={i} style={arrival(Math.max(0, k - 1))} data-d={ch} />
    ),
  );

  return (
    <span ref={rootRef} className={['digits', gradient ? 'grad-text' : '', className].filter(Boolean).join(' ')}>
      <span className="sr">{text}</span>
      <span className="d-cells" aria-hidden="true" key={current.n}>
        {cells}
      </span>
    </span>
  );
}
