import type { CSSProperties } from 'react';

/**
 * A placeholder at the final geometry while data loads: --fill shapes that
 * pulse gently (opacity .55 to 1, 1.6 s) only as long as they are on screen,
 * and stand still at .7 under reduced motion.
 *
 * - line: a line of text (width 100%, height .85em by default).
 * - block: a card-sized rounded rectangle.
 * - ring: the hero ring's track (252 px or 66vw by default).
 * - row: a list row (glyph, two lines, a number at the end), 68 px tall.
 *
 * Shapes are aria-hidden. Give one skeleton of a group the `label`
 * (t('loading')) so a screen reader hears "Loading…" once.
 */
export type SkeletonVariant = 'line' | 'block' | 'ring' | 'row';

export interface SkeletonProps {
  variant?: SkeletonVariant;
  /** px (number) or any CSS length; the ring's diameter. */
  width?: number | string;
  /** px (number) or any CSS length; ignored by the ring. */
  height?: number | string;
  /** Visually hidden text read once for the group, usually t('loading'). */
  label?: string;
  className?: string;
}

const length = (v: number | string) => (typeof v === 'number' ? `${v}px` : v);

export function Skeleton({ variant = 'line', width, height, label, className }: SkeletonProps) {
  const style: Record<string, string> = {};
  if (width != null) style['--sk-w'] = length(width);
  if (height != null) style['--sk-h'] = length(height);
  const cls = ['skel', `skel-${variant}`, className].filter(Boolean).join(' ');
  return (
    <>
      <span className={cls} style={style as CSSProperties} aria-hidden="true">
        {variant === 'row' && (
          <>
            <span className="sk-glyph" />
            <span className="sk-lines">
              <span />
              <span />
            </span>
            <span className="sk-end" />
          </>
        )}
      </span>
      {label && (
        <span className="sr" role="status">
          {label}
        </span>
      )}
    </>
  );
}
