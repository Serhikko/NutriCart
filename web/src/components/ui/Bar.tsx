import type { CSSProperties } from 'react';
import type { Metric } from './Card';

/**
 * A 6 px progress capsule in a metric's colour (macros against their targets).
 * The fill is a full-width capsule moved by translateX, so it keeps its round
 * ends at any value and moves on the compositor: it fills from the left on the
 * first open (.a-bar, T7) and glides to a new value when the amount changes.
 */
export interface BarProps {
  value: number;
  max: number;
  metric: Metric;
  /** The accessible name ("Protein"). */
  label: string;
  /** ms before the first-open fill. */
  delay?: number;
  /** What a screen reader says for the value ("51 g of 115 g"); the bare number otherwise. */
  valueText?: string;
  className?: string;
}

export function Bar({ value, max, metric, label, delay, valueText, className }: BarProps) {
  const safe = Number.isFinite(value) ? Math.max(0, value) : 0;
  const w = max > 0 ? Math.min(1, safe / max) : 0;
  const style = (delay != null ? { '--d': delay } : {}) as CSSProperties;
  return (
    <span
      className={['bar', 'a-bar', `m-${metric}`, className].filter(Boolean).join(' ')}
      style={style}
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      // A meter's value stays within its range; past the target it reads as full, with the real amount in the text.
      aria-valuenow={Math.min(safe, Math.max(0, max))}
      aria-valuetext={valueText}
    >
      <i style={{ '--w': Number(w.toFixed(4)) } as CSSProperties} />
    </span>
  );
}
