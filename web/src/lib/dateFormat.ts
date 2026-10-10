/**
 * Dates in the page language, for every site language, including Belarusian
 * where the browser has no Belarusian date data. Chromium (Chrome, Edge, the
 * Android WebView behind most in-app browsers) ships Belarusian plural rules
 * but no Belarusian dates: asked for be-BY it writes "M10 4–10". There the
 * site formats with BORROWED_BE_TAG (Russian as written in Belarus, which has
 * Belarusian's number separators), and this module supplies the Belarusian
 * month and weekday names for the few date shapes the site writes. The names
 * and patterns are CLDR's, the same data Safari and Firefox format with; the
 * tests compare every day of a year against a full-ICU Intl('be-BY').
 */
export const BORROWED_BE_TAG = 'ru-BY';

const WEEKDAYS_SHORT = ['нд', 'пн', 'аў', 'ср', 'чц', 'пт', 'сб'];
const WEEKDAYS_LONG = ['нядзеля', 'панядзелак', 'аўторак', 'серада', 'чацвер', 'пятніца', 'субота'];
const MONTHS_SHORT = ['сту', 'лют', 'сак', 'кра', 'мая', 'чэр', 'ліп', 'жні', 'вер', 'кас', 'ліс', 'сне'];
const MONTHS_LONG = ['студзеня', 'лютага', 'сакавіка', 'красавіка', 'мая', 'чэрвеня', 'ліпеня', 'жніўня', 'верасня', 'кастрычніка', 'лістапада', 'снежня'];

export interface DateFormat {
  format(date: Date): string;
  /** "4–10 October", "28 September – 4 October"; a plain "a – b" where the engine has no formatRange. */
  formatRange(start: Date, end: Date): string;
}

/** Intl.DateTimeFormat for `tag`, or the Belarusian names when `tag` is the borrowed one. */
export function dateFormat(tag: string, options: Intl.DateTimeFormatOptions): DateFormat {
  if (tag === BORROWED_BE_TAG) return belarusianDates(options);
  const fmt = new Intl.DateTimeFormat(tag, options);
  return {
    format: (date) => fmt.format(date),
    formatRange: (start, end) => {
      try {
        return fmt.formatRange(start, end);
      } catch {
        return `${fmt.format(start)} – ${fmt.format(end)}`;
      }
    },
  };
}

const two = (n: number) => String(n).padStart(2, '0');

/**
 * The shapes the site uses, in CLDR's Belarusian patterns: "пт" / "пятніца",
 * "9 кас" / "9 кастрычніка", "пт, 9 кас", "3 сту, 09:05", and ranges. Any
 * other shape falls back to the borrowed tag rather than guessing a pattern.
 */
export function belarusianDates(options: Intl.DateTimeFormatOptions): DateFormat {
  const { weekday, day, month, hour, minute, year } = options;
  const months = month === 'long' ? MONTHS_LONG : month === 'short' ? MONTHS_SHORT : null;
  const known =
    year === undefined &&
    (month === undefined || months !== null) &&
    (weekday === undefined || weekday === 'short' || weekday === 'long') &&
    (hour === undefined) === (minute === undefined) &&
    (day !== undefined || (month === undefined && hour === undefined));
  if (!known) {
    const fallback = new Intl.DateTimeFormat(BORROWED_BE_TAG, options);
    return {
      format: (date) => fallback.format(date),
      formatRange: (start, end) => `${fallback.format(start)} – ${fallback.format(end)}`,
    };
  }
  const dayMonth = (date: Date, withYear = false) =>
    [day !== undefined ? String(date.getDate()) : '', months ? months[date.getMonth()] : '', withYear ? String(date.getFullYear()) : '']
      .filter(Boolean)
      .join(' ');
  const format = (date: Date) => {
    const parts: string[] = [];
    if (weekday) parts.push((weekday === 'long' ? WEEKDAYS_LONG : WEEKDAYS_SHORT)[date.getDay()]);
    const dm = dayMonth(date);
    if (dm) parts.push(dm);
    if (hour !== undefined) parts.push(`${two(date.getHours())}:${two(date.getMinutes())}`);
    return parts.join(', ');
  };
  return {
    format,
    formatRange: (start, end) => {
      // Only the day-and-month shape is ever a range on the site (the week's eyebrow).
      if (weekday || hour !== undefined || day === undefined || !months) return `${format(start)} – ${format(end)}`;
      if (start.getFullYear() !== end.getFullYear()) return `${dayMonth(start, true)} – ${dayMonth(end, true)}`;
      if (start.getMonth() !== end.getMonth()) return `${dayMonth(start)} – ${dayMonth(end)}`;
      if (start.getDate() === end.getDate()) return dayMonth(start);
      return `${start.getDate()}–${end.getDate()} ${months[start.getMonth()]}`;
    },
  };
}
