import type { CSSProperties } from 'react';
import { Icon } from './Icon';
import { Ring } from './Ring';

/**
 * One day of a week strip (Week hero, the desktop "My week" card): exactly one
 * state per marker, and none of them relies on colour alone.
 *
 * - GOOD: an Ember disc with a white check (on plan).
 * - OVER: an ink ring with "!" (past the goal by more than the tolerance).
 * - TODAY: a mini Ring of today so far (its second lap in ink when over).
 * - EMPTY: a dashed grey ring (nothing logged).
 *
 * The marker is role="img", named by a full sentence ("Tuesday: 2,060 kcal,
 * on plan", the week.marker_* strings); the short visible label below the
 * disc ("Tue", "Today") is part of the picture.
 */
export type DayMarkerState = 'GOOD' | 'OVER' | 'TODAY' | 'EMPTY';

export interface DayMarkerProps {
  state: DayMarkerState;
  /** Eaten / target for TODAY's mini ring (may exceed 1). */
  progress?: number;
  /** The short visible label: Intl weekday short ("Sat"), or "Today". */
  label: string;
  /** The accessible sentence. */
  name: string;
  /** Disc diameter: 36 (Week hero) or 30 (the mini week card). */
  size?: number;
  /** ms before the disc pops on the first open (K2: 260 + 50 per day); today's ring sweeps 120 ms later. */
  delay?: number;
  /** Today's mini ring sweeps from 0 (the first open of the week). */
  sweep?: boolean;
  className?: string;
}

// The state classes, prefixed where a bare word would collide with another component (.empty is EmptyState).
const STATE_CLASS: Record<DayMarkerState, string> = { GOOD: 'good', OVER: 'over', TODAY: 'today', EMPTY: 'unlogged' };

export function DayMarker({ state, progress = 0, label, name, size = 36, delay = 0, sweep = false, className }: DayMarkerProps) {
  const today = state === 'TODAY';
  const style = { '--d': delay, ...(size !== 36 ? { '--dm-size': `${size}px` } : {}) } as CSSProperties;
  return (
    <div
      className={['dm', STATE_CLASS[state], size < 34 ? 'dm-small' : '', className].filter(Boolean).join(' ')}
      role="img"
      aria-label={name}
    >
      <span className="disc a-pop" style={style}>
        {state === 'GOOD' && <Icon name="check" size="xs" />}
        {state === 'OVER' && <Icon name="bang" size="xs" />}
        {state === 'EMPTY' && (
          <svg className="dm-empty" viewBox="0 0 36 36" aria-hidden="true" focusable="false">
            <circle cx="18" cy="18" r="17" pathLength={100} />
          </svg>
        )}
        {today && (
          <Ring value={progress} target={1} size={size} stroke={size < 34 ? 4.5 : 5} sweep={sweep} delay={delay + 120} className="mini" />
        )}
      </span>
      {/* Fades in just after its disc pops, never ahead of it. */}
      <span className="lbl a-fade-up" style={{ '--d': delay + 60 } as CSSProperties}>{label}</span>
    </div>
  );
}
