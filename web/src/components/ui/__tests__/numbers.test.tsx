import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { I18nProvider, type Locale } from '../../../lib/i18n';
import { Ring } from '../Ring';
import { Digits } from '../Digits';
import { Bar } from '../Bar';
import { DayMarker } from '../DayMarker';
import { Skeleton } from '../Skeleton';

// jsdom has no Web Animations, matchMedia, ResizeObserver or @property: everything here
// must render complete and correct without them.

function inLocale(locale: Locale, ui: ReactNode) {
  localStorage.setItem('nutricart.locale', locale);
  return render(<I18nProvider>{ui}</I18nProvider>);
}

/** What the eye sees: the glyphs CSS draws in the aria-hidden cells, without digits on their way out. */
function shown(root: Element): string {
  const cells = root.querySelector('.d-cells');
  if (!cells) return '';
  return [...cells.children]
    .map((c) => (c.classList.contains('dc') ? c.querySelector<HTMLElement>('i:not(.out)')?.dataset.d : (c as HTMLElement).dataset.d) ?? '')
    .join('');
}

describe('Ring', () => {
  it('is one picture named by the day line, with the SVG hidden from screen readers', () => {
    render(
      <Ring value={1230} target={2100} size={252} stroke={26} label="1,230 / 2,100 kcal, 870 left">
        <span>870</span>
      </Ring>,
    );
    const img = screen.getByRole('img', { name: '1,230 / 2,100 kcal, 870 left' });
    const svg = img.querySelector('svg.ring')!;
    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(svg.getAttribute('style')).toContain('--p: 0.5857');
    // Track, the two halves, the start cap, the end cap in both gradients: no over lap at 59%.
    expect(svg.querySelectorAll('.track, .arc.a, .arc.b, .cap.start, .cap.ea, .cap.eb')).toHaveLength(6);
    expect(svg.querySelector('.arc.c')).toBeNull();
    // The centre content is part of the picture, not read on its own.
    expect(within(img).getByText('870')).toBeInTheDocument();
  });

  it('without a label it is only a hidden drawing, for a wrapper that carries the name', () => {
    const { container } = render(<Ring value={10} target={20} size={36} stroke={5} />);
    expect(screen.queryByRole('img')).toBeNull();
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('draws the second lap in ink past the goal, and the "this food" arc in the sheet preview', () => {
    const { container, rerender } = render(<Ring value={2390} target={2100} size={252} stroke={26} />);
    expect(container.querySelector('.arc.c')).not.toBeNull();
    rerender(<Ring value={1230} target={2100} size={88} stroke={11} add={161} muted className="preview" />);
    const svg = container.querySelector('svg')!;
    expect(svg.querySelector('.add-arc')).not.toBeNull();
    expect(svg.getAttribute('style')).toContain('--g: 0.0767');
    expect(svg.querySelector('.today')).toHaveClass('ring-muted');
  });

  it('places the end cap at its final position for browsers without CSS trig', () => {
    const { container } = render(<Ring value={1} target={4} size={100} stroke={10} />);
    // A quarter of the way round: 3 o'clock.
    const cap = container.querySelector('.cap.ea')!;
    expect(Number(cap.getAttribute('cx'))).toBeCloseTo(95);
    expect(Number(cap.getAttribute('cy'))).toBeCloseTo(50);
  });

  it('takes a new value on a mounted ring without needing animation support', () => {
    const { container, rerender } = render(<Ring value={1230} target={2100} size={252} stroke={26} sweep />);
    rerender(<Ring value={1391} target={2100} size={252} stroke={26} sweep />);
    const svg = container.querySelector('svg')!;
    expect(svg.getAttribute('style')).toContain('--p: 0.6624');
    // No @property support in jsdom: the ring stands at its value instead of animating.
    expect(svg.getAttribute('class')).toBe('ring');
  });

  it('copes with a missing target', () => {
    const { container } = render(<Ring value={500} target={0} size={64} stroke={9} />);
    expect(container.querySelector('svg')!.getAttribute('style')).toContain('--p: 0');
  });
});

describe('Digits', () => {
  afterEach(() => localStorage.clear());

  it('shows the final value, formatted for the page language, read once', () => {
    const { container } = inLocale('en', <Digits value={1230} />);
    const root = container.querySelector('.digits')!;
    expect(within(root as HTMLElement).getByText('1,230')).toHaveClass('sr');
    expect(root.querySelector('.d-cells')).toHaveAttribute('aria-hidden', 'true');
    expect(shown(root)).toBe('1,230');
    expect(root.querySelectorAll('.dc')).toHaveLength(4);
    expect(root.querySelectorAll('.dsep')).toHaveLength(1);
    // The value is the number's only text: it never reads, copies or matches twice.
    expect(root.textContent).toBe('1,230');
  });

  it('uses the Ukrainian group separator in Ukrainian', () => {
    const { container } = inLocale('uk', <Digits value={1230} />);
    expect(shown(container.querySelector('.digits')!)).toBe('1 230');
  });

  it('groups like the page language outside a provider, English for a language the site lacks', () => {
    const lang = document.documentElement.lang;
    try {
      for (const [pageLang, tag] of [['ru', 'ru-RU'], ['be', 'be-BY'], ['uk', 'uk-UA'], ['de', 'en-GB'], ['', 'en-GB']] as const) {
        document.documentElement.lang = pageLang;
        const { container, unmount } = render(<Digits value={12300} />);
        expect(shown(container.querySelector('.digits')!)).toBe(new Intl.NumberFormat(tag).format(12300));
        unmount();
      }
    } finally {
      document.documentElement.lang = lang;
    }
  });

  it('takes a custom format (one decimal for kilograms)', () => {
    const nf = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const { container } = inLocale('en', <Digits value={64.2} format={(n) => nf.format(n)} />);
    expect(shown(container.querySelector('.digits')!)).toBe('64.2');
  });

  it('staggers the entrance digit by digit on the first open', () => {
    const { container } = inLocale('en', <Digits value={870} enter delay={200} step={60} />);
    const digits = [...container.querySelectorAll('.dc > i')];
    expect(digits.every((d) => d.classList.contains('a-digit'))).toBe(true);
    expect(digits.map((d) => (d as HTMLElement).style.getPropertyValue('--d'))).toEqual(['200', '260', '320']);
  });

  it('lands on each new value without animation support (no stacked or leftover digits)', () => {
    const { container, rerender } = inLocale('en', <Digits value={870} />);
    const root = container.querySelector('.digits')!;
    rerender(
      <I18nProvider>
        <Digits value={709} />
      </I18nProvider>,
    );
    expect(shown(root)).toBe('709');
    expect(root.querySelector('.sr')).toHaveTextContent('709');
    expect(root.querySelectorAll('i.out')).toHaveLength(0);
    // Another digit count re-enters the whole number.
    rerender(
      <I18nProvider>
        <Digits value={1391} />
      </I18nProvider>,
    );
    expect(shown(container.querySelector('.digits')!)).toBe('1,391');
    // The separator arrives with its digits, never on its own.
    expect(container.querySelectorAll('.dc > i.a-digit, .dsep.a-digit')).toHaveLength(5);
  });

  it('marks gradient numerals', () => {
    const { container } = inLocale('en', <Digits value={1918} gradient />);
    expect(container.querySelector('.digits')).toHaveClass('grad-text');
  });

  it('starts from `from` and shows `value` as its final text', () => {
    const { container } = inLocale('en', <Digits value={709} from={870} delay={240} />);
    expect(shown(container.querySelector('.digits')!)).toBe('709');
    expect(screen.getByText('709')).toHaveClass('sr');
  });
});

describe('Digits hand-off with Web Animations', () => {
  const calls: { el: HTMLElement; frames: Keyframe[]; options: KeyframeAnimationOptions }[] = [];

  beforeEach(() => {
    calls.length = 0;
    // A stand-in for Element.animate: records each call; finished never settles, so a
    // leaving digit stays in the DOM where the test can see it.
    Object.defineProperty(Element.prototype, 'animate', {
      configurable: true,
      writable: true,
      value(this: HTMLElement, frames: Keyframe[], options: KeyframeAnimationOptions) {
        calls.push({ el: this, frames, options });
        return { cancel: vi.fn(), playState: 'running', finished: new Promise(() => undefined) } as unknown as Animation;
      },
    });
  });
  afterEach(() => {
    delete (Element.prototype as { animate?: unknown }).animate;
    localStorage.clear();
  });

  function change(from: number, to: number) {
    const view = inLocale('en', <Digits value={from} changeDelay={240} />);
    view.rerender(
      <I18nProvider>
        <Digits value={to} changeDelay={240} />
      </I18nProvider>,
    );
    return view.container.querySelector('.digits')!;
  }

  it('hands off only the digits that changed, old ones out and new ones in, never a strip', () => {
    const root = change(1230, 1391);
    // "1" stays; 2→3, 3→9, 0→1 hand off: one leaving copy per changed cell, holding the old digit.
    const outs = [...root.querySelectorAll<HTMLElement>('i.out')].map((o) => o.dataset.d);
    expect(outs).toEqual(['2', '3', '0']);
    expect(shown(root)).toBe('1,391');
    expect(calls).toHaveLength(6);
    // Staggered 40 ms by digit position after the change delay.
    expect(calls.filter((c) => c.el.classList.contains('out')).map((c) => c.options.delay)).toEqual([280, 320, 360]);
    // Up for an increase: the new digit comes from below, the old one leaves upwards.
    const arriving = calls.find((c) => !c.el.classList.contains('out'))!;
    expect(arriving.frames[0].transform).toBe('translateY(0.5em)');
  });

  it('hands off from `from` after `delay` when it mounts (back from Add)', () => {
    const { container } = inLocale('en', <Digits value={709} from={870} delay={240} />);
    const root = container.querySelector('.digits')!;
    expect([...root.querySelectorAll<HTMLElement>('i.out')].map((o) => o.dataset.d)).toEqual(['8', '7', '0']);
    expect(shown(root)).toBe('709');
    expect(calls.filter((c) => c.el.classList.contains('out')).map((c) => c.options.delay)).toEqual([240, 280, 320]);
  });

  it('starts later changes after changeDelay, not the entrance delay', () => {
    const view = inLocale('en', <Digits value={16} enter delay={340} />);
    view.rerender(
      <I18nProvider>
        <Digits value={17} enter delay={340} />
      </I18nProvider>,
    );
    expect(calls.map((c) => c.options.delay)).toEqual([40, 40]);
  });

  it('moves down for a decrease', () => {
    change(870, 709);
    const leaving = calls.find((c) => c.el.classList.contains('out'))!;
    expect(leaving.frames[1].transform).toBe('translateY(0.4em)');
  });

  it('never leaves two old digits in one cell when changes come fast', () => {
    const view = inLocale('en', <Digits value={207} />);
    for (const v of [208, 209, 210]) {
      view.rerender(
        <I18nProvider>
          <Digits value={v} />
        </I18nProvider>,
      );
    }
    const root = view.container.querySelector('.digits')!;
    for (const cell of root.querySelectorAll('.dc')) expect(cell.querySelectorAll('i.out').length).toBeLessThanOrEqual(1);
    expect(shown(root)).toBe('210');
  });

  it('swaps at once while changes come faster than a hand-off (a held stepper), then hands off again', () => {
    let clock = 1000;
    const now = vi.spyOn(performance, 'now').mockImplementation(() => clock);
    try {
      const view = inLocale('en', <Digits value={172} />);
      const to = (v: number) =>
        view.rerender(
          <I18nProvider>
            <Digits value={v} />
          </I18nProvider>,
        );
      const root = view.container.querySelector('.digits')!;
      to(179); // on its own: 2 -> 9 hands off
      expect(calls).toHaveLength(2);
      // The stepper repeats every 85 ms: each step shows its own value at once, with no
      // digit left on its way out and no hand-off that would hide the new one.
      for (const v of [187, 194, 201]) {
        clock += 85;
        to(v);
        expect(shown(root)).toBe(String(v));
        expect(root.querySelectorAll('i.out')).toHaveLength(0);
      }
      expect(calls).toHaveLength(2);
      // Released: the next change on its own hands off again (1 -> 8).
      clock += 600;
      to(208);
      expect(calls).toHaveLength(4);
      // One still in flight (a long change delay) also makes the next change swap at once.
      clock += 600;
      to(215);
      expect(calls).toHaveLength(4);
      expect(shown(root)).toBe('215');
    } finally {
      now.mockRestore();
    }
  });
});

describe('Bar', () => {
  it('is a meter named for its metric, its value kept within range', () => {
    render(<Bar value={130} max={115} metric="protein" label="Protein" valueText="130 g of 115 g" />);
    const meter = screen.getByRole('meter', { name: 'Protein' });
    expect(meter).toHaveAttribute('aria-valuenow', '115');
    expect(meter).toHaveAttribute('aria-valuemax', '115');
    expect(meter).toHaveAttribute('aria-valuetext', '130 g of 115 g');
    expect(meter).toHaveClass('m-protein');
    expect(meter.querySelector('i')!.style.getPropertyValue('--w')).toBe('1');
  });

  it('fills in proportion', () => {
    render(<Bar value={51} max={115} metric="carbs" label="Carbs" delay={420} />);
    const meter = screen.getByRole('meter', { name: 'Carbs' });
    expect(meter.querySelector('i')!.style.getPropertyValue('--w')).toBe('0.4435');
    expect(meter.style.getPropertyValue('--d')).toBe('420');
  });
});

describe('DayMarker', () => {
  it('names each marker with its sentence and shows one state each', () => {
    render(
      <>
        <DayMarker state="GOOD" label="Tue" name="Tuesday: 2,060 kcal, on plan" />
        <DayMarker state="OVER" label="Sat" name="Saturday: 2,290 kcal, over goal" />
        <DayMarker state="TODAY" progress={0.58} label="Today" name="Friday: 1,230 kcal so far" />
        <DayMarker state="EMPTY" label="Mon" name="Monday: nothing logged" />
      </>,
    );
    const good = screen.getByRole('img', { name: 'Tuesday: 2,060 kcal, on plan' });
    expect(good).toHaveClass('dm', 'good');
    expect(good.querySelector('.icon')).not.toBeNull();
    expect(within(good).getByText('Tue')).toHaveClass('lbl');
    expect(screen.getByRole('img', { name: 'Saturday: 2,290 kcal, over goal' })).toHaveClass('over');
    const today = screen.getByRole('img', { name: 'Friday: 1,230 kcal so far' });
    expect(today).toHaveClass('today');
    expect(today.querySelector('svg.ring.mini')).not.toBeNull();
    const empty = screen.getByRole('img', { name: 'Monday: nothing logged' });
    expect(empty).toHaveClass('unlogged');
    expect(empty).not.toHaveClass('empty'); // EmptyState's class
    expect(empty.querySelector('.dm-empty')).not.toBeNull();
  });

  it('shrinks for the mini week card', () => {
    render(<DayMarker state="TODAY" progress={0.5} label="Today" name="Friday: 900 kcal so far" size={30} delay={260} />);
    const marker = screen.getByRole('img', { name: 'Friday: 900 kcal so far' });
    expect(marker).toHaveClass('dm-small');
    const disc = marker.querySelector<HTMLElement>('.disc')!;
    expect(disc.style.getPropertyValue('--dm-size')).toBe('30px');
    expect(disc.querySelector('svg.ring')).toHaveAttribute('width', '30');
  });
});

describe('Skeleton', () => {
  it('draws hidden shapes and says "Loading…" once when labelled', () => {
    const { container } = inLocale(
      'en',
      <>
        <Skeleton variant="ring" label="Loading…" />
        <Skeleton width="60%" />
        <Skeleton variant="row" />
      </>,
    );
    expect(screen.getAllByText('Loading…')).toHaveLength(1);
    expect(screen.getByRole('status')).toHaveTextContent('Loading…');
    const shapes = container.querySelectorAll('.skel');
    expect(shapes).toHaveLength(3);
    for (const s of shapes) expect(s).toHaveAttribute('aria-hidden', 'true');
    expect((shapes[1] as HTMLElement).style.getPropertyValue('--sk-w')).toBe('60%');
    expect(shapes[2].querySelector('.sk-glyph')).not.toBeNull();
  });
});
