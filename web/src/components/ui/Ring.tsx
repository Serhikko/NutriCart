import { useId, useRef, useState, type CSSProperties, type ReactNode } from 'react';

/**
 * The day ring: one thick SVG stroke that fills from amber through vermilion
 * to raspberry (DESIGN.md §4.3, the mock's ring()).
 *
 * - One circular path that starts at 12 o'clock, pathLength 100, drawn as two
 *   butt-ended dashes: the right half (amber to vermilion) and the left half
 *   (vermilion to raspberry). Each half has a vertical gradient in the SVG's
 *   own coordinates that is flat across the 6 o'clock band, so the halves meet
 *   without a seam (the right half runs 0.4% under the left one).
 * - Round ends are circles filled with the same gradients, so a cap is always
 *   exactly the colour of the stroke under it. The end cap is placed with CSS
 *   trig from --p, never rotated (a rotated element would rotate its gradient).
 * - Past the goal a second lap is drawn in ink; the sheet preview adds a bright
 *   raspberry "this food" arc after today's part.
 * - The registered --p drives the dashes and the end cap, so animating it
 *   sweeps everything together. Without @property support the ring just shows
 *   its final value.
 *
 * The SVG is always aria-hidden. Give `label` to wrap it (and any centre
 * content passed as children) in a role="img" element named by that sentence;
 * otherwise the caller's wrapper carries the name.
 */
export interface RingProps {
  /** Today's amount; p = value / target, which may exceed 1 (over target). */
  value: number;
  target: number;
  /** Diameter and stroke in px: hero 252/26, sheet preview 88/11, onboarding 140/16, markers 36/5 (30/4.5). */
  size: number;
  stroke: number;
  /** The previous value, in the units of `value`: the first sweep starts here, never from 0 on revisits. */
  from?: number;
  /** "This food" in the units of `value`: a raspberry arc from p to p + add / target (sheet preview). */
  add?: number;
  /** Today's part at 32% opacity, under the "this food" arc (sheet preview). */
  muted?: boolean;
  /**
   * Sweep from `from` (or 0) when the ring appears: the first open of the day,
   * or a day change that remounts it. Later changes of `value` on a mounted
   * ring always sweep, from wherever the ring is drawn at that moment.
   */
  sweep?: boolean;
  /** ms before the first sweep starts. */
  delay?: number;
  /** ms before one pass of light runs round the ring (first open, the zone moment); null for none. */
  shine?: number | null;
  /** On the <svg>: 'mini' (day markers), 'preview' (sheet), 'shared' (the hero-ring view transition), 'tick' (a 600 ms ease-out change, the code countdown). */
  className?: string;
  /** The sentence for screen readers ("1,230 / 2,100 kcal, 870 left"); makes the wrapper role="img". */
  label?: string;
  /** Centre content (numeral, percentage), drawn over the ring inside the named wrapper. Needs `label`. */
  children?: ReactNode;
}

type Vars = Record<`--${string}`, string | number>;
const vars = (v: Vars) => v as CSSProperties;

// Two laps is the most the ring can show (the second one in ink); anything more reads the same.
const MAX_P = 2;
const fraction = (v: number, target: number) => (target > 0 && Number.isFinite(v) ? Math.min(MAX_P, Math.max(0, v / target)) : 0);
const fixed = (n: number) => Number(n.toFixed(4));

/**
 * Registered custom properties animate smoothly; unregistered ones would flip
 * at the halfway point, so without registerProperty the ring stays still
 * (jsdom, very old browsers).
 */
function canSweep(): boolean {
  return typeof CSS !== 'undefined' && typeof (CSS as { registerProperty?: unknown }).registerProperty === 'function';
}

/** Where the ring is drawn right now, mid-sweep included (the registered --p as computed). */
function drawnP(svg: SVGSVGElement | null): number | null {
  if (!svg || typeof getComputedStyle !== 'function') return null;
  const raw = parseFloat(getComputedStyle(svg).getPropertyValue('--p'));
  return Number.isFinite(raw) ? raw : null;
}

// What the ring is doing: still, the first sweep (with the delay it was asked for when it
// appeared, kept so a later prop change cannot restart it), or the nth change.
type Run = { p: number; from: number; mode: 'still' | 'enter' | 'change'; n: number; d: number };

export function Ring({
  value,
  target,
  size,
  stroke,
  from,
  add,
  muted = false,
  sweep = false,
  delay = 0,
  shine = null,
  className,
  label,
  children,
}: RingProps) {
  // Gradient ids must be unique on the page and safe inside url(#...).
  const id = `rg${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const svgRef = useRef<SVGSVGElement>(null);
  const p = fixed(fraction(value, target));

  const [run, setRun] = useState<Run>(() =>
    sweep
      ? { p, from: fixed(fraction(from ?? 0, target)), mode: 'enter', n: 0, d: delay }
      : { p, from: p, mode: 'still', n: 0, d: 0 },
  );
  let current = run;
  if (run.p !== p) {
    // The value changed under a mounted ring (an add, a delete, a live update, another day):
    // sweep from where the ring is drawn now to the new value, never from zero.
    const now = drawnP(svgRef.current) ?? run.p;
    current = { p, from: fixed(Math.min(MAX_P, Math.max(0, now))), mode: 'change', n: run.n + 1, d: 0 };
    setRun(current);
  }

  const animated = current.mode !== 'still' && current.from !== current.p && canSweep();
  // A change alternates between two identical keyframes, so each one restarts the animation.
  const sweepClass = !animated ? '' : current.mode === 'enter' ? 'a-sweep' : `ring-resweep-${current.n % 2 ? 'a' : 'b'}`;

  const R = (size - stroke) / 2;
  const C = size / 2;
  const top = stroke / 2;
  const bottom = size - stroke / 2;
  const path = `M${C} ${top}A${R} ${R} 0 0 1 ${C} ${bottom}A${R} ${R} 0 0 1 ${C} ${top}`;
  // The static cap positions, for browsers without CSS trig (CSS moves them with --p elsewhere).
  const at = (q: number) => ({
    cx: fixed(C + R * Math.sin(q * 2 * Math.PI)),
    cy: fixed(C - R * Math.cos(q * 2 * Math.PI)),
  });
  const end = Math.min(p, 1);
  const g = add != null ? fixed(Math.max(0, fraction(add, target))) : null;
  const showOverLap = Math.max(p, current.from) > 1;

  const style: Vars = {
    '--p': p,
    '--from': current.from,
    '--d': current.d,
    '--C': `${C}px`,
    '--R': `${R}px`,
  };
  if (g != null) style['--g'] = g;

  const svg = (
    <svg
      ref={svgRef}
      className={['ring', sweepClass, className].filter(Boolean).join(' ')}
      viewBox={`0 0 ${size} ${size}`}
      width={size}
      height={size}
      style={vars(style)}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={`${id}a`} gradientUnits="userSpaceOnUse" x1="0" y1={stroke} x2="0" y2={size - stroke}>
          <stop offset="0" className="st-1" />
          <stop offset="1" className="st-2" />
        </linearGradient>
        <linearGradient id={`${id}b`} gradientUnits="userSpaceOnUse" x1="0" y1={size - stroke} x2="0" y2={stroke}>
          <stop offset="0" className="st-2" />
          <stop offset="1" className="st-3" />
        </linearGradient>
        <radialGradient id={`${id}s`}>
          <stop offset=".5" className="st-shade" />
          <stop offset="1" className="st-shade st-clear" />
        </radialGradient>
      </defs>
      <path className="track" d={path} strokeWidth={stroke} />
      <g className={muted ? 'today ring-muted' : 'today'}>
        <path className="arc a" d={path} pathLength={100} strokeWidth={stroke} stroke={`url(#${id}a)`} />
        <path className="arc b" d={path} pathLength={100} strokeWidth={stroke} stroke={`url(#${id}b)`} />
        <circle className="cap start" {...at(0)} r={stroke / 2} fill={`url(#${id}a)`} />
        <circle className="cap-shadow end" {...at(end + 0.012)} r={fixed(stroke * 0.62)} fill={`url(#${id}s)`} />
        <circle className="cap end ea" {...at(end)} r={stroke / 2} fill={`url(#${id}a)`} />
        <circle className="cap end eb" {...at(end)} r={stroke / 2} fill={`url(#${id}b)`} />
      </g>
      {showOverLap && <path className="arc c" d={path} pathLength={100} strokeWidth={stroke} />}
      {g != null && <path className="add-arc" d={path} pathLength={100} strokeWidth={stroke} />}
      {shine != null && (
        // Keyed by its delay, so asking for another pass (the zone moment) plays it again.
        <g key={shine} className="rotor a-shine" style={vars({ '--d': shine })}>
          <path d={path} pathLength={100} strokeWidth={Math.max(2, stroke - 6)} />
        </g>
      )}
    </svg>
  );

  if (label == null) return svg;
  return (
    <span className="ring-wrap" role="img" aria-label={label}>
      {svg}
      {children}
    </span>
  );
}
