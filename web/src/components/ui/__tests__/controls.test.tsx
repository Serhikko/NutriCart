import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { useState, type ReactNode } from 'react';
import { I18nProvider, type Locale } from '../../../lib/i18n';
import { SegmentedControl } from '../SegmentedControl';
import { Sheet } from '../Sheet';
import { AmountStepper } from '../AmountStepper';
import { ChipGroup } from '../ChipGroup';
import { Notice } from '../Notice';
import { EmptyState } from '../EmptyState';
import { OfflineBanner } from '../OfflineBanner';

function withI18n(ui: ReactNode, locale: Locale = 'en') {
  localStorage.setItem('nutricart.locale', locale);
  return render(<I18nProvider>{ui}</I18nProvider>);
}

// ---------------------------------------------------------------- SegmentedControl

const SEX = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
] as const;
const RATES = [
  { value: 0.25, label: '0.25' },
  { value: 0.5, label: '0.5' },
  { value: 0.75, label: '0.75' },
  { value: 1, label: '1' },
];

function Rates({ initial = 0.5, onChange }: { initial?: number | null; onChange?: (v: number) => void }) {
  const [value, setValue] = useState<number | null>(initial);
  return (
    <SegmentedControl
      label="Pace"
      options={RATES}
      value={value}
      onChange={(v) => {
        setValue(v);
        onChange?.(v);
      }}
    />
  );
}

describe('SegmentedControl', () => {
  it('is a named radio group with one checked radio and one tab stop', () => {
    render(<SegmentedControl label="Sex" options={SEX} value="FEMALE" onChange={() => undefined} />);
    const group = screen.getByRole('radiogroup', { name: 'Sex' });
    const male = within(group).getByRole('radio', { name: 'Male' });
    const female = within(group).getByRole('radio', { name: 'Female' });
    expect(female).toHaveAttribute('aria-checked', 'true');
    expect(male).toHaveAttribute('aria-checked', 'false');
    expect(female).toHaveAttribute('tabindex', '0');
    expect(male).toHaveAttribute('tabindex', '-1');
  });

  it('makes the first segment the tab stop when nothing is chosen yet', () => {
    render(<SegmentedControl label="Sex" options={SEX} value={null} onChange={() => undefined} />);
    expect(screen.getByRole('radio', { name: 'Male' })).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('radio', { name: 'Female' })).toHaveAttribute('tabindex', '-1');
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true);
  });

  it('moves and chooses with the arrow keys, wrapping round, and jumps with Home and End', () => {
    const onChange = vi.fn();
    render(<Rates onChange={onChange} />);
    const half = screen.getByRole('radio', { name: '0.5' });
    half.focus();

    fireEvent.keyDown(half, { key: 'ArrowRight' });
    const threeQuarters = screen.getByRole('radio', { name: '0.75' });
    expect(onChange).toHaveBeenLastCalledWith(0.75);
    expect(threeQuarters).toHaveFocus();
    expect(threeQuarters).toHaveAttribute('aria-checked', 'true');
    expect(threeQuarters).toHaveAttribute('tabindex', '0');
    expect(half).toHaveAttribute('tabindex', '-1');

    fireEvent.keyDown(threeQuarters, { key: 'ArrowDown' });
    fireEvent.keyDown(screen.getByRole('radio', { name: '1' }), { key: 'ArrowRight' });
    expect(onChange).toHaveBeenLastCalledWith(0.25);
    expect(screen.getByRole('radio', { name: '0.25' })).toHaveFocus();

    fireEvent.keyDown(screen.getByRole('radio', { name: '0.25' }), { key: 'ArrowLeft' });
    expect(onChange).toHaveBeenLastCalledWith(1);

    fireEvent.keyDown(screen.getByRole('radio', { name: '1' }), { key: 'Home' });
    expect(onChange).toHaveBeenLastCalledWith(0.25);
    fireEvent.keyDown(screen.getByRole('radio', { name: '0.25' }), { key: 'End' });
    expect(onChange).toHaveBeenLastCalledWith(1);
    expect(screen.getByRole('radio', { name: '1' })).toHaveFocus();
  });

  it('chooses on click and skips disabled segments with the keyboard', () => {
    const onChange = vi.fn();
    render(
      <SegmentedControl
        label="Language"
        options={[
          { value: 'en', label: 'English' },
          { value: 'xx', label: 'Soon', disabled: true },
          { value: 'uk', label: 'Українська' },
        ]}
        value="en"
        onChange={onChange}
      />,
    );
    fireEvent.click(screen.getByRole('radio', { name: 'Українська' }));
    expect(onChange).toHaveBeenLastCalledWith('uk');
    const english = screen.getByRole('radio', { name: 'English' });
    english.focus();
    fireEvent.keyDown(english, { key: 'ArrowRight' });
    expect(onChange).toHaveBeenLastCalledWith('uk');
    expect(screen.getByRole('radio', { name: 'Soon' })).toBeDisabled();
  });

  it('does not report a change when the chosen segment is clicked again', () => {
    const onChange = vi.fn();
    render(<SegmentedControl label="Sex" options={SEX} value="MALE" onChange={onChange} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Male' }));
    expect(onChange).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------- Sheet

function SheetHarness({ initial = false, dock = false, onClose }: { initial?: boolean; dock?: boolean; onClose?: () => void }) {
  const [open, setOpen] = useState(initial);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Open
      </button>
      <Sheet
        open={open}
        dock={dock}
        label="Baked Beans in Tomato Sauce"
        onClose={() => {
          onClose?.();
          setOpen(false);
        }}
      >
        <h2>Baked Beans in Tomato Sauce</h2>
        <button type="button" aria-label="Cancel" onClick={() => setOpen(false)}>
          ×
        </button>
        <input aria-label="Grams" defaultValue="207" />
        <button type="button">Add</button>
      </Sheet>
    </>
  );
}

describe('Sheet', () => {
  afterEach(() => {
    document.documentElement.classList.remove('presenting');
  });

  it('renders nothing while closed', () => {
    render(<SheetHarness />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders one dialog named by its label, takes focus, and recedes the page', () => {
    render(<SheetHarness />);
    const opener = screen.getByRole('button', { name: 'Open' });
    opener.focus();
    fireEvent.click(opener);
    const dialog = screen.getByRole('dialog', { name: 'Baked Beans in Tomato Sauce' });
    expect(screen.getAllByRole('dialog')).toHaveLength(1);
    expect(dialog.tagName).toBe('DIALOG');
    expect(dialog).toHaveAttribute('open');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveFocus();
    expect(document.documentElement).toHaveClass('presenting');
    // The controls inside are found within it, exactly once each.
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Cancel' })).toHaveLength(1);
  });

  it('closes on Escape and gives focus back to the opener', () => {
    const onClose = vi.fn();
    render(<SheetHarness onClose={onClose} />);
    const opener = screen.getByRole('button', { name: 'Open' });
    opener.focus();
    fireEvent.click(opener);
    const dialog = screen.getByRole('dialog', { name: 'Baked Beans in Tomato Sauce' });
    fireEvent.keyDown(within(dialog).getByLabelText('Grams'), { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.documentElement).not.toHaveClass('presenting');
    expect(opener).toHaveFocus();
  });

  it('closes when the scrim is tapped', () => {
    const onClose = vi.fn();
    render(<SheetHarness initial onClose={onClose} />);
    const scrim = document.querySelector('.scrim');
    expect(scrim).not.toBeNull();
    fireEvent.click(scrim!);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.querySelector('.scrim')).toBeNull();
  });

  it('keeps Tab inside the sheet', () => {
    render(<SheetHarness initial />);
    const dialog = screen.getByRole('dialog');
    const add = within(dialog).getByRole('button', { name: 'Add' });
    const cancel = within(dialog).getByRole('button', { name: 'Cancel' });
    add.focus();
    fireEvent.keyDown(add, { key: 'Tab' });
    expect(cancel).toHaveFocus();
    fireEvent.keyDown(cancel, { key: 'Tab', shiftKey: true });
    expect(add).toHaveFocus();
  });

  it('closes from a parent that clears `open` (the content\'s own Cancel)', () => {
    render(<SheetHarness initial />);
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.documentElement).not.toHaveClass('presenting');
  });

  it('keeps the page in place while two sheets hand over to each other', () => {
    const { rerender } = render(
      <>
        <Sheet open label="New product" onClose={() => undefined}>
          form
        </Sheet>
        <Sheet open={false} label="Kefir" onClose={() => undefined}>
          amount
        </Sheet>
      </>,
    );
    rerender(
      <>
        <Sheet open={false} label="New product" onClose={() => undefined}>
          form
        </Sheet>
        <Sheet open label="Kefir" onClose={() => undefined}>
          amount
        </Sheet>
      </>,
    );
    expect(screen.getByRole('dialog', { name: 'Kefir' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'New product' })).not.toBeInTheDocument();
    expect(document.documentElement).toHaveClass('presenting');
  });

  describe('docked on a wide screen', () => {
    const original = window.matchMedia;
    beforeEach(() => {
      // Every min-width query matches: a 1440 px desktop.
      window.matchMedia = ((query: string) => ({
        matches: query.includes('min-width'),
        media: query,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia;
    });
    afterEach(() => {
      window.matchMedia = original;
    });

    it('is a named, non-modal inspector with no scrim and no recede', () => {
      const onClose = vi.fn();
      render(<SheetHarness initial dock onClose={onClose} />);
      const dialog = screen.getByRole('dialog', { name: 'Baked Beans in Tomato Sauce' });
      expect(dialog).toHaveClass('is-docked');
      expect(dialog).not.toHaveAttribute('aria-modal');
      expect(document.querySelector('.scrim')).toBeNull();
      expect(document.documentElement).not.toHaveClass('presenting');
      fireEvent.keyDown(dialog, { key: 'Escape' });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('is a centred modal dialog without `dock`', () => {
      render(<SheetHarness initial />);
      const dialog = screen.getByRole('dialog', { name: 'Baked Beans in Tomato Sauce' });
      expect(dialog).toHaveClass('is-center');
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(document.documentElement).toHaveClass('presenting');
    });
  });
});

// -------------------------------------------------------------------- AmountStepper

function Stepper({
  initial = '100',
  step = 10,
  max,
  units,
  onChange,
}: {
  initial?: string;
  step?: number;
  max?: number;
  units?: string[];
  onChange?: (v: string) => void;
}) {
  const [value, setValue] = useState(initial);
  const [unit, setUnit] = useState(units?.[0] ?? 'g');
  return (
    <AmountStepper
      value={value}
      onChange={(v) => {
        setValue(v);
        onChange?.(v);
      }}
      unit={unit}
      units={units}
      onUnitChange={setUnit}
      step={step}
      max={max}
      label="Grams"
    />
  );
}

describe('AmountStepper', () => {
  it('keeps the field named as before and steps with − and +', () => {
    withI18n(<Stepper />);
    const field = screen.getByLabelText('Grams') as HTMLInputElement;
    expect(field.value).toBe('100');
    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    expect(field.value).toBe('110');
    fireEvent.click(screen.getByRole('button', { name: 'Less' }));
    fireEvent.click(screen.getByRole('button', { name: 'Less' }));
    expect(field.value).toBe('90');
  });

  it('snaps a typed amount to the step', () => {
    withI18n(<Stepper initial="107" />);
    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    expect((screen.getByLabelText('Grams') as HTMLInputElement).value).toBe('110');
    fireEvent.change(screen.getByLabelText('Grams'), { target: { value: '107' } });
    fireEvent.click(screen.getByRole('button', { name: 'Less' }));
    expect((screen.getByLabelText('Grams') as HTMLInputElement).value).toBe('100');
  });

  it('stops at its ends: − at one step, + at the maximum', () => {
    withI18n(<Stepper initial="10" max={5000} />);
    expect(screen.getByRole('button', { name: 'Less' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Grams'), { target: { value: '4995' } });
    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    expect((screen.getByLabelText('Grams') as HTMLInputElement).value).toBe('5000');
    expect(screen.getByRole('button', { name: 'More' })).toBeDisabled();
  });

  it('steps with the arrow keys in the field', () => {
    const onChange = vi.fn();
    withI18n(<Stepper onChange={onChange} />);
    fireEvent.keyDown(screen.getByLabelText('Grams'), { key: 'ArrowUp' });
    expect(onChange).toHaveBeenLastCalledWith('110');
    fireEvent.keyDown(screen.getByLabelText('Grams'), { key: 'ArrowDown' });
    expect(onChange).toHaveBeenLastCalledWith('100');
  });

  it('keeps the decimal comma the person typed', () => {
    withI18n(<Stepper initial="1,25" step={0.5} />, 'uk');
    fireEvent.click(screen.getByRole('button', { name: 'Більше' }));
    expect((screen.getByLabelText('Grams') as HTMLInputElement).value).toBe('1,5');
    fireEvent.change(screen.getByLabelText('Grams'), { target: { value: '2.5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Менше' }));
    expect((screen.getByLabelText('Grams') as HTMLInputElement).value).toBe('2');
  });

  it('cycles the units when there is more than one', () => {
    withI18n(<Stepper units={['g', 'portions']} />);
    const switcher = screen.getByRole('button', { name: 'Change unit' });
    expect(switcher).toHaveTextContent('g');
    fireEvent.click(switcher);
    expect(screen.getByRole('button', { name: 'Change unit' })).toHaveTextContent('portions');
    fireEvent.click(screen.getByRole('button', { name: 'Change unit' }));
    expect(screen.getByRole('button', { name: 'Change unit' })).toHaveTextContent('g');
    // Only the field answers to its name.
    expect(screen.getAllByLabelText(/grams/i)).toHaveLength(1);
  });

  it('shows a single unit as plain text', () => {
    withI18n(<Stepper />);
    expect(screen.queryByRole('button', { name: /Change unit/ })).not.toBeInTheDocument();
    expect(screen.getByText('g')).toBeInTheDocument();
  });
});

// ------------------------------------------------------------------------ ChipGroup

describe('ChipGroup', () => {
  const options = [
    { value: 100, label: '100 g' },
    { value: 207, label: '1 portion · 207 g' },
    { value: 414, label: '2 portions · 414 g' },
  ];

  it('marks the chosen chip pressed and reports taps', () => {
    const onChange = vi.fn();
    render(<ChipGroup label="Amount" options={options} value={207} onChange={onChange} />);
    const group = screen.getByRole('group', { name: 'Amount' });
    expect(within(group).getByRole('button', { name: '1 portion · 207 g' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(group).getByRole('button', { name: '100 g' })).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(within(group).getByRole('button', { name: '2 portions · 414 g' }));
    expect(onChange).toHaveBeenCalledWith(414);
  });

  it('has no pressed state for action chips', () => {
    render(<ChipGroup label="Quick messages" options={[{ value: 'water', label: 'Drink some water 💧' }]} onChange={() => undefined} />);
    expect(screen.getByRole('button', { name: 'Drink some water 💧' })).not.toHaveAttribute('aria-pressed');
  });
});

// ---------------------------------------------------------- Notice, EmptyState, offline

describe('Notice', () => {
  it('announces an error with its title and detail in separate elements', () => {
    render(<Notice tone="error" title="Could not reach Open Food Facts." detail="HTTP 503" />);
    const alert = screen.getByRole('alert');
    expect(within(alert).getByText('Could not reach Open Food Facts.')).toBeInTheDocument();
    expect(within(alert).getByText('HTTP 503')).toBeInTheDocument();
    expect(screen.getByText('Could not reach Open Food Facts.')).not.toBe(screen.getByText('HTTP 503'));
  });

  it('is quiet for a warning and shows its action', () => {
    render(<Notice tone="warning" title="Type at least 2 letters." action={<button type="button">Try again</button>} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

describe('EmptyState', () => {
  it('shows a heading, the body and the action', () => {
    render(<EmptyState icon="search" title="Nothing yet" body="Search by name." action={<a href="/x">Start</a>} />);
    expect(screen.getByRole('heading', { name: 'Nothing yet' })).toBeInTheDocument();
    expect(screen.getByText('Search by name.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Start' })).toBeInTheDocument();
  });
});

describe('OfflineBanner', () => {
  afterEach(() => {
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, get: () => true });
  });

  it('appears while offline and goes when the connection is back', () => {
    withI18n(<OfflineBanner />);
    expect(screen.queryByText("You're offline. Showing what was already loaded.")).not.toBeInTheDocument();
    act(() => {
      Object.defineProperty(window.navigator, 'onLine', { configurable: true, get: () => false });
      window.dispatchEvent(new Event('offline'));
    });
    expect(screen.getByRole('status')).toHaveTextContent("You're offline. Showing what was already loaded.");
    act(() => {
      Object.defineProperty(window.navigator, 'onLine', { configurable: true, get: () => true });
      window.dispatchEvent(new Event('online'));
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
