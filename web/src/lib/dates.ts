/**
 * Day arithmetic shared with the phone. The phone keys every table by
 * `epochDay` = days since 1970-01-01 in the user's local calendar
 * (java.time.LocalDate.toEpochDay). The website must compute the same number
 * from the same local date, never from UTC, or a meal logged after 22:00 in
 * Kyiv would show under the wrong day.
 */
import { dateFormat } from './dateFormat';

const MS_PER_DAY = 86_400_000;

/** Local calendar date -> epoch day, matching LocalDate.toEpochDay(). */
export function toEpochDay(date: Date): number {
  const utcMidnight = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  return Math.floor(utcMidnight / MS_PER_DAY);
}

/** Epoch day -> a local Date at midnight. */
export function fromEpochDay(epochDay: number): Date {
  const utc = new Date(epochDay * MS_PER_DAY);
  return new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate());
}

export function todayEpochDay(now: Date = new Date()): number {
  return toEpochDay(now);
}

/** "Mon 28 Sep" in the given locale. */
export function formatDay(epochDay: number, locale: string): string {
  return dateFormat(locale, { weekday: 'short', day: 'numeric', month: 'short' }).format(fromEpochDay(epochDay));
}

/** "13:05" in the given locale, from an ISO timestamp. */
export function formatTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
}

/** The epoch days of the last [count] days, oldest first, ending today. */
export function lastDays(count: number, today: number): number[] {
  return Array.from({ length: count }, (_, i) => today - (count - 1) + i);
}
