import { useRef, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react';

export interface SegmentOption<T extends string | number> {
  value: T;
  /** The visible text; it is also the radio's accessible name unless `ariaLabel` is given. */
  label: ReactNode;
  ariaLabel?: string;
  disabled?: boolean;
}

interface SegmentedControlProps<T extends string | number> {
  /** The group's accessible name ("Sex", "Language"); or point at a visible label with `labelledBy`. */
  label?: string;
  labelledBy?: string;
  options: readonly SegmentOption<T>[];
  /** The chosen option; null or a value not in `options` leaves every segment unchecked. */
  value: T | null | undefined;
  onChange: (value: T) => void;
  className?: string;
  id?: string;
}

const NEXT_KEYS = new Set(['ArrowRight', 'ArrowDown']);
const PREVIOUS_KEYS = new Set(['ArrowLeft', 'ArrowUp']);

/**
 * iOS-style segmented control: a grey track with a raised thumb that slides
 * to the chosen segment. It is a radio group: one tab stop (the chosen
 * segment, or the first one when none is chosen), arrow keys move and choose
 * (wrapping round), Home and End jump to the ends.
 *
 * The thumb is placed in CSS from --i (the chosen index) and --n (the number
 * of segments), so it is right on the first paint and without layout (tests).
 */
export function SegmentedControl<T extends string | number>({
  label,
  labelledBy,
  options,
  value,
  onChange,
  className,
  id,
}: SegmentedControlProps<T>) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const selected = options.findIndex((o) => o.value === value);
  const firstEnabled = options.findIndex((o) => !o.disabled);
  const tabStop = selected >= 0 && !options[selected].disabled ? selected : firstEnabled;

  const choose = (index: number) => {
    const option = options[index];
    if (!option || option.disabled) return;
    buttons.current[index]?.focus();
    if (option.value !== value) onChange(option.value);
  };

  /** The next enabled segment from `from` in direction `step`, wrapping; -1 when none. */
  const seek = (from: number, step: 1 | -1): number => {
    const n = options.length;
    for (let k = 1; k <= n; k += 1) {
      const i = (((from + step * k) % n) + n) % n;
      if (!options[i].disabled) return i;
    }
    return -1;
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const current = buttons.current.findIndex((b) => b === document.activeElement);
    const from = current >= 0 ? current : tabStop;
    let to = -1;
    if (NEXT_KEYS.has(event.key)) to = seek(from, 1);
    else if (PREVIOUS_KEYS.has(event.key)) to = seek(from, -1);
    else if (event.key === 'Home') to = seek(-1, 1);
    else if (event.key === 'End') to = seek(options.length, -1);
    else return;
    event.preventDefault();
    if (to >= 0) choose(to);
  };

  const vars = { '--n': Math.max(1, options.length), '--i': Math.max(0, selected) } as CSSProperties;

  return (
    <div
      id={id}
      className={['seg', className ?? ''].filter(Boolean).join(' ')}
      role="radiogroup"
      aria-label={labelledBy ? undefined : label}
      aria-labelledby={labelledBy}
      style={vars}
      onKeyDown={onKeyDown}
    >
      {selected >= 0 && <span className="seg-thumb" aria-hidden="true" />}
      {options.map((option, index) => (
        <button
          key={String(option.value)}
          ref={(el) => {
            buttons.current[index] = el;
          }}
          type="button"
          role="radio"
          className="seg-btn"
          aria-checked={index === selected}
          aria-label={option.ariaLabel}
          tabIndex={index === tabStop ? 0 : -1}
          disabled={option.disabled}
          onClick={() => choose(index)}
        >
          <span className="seg-text">{option.label}</span>
        </button>
      ))}
    </div>
  );
}
