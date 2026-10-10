import { useEffect, useRef, type KeyboardEvent, type PointerEvent } from 'react';
import { useI18n } from '../../lib/i18n';
import { Icon } from './Icon';

interface AmountStepperProps {
  /** The text in the field, exactly as typed ("207", "1,5"); the parent parses it. */
  value: string;
  onChange: (value: string) => void;
  /** The unit after the number ("g", "ml", "portions"). */
  unit: string;
  /** The units the unit button cycles through, in order; with fewer than two (or no
   *  onUnitChange) the unit is plain text. */
  units?: readonly string[];
  onUnitChange?: (unit: string) => void;
  /** How far − and + move the amount (10 g, 0.5 portions); they snap to its multiples. */
  step: number;
  /** The smallest amount − goes to (default: one step, so it never empties the field). */
  min?: number;
  max?: number;
  /** The field's accessible name: keep the existing one ("Grams", "Millilitres", "Portions"). */
  label: string;
  decLabel?: string;
  incLabel?: string;
  /** The unit button's name (default "Change unit"). */
  unitLabel?: string;
  id?: string;
  className?: string;
}

// Holding − or + repeats after a beat, like the phone's stepper.
const REPEAT_AFTER = 450;
const REPEAT_EVERY = 85;

const parse = (text: string) => Number(text.trim().replace(',', '.'));
const decimalsOf = (n: number) => (String(n).split('.')[1] ?? '').length;

/**
 * [−] [ 207 g ⇕ ] [+]: an amount field between two 48 px round buttons. The
 * field stays free text (decimal keyboard), so typing works as before; the
 * buttons and the arrow keys step it, snapping to multiples of `step`
 * (107 → 110 up, 107 → 100 down). Holding a button repeats. The unit button
 * cycles the units (g ↔ portions) when the product states a portion size.
 */
export function AmountStepper({
  value,
  onChange,
  unit,
  units,
  onUnitChange,
  step,
  min = step,
  max = Number.POSITIVE_INFINITY,
  label,
  decLabel,
  incLabel,
  unitLabel,
  id,
  className,
}: AmountStepperProps) {
  const { t, tag } = useI18n();
  const parsed = parse(value);
  const current = Number.isFinite(parsed) ? parsed : 0;

  // The latest props for the repeat timer, which outlives the render that started it.
  const latest = useRef({ value, current, step, min, max, onChange, tag });
  latest.current = { value, current, step, min, max, onChange, tag };
  const repeat = useRef<{ timer?: ReturnType<typeof setTimeout>; interval?: ReturnType<typeof setInterval>; fired: boolean }>({ fired: false });

  const stopRepeat = () => {
    clearTimeout(repeat.current.timer);
    clearInterval(repeat.current.interval);
    repeat.current.timer = undefined;
    repeat.current.interval = undefined;
  };
  useEffect(() => stopRepeat, []);

  /** One step up or down; false when the amount is already at that end. */
  const stepBy = (direction: 1 | -1): boolean => {
    const { value: text, current: v, step: s, min: lo, max: hi, onChange: change, tag: locale } = latest.current;
    const snapped = direction > 0 ? Math.floor(v / s + 1e-9) * s + s : Math.ceil(v / s - 1e-9) * s - s;
    const next = Math.min(hi, Math.max(lo, snapped));
    if ((direction > 0 && v >= hi) || (direction < 0 && v <= lo) || next === v) return false;
    const digits = decimalsOf(s);
    // Keep the decimal mark the person is using; otherwise the language's ("1,5" in Ukrainian).
    let out = new Intl.NumberFormat(locale, { maximumFractionDigits: digits, useGrouping: false }).format(next);
    if (text.includes(',')) out = out.replace('.', ',');
    else if (text.includes('.')) out = out.replace(',', '.');
    change(out);
    return true;
  };

  const startRepeat = (direction: 1 | -1) => (event: PointerEvent<HTMLButtonElement>) => {
    if (!event.isPrimary || event.button !== 0) return;
    stopRepeat();
    repeat.current.fired = false;
    repeat.current.timer = setTimeout(() => {
      repeat.current.interval = setInterval(() => {
        repeat.current.fired = true;
        if (!stepBy(direction)) stopRepeat();
      }, REPEAT_EVERY);
    }, REPEAT_AFTER);
  };

  // A click after a hold that already repeated is not one more step.
  const onStepClick = (direction: 1 | -1) => () => {
    if (repeat.current.fired) {
      repeat.current.fired = false;
      return;
    }
    stepBy(direction);
  };

  const onFieldKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      stepBy(event.key === 'ArrowUp' ? 1 : -1);
    }
  };

  const cycle = units && units.length > 1 && onUnitChange ? units : null;
  const nextUnit = () => {
    if (!cycle || !onUnitChange) return;
    const at = cycle.indexOf(unit);
    onUnitChange(cycle[(at + 1) % cycle.length]);
  };

  const canDec = current > min;
  const canInc = current < max;
  const stepButton = (direction: 1 | -1) => (
    <button
      type="button"
      className="icon-btn step-btn"
      aria-label={direction > 0 ? (incLabel ?? t('add.more')) : (decLabel ?? t('add.less'))}
      disabled={direction > 0 ? !canInc : !canDec}
      onPointerDown={startRepeat(direction)}
      onPointerUp={stopRepeat}
      onPointerLeave={stopRepeat}
      onPointerCancel={stopRepeat}
      onClick={onStepClick(direction)}
    >
      <Icon name={direction > 0 ? 'plus' : 'minus'} />
    </button>
  );

  return (
    <div className={['stepper', className ?? ''].filter(Boolean).join(' ')}>
      {stepButton(-1)}
      <label className="amount">
        <input
          id={id}
          type="text"
          inputMode="decimal"
          autoComplete="off"
          enterKeyHint="done"
          className="num"
          value={value}
          aria-label={label}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={onFieldKeyDown}
        />
        {cycle ? (
          // Named "Change unit" alone: the field's own name ("Grams", "Portions") already says which
          // unit is on, and a name holding "portions" would also answer a search for that field.
          <button type="button" className="unit-switch" aria-label={unitLabel ?? t('add.switch_unit')} onClick={nextUnit}>
            <span className="unit-text" key={unit}>
              {unit}
            </span>
            <Icon name="updown" size="xs" />
          </button>
        ) : (
          <span className="unit-switch is-static">{unit}</span>
        )}
      </label>
      {stepButton(1)}
    </div>
  );
}
