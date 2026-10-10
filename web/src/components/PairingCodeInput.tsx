import { useLayoutEffect, useRef, useState, type CSSProperties, type SyntheticEvent } from 'react';
import { isWellFormedPairingCode, normalizePairingCode, PAIRING_CODE_LENGTH } from '../lib/pairing';
import { canAnimate, prefersReducedMotion, spring } from '../lib/motion';

interface PairingCodeInputProps {
  /** The input's id; the label points at it ("code" on Welcome). */
  id?: string;
  /** The visible label ("Pairing code"); it names the input. */
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** The last attempt failed: the cells take a danger ring until the code changes. */
  invalid?: boolean;
  /** The id of the message that explains the failure. */
  describedBy?: string;
  disabled?: boolean;
}

/** The normalised index the caret sits before, from its place in the raw text. */
function caretCell(value: string, selection: number | null): number {
  const before = selection == null ? value : value.slice(0, selection);
  return Math.min(normalizePairingCode(before).length, PAIRING_CODE_LENGTH - 1);
}

/**
 * The partner's pairing code as six cells (DESIGN.md §6.1). There is still one
 * ordinary text input: it keeps its label, its 8-character limit (a code may
 * be typed with a space or a dash) and paste, and it lies over the cells with
 * transparent text. The cells only mirror what it holds, upper-cased and
 * tidied the way the server reads it, so they are hidden from screen readers.
 *
 * - The cell the next character goes into carries the focus ring (the input
 *   itself draws none, so this is its focus indicator).
 * - A character pops into its cell as it is typed (W2).
 * - Once the code is well formed the cells tint Ember one after another (W3).
 */
export function PairingCodeInput({ id = 'code', label, value, onChange, invalid = false, describedBy, disabled = false }: PairingCodeInputProps) {
  const [selection, setSelection] = useState<number | null>(null);
  const chars = [...normalizePairingCode(value)];
  const complete = isWellFormedPairingCode(chars.join(''));
  // More than six characters can only be a wrong code: every cell says so.
  const tooLong = chars.length > PAIRING_CODE_LENGTH;
  const caret = caretCell(value, selection);

  const track = (event: SyntheticEvent<HTMLInputElement>) => setSelection(event.currentTarget.selectionStart);

  // W2: a cell that has just received a character (or a different one) pops from .85. The cells
  // themselves stay mounted, so the Ember tint of a complete code can still run along them.
  const cells = useRef<HTMLDivElement>(null);
  const shown = useRef<string[]>(chars);
  const text = chars.join('\u0000');
  useLayoutEffect(() => {
    const before = shown.current;
    shown.current = text ? text.split('\u0000') : [];
    const box = cells.current;
    if (!box || !canAnimate() || prefersReducedMotion()) return;
    shown.current.forEach((ch, i) => {
      if (i >= PAIRING_CODE_LENGTH || ch === before[i]) return;
      box.children[i]?.animate([{ transform: 'scale(.85)' }, { transform: 'none' }], { duration: 380, easing: spring('bouncy') });
    });
  }, [text]);

  const cls = ['code-entry', complete ? 'is-complete' : '', invalid || tooLong ? 'is-invalid' : ''].filter(Boolean).join(' ');

  return (
    <div className={cls}>
      <label htmlFor={id} className="field-label">
        {label}
      </label>
      <div className="code-box">
        <div className="code-cells" aria-hidden="true" ref={cells}>
          {Array.from({ length: PAIRING_CODE_LENGTH }, (_, i) => {
            const ch = chars[i];
            const cellCls = ['code-cell', ch ? 'filled' : '', i === caret ? 'caret' : ''].filter(Boolean).join(' ');
            return (
              <span className={cellCls} key={i} style={{ '--i': i } as CSSProperties}>
                {ch}
              </span>
            );
          })}
        </div>
        <input
          id={id}
          className="code-input"
          type="text"
          inputMode="text"
          autoComplete="off"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          maxLength={8}
          value={value}
          disabled={disabled}
          aria-invalid={invalid || tooLong ? true : undefined}
          aria-describedby={describedBy}
          onChange={(e) => {
            onChange(e.target.value);
            setSelection(e.target.selectionStart);
          }}
          onSelect={track}
          onFocus={track}
        />
      </div>
    </div>
  );
}
