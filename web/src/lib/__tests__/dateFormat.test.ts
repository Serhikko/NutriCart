import { describe, expect, it } from 'vitest';
import { BORROWED_BE_TAG, belarusianDates, dateFormat } from '../dateFormat';

// Node ships full ICU, so Intl('be-BY') here is CLDR's Belarusian: the
// reference the hand-written names must match on every day of a year.
const reference = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('be-BY', options);

const SHAPES: Intl.DateTimeFormatOptions[] = [
  { weekday: 'short' },
  { weekday: 'long' },
  { day: 'numeric', month: 'short' },
  { day: 'numeric', month: 'long' },
  { weekday: 'short', day: 'numeric', month: 'short' },
  { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' },
];

const everyDayOf2026 = Array.from({ length: 365 }, (_, i) => new Date(2026, 0, 1 + i, 9, 5));

describe('dateFormat: Belarusian where the browser has no Belarusian dates', () => {
  it.each(SHAPES.map((s) => [JSON.stringify(s), s] as const))('%s matches CLDR on every day of 2026', (_, shape) => {
    const ours = belarusianDates(shape);
    const cldr = reference(shape);
    for (const day of everyDayOf2026) expect(ours.format(day)).toBe(cldr.format(day));
  });

  it('writes week ranges as CLDR does, within a month, across months and across years', () => {
    const shape: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long' };
    const ours = belarusianDates(shape);
    const cldr = reference(shape);
    const pairs: [Date, Date][] = [
      [new Date(2026, 9, 4), new Date(2026, 9, 10)],
      [new Date(2026, 8, 28), new Date(2026, 9, 4)],
      [new Date(2026, 11, 28), new Date(2027, 0, 3)],
    ];
    for (const [a, b] of pairs) expect(ours.formatRange(a, b)).toBe(cldr.formatRange(a, b));
    expect(ours.formatRange(new Date(2026, 9, 4), new Date(2026, 9, 10))).toBe('4–10 кастрычніка');
  });

  it('is what the borrowed tag formats with; every other tag is plain Intl', () => {
    const friday = new Date(2026, 9, 9);
    expect(dateFormat(BORROWED_BE_TAG, { day: 'numeric', month: 'long' }).format(friday)).toBe('9 кастрычніка');
    expect(dateFormat('ru-RU', { day: 'numeric', month: 'long' }).format(friday)).toBe('9 октября');
    expect(dateFormat('en-GB', { weekday: 'long' }).format(friday)).toBe('Friday');
  });

  it('never guesses a shape it does not know: those keep the borrowed tag', () => {
    const withYear = belarusianDates({ day: 'numeric', month: 'long', year: 'numeric' });
    expect(withYear.format(new Date(2026, 9, 9))).toBe(new Intl.DateTimeFormat(BORROWED_BE_TAG, { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(2026, 9, 9)));
  });
});
