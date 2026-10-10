import type { ReactNode } from 'react';

export interface ChipOption<T extends string | number> {
  value: T;
  /** The visible text ("1 portion · 207 g"); also the accessible name unless `ariaLabel` is given. */
  label: ReactNode;
  ariaLabel?: string;
  disabled?: boolean;
}

interface ChipGroupProps<T extends string | number> {
  /** The group's accessible name ("Amount", "Quick messages"). */
  label: string;
  options: readonly ChipOption<T>[];
  /**
   * The chosen chip (aria-pressed="true", drawn in ink); null when none is.
   * Leave it out for action chips that do something once (the nudge's quick
   * messages): they then carry no pressed state at all.
   */
  value?: T | null;
  onChange: (value: T) => void;
  className?: string;
}

/**
 * A row of capsule chips that wraps onto more lines rather than scrolling or
 * clipping. Each chip is a 36 px capsule with a 44 px hit area. A chosen chip
 * turns ink (near-black, white at night), so it never reads as an accent.
 */
export function ChipGroup<T extends string | number>({ label, options, value, onChange, className }: ChipGroupProps<T>) {
  const toggles = value !== undefined;
  return (
    <div className={['chips', className ?? ''].filter(Boolean).join(' ')} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={String(option.value)}
          type="button"
          className="chip"
          aria-pressed={toggles ? option.value === value : undefined}
          aria-label={option.ariaLabel}
          disabled={option.disabled}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
