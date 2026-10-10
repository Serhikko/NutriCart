import { Icon } from './Icon';
import { withViewTransition } from '../../lib/motion';

interface DayNavProps {
  /** "Today", or the short date of the day shown. */
  label: string;
  onPrev: () => void;
  onNext: () => void;
  nextDisabled?: boolean;
  prevDisabled?: boolean;
  /** Run the change as a page transition sliding in from the side it came from (default on). */
  transition?: boolean;
}

/**
 * The white capsule ‹ Today ›. The arrow buttons keep their untranslated
 * aria-labels "previous day" / "next day": tests and the screenshot harness
 * find them by those names.
 */
export function DayNav({ label, onPrev, onNext, nextDisabled, prevDisabled, transition = true }: DayNavProps) {
  const go = (dir: 'back' | 'forward', change: () => void) => () => (transition ? withViewTransition(change, dir) : change());
  return (
    <div className="daynav">
      <button type="button" aria-label="previous day" onClick={go('back', onPrev)} disabled={prevDisabled}>
        <Icon name="left" size="sm" />
      </button>
      <span className="lbl" aria-live="polite">
        {label}
      </span>
      <button type="button" aria-label="next day" onClick={go('forward', onNext)} disabled={nextDisabled}>
        <Icon name="right" size="sm" />
      </button>
    </div>
  );
}
