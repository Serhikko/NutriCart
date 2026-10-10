import { fromEpochDay } from './dates';
import { dateFormat } from './dateFormat';
import { onePerDay, type DaySummary, type WeightEntry } from './diary';
import { ADHERENCE_TOLERANCE, dayState, streak, type DayState } from '../domain/habits';
import { weekdaySeparator } from './i18n';

/**
 * The pure arithmetic behind the Week pages (My week and a partner's week):
 * one row per day, the daily average, days on plan, the chart's scale, and the
 * 30-day weight series. No React and no strings here; the pages and charts
 * turn these numbers into words with the page language.
 */

/** One day of the week strip and the calories chart. */
export interface WeekDay {
  epochDay: number;
  /** Whole kcal eaten that day (0 when nothing was logged). */
  eaten: number;
  /** That day's target, or null when nobody published one (a partner day without a summary). */
  target: number | null;
  /** EMPTY / GOOD / OVER, the phone's AdherenceCalculator. */
  state: DayState;
  isToday: boolean;
}

interface WeekSource {
  days: number[];
  summaries: DaySummary[];
  entries: { epoch_day: number; kcal: number }[];
}

/**
 * One row per day. Whose number wins differs on purpose and is kept from the
 * pages as they were: on your own week the diary entries are counted first
 * (this browser may be ahead of the published summary), on a partner's week
 * the published summary is (their phone is the source of truth).
 */
export function buildWeekRows(
  week: WeekSource,
  today: number,
  { fallbackTarget = null, preferSummary = false }: { fallbackTarget?: number | null; preferSummary?: boolean } = {},
): WeekDay[] {
  const eatenByDay = new Map<number, number>();
  for (const e of week.entries) eatenByDay.set(e.epoch_day, (eatenByDay.get(e.epoch_day) ?? 0) + e.kcal);
  const summaryByDay = new Map(week.summaries.map((s) => [s.epoch_day, s]));
  return week.days.map((d) => {
    const summary = summaryByDay.get(d);
    const fromEntries = eatenByDay.get(d);
    const raw = preferSummary ? (summary?.eaten_kcal ?? fromEntries ?? 0) : (fromEntries ?? summary?.eaten_kcal ?? 0);
    const eaten = Math.round(raw);
    const target = summary?.target_kcal ?? fallbackTarget;
    return { epochDay: d, eaten, target, state: dayState(eaten, target ?? 0), isToday: d === today };
  });
}

export interface WeekStats {
  /**
   * The daily average of the completed days that have something logged
   * (today is still going, so it would pull the average down); today counts
   * only when it is the one day logged. Null when nothing is logged at all.
   */
  average: number | null;
  /** Days on plan, and days with a verdict at all (logged against a target). */
  good: number;
  logged: number;
  /** The goal the hero quotes and the chart's goal line marks: today's, else the latest day's that has one. */
  goal: number | null;
  /** Days in a row with something logged, ending today (or yesterday while today is empty). */
  streak: number;
  /** Anything logged in these seven days. */
  anyLogged: boolean;
}

export function weekStats(rows: WeekDay[], today: number): WeekStats {
  const loggedRows = rows.filter((r) => r.eaten > 0);
  const completed = loggedRows.filter((r) => !r.isToday);
  const basis = completed.length > 0 ? completed : loggedRows;
  const average = basis.length > 0 ? Math.round(basis.reduce((sum, r) => sum + r.eaten, 0) / basis.length) : null;
  const judged = rows.filter((r) => r.state !== 'EMPTY');
  const goal = [...rows].reverse().find((r) => r.target != null && r.target > 0)?.target ?? null;
  return {
    average,
    good: judged.filter((r) => r.state === 'GOOD').length,
    logged: judged.length,
    goal,
    streak: streak(new Set(loggedRows.map((r) => r.epochDay)), today),
    anyLogged: loggedRows.length > 0,
  };
}

/** How a day stands against its goal: under it, a little over but on plan (within 5%), or over. */
export type Verdict = 'under' | 'within' | 'over';

export function verdict(eaten: number, target: number): Verdict {
  const diff = eaten - target;
  if (diff > target * ADHERENCE_TOLERANCE) return 'over';
  return diff > 0 ? 'within' : 'under';
}

/** The marker a day gets in the strip: today is always its own mini ring. */
export type MarkerState = 'GOOD' | 'OVER' | 'TODAY' | 'EMPTY';

export function markerState(row: WeekDay): MarkerState {
  if (row.isToday) return 'TODAY';
  return row.state;
}

/**
 * The top of the calories chart: room above the goal for its pill (24%), and
 * for the tallest day with a little headroom so an over cap never touches the top.
 */
export function kcalChartMax(rows: WeekDay[], goal: number | null): number {
  const tallest = Math.max(0, ...rows.map((r) => r.eaten));
  const goals = rows.map((r) => r.target ?? 0).concat(goal ?? 0);
  const highestGoal = Math.max(0, ...goals);
  return Math.max(highestGoal * 1.24, tallest * 1.04, 1000);
}

/**
 * The chart's gridlines: 0 and 1,000 kcal only, and the 1,000 line goes when
 * the goal sits so close to it that the goal pill and the tick would collide.
 */
export function kcalGridValues(max: number, goal: number | null): number[] {
  if (goal != null && Math.abs(goal - 1000) < max * 0.1) return [0];
  return [0, 1000];
}

export interface WeightPoint {
  epochDay: number;
  kg: number;
}

export interface WeightSeries {
  /** One weight per day (a manual entry wins over a synced one), oldest first, inside the window. */
  points: WeightPoint[];
  /** The window shown: the last 30 days, ending today. */
  from: number;
  to: number;
  latest: WeightPoint | null;
  /** Latest minus the first weight in the window, to 0.1 kg; null with fewer than two weights. */
  delta: number | null;
  /** The value axis, with room round the line. */
  lo: number;
  hi: number;
  /** One or two dashed guides at whole kilograms (half kilograms when the line is very flat). */
  guides: number[];
}

const round1 = (n: number) => Math.round(n * 10) / 10;

export function weightSeries(weights: WeightEntry[], today: number, days = 30): WeightSeries {
  const from = today - days;
  const points = onePerDay(weights)
    .filter((w) => w.epoch_day >= from && w.epoch_day <= today)
    .map((w) => ({ epochDay: w.epoch_day, kg: w.weight_kg }));
  const latest = points.at(-1) ?? null;
  const delta = points.length >= 2 ? round1(points[points.length - 1].kg - points[0].kg) : null;

  const values = points.map((p) => p.kg);
  const min = values.length ? Math.min(...values) : 0;
  const max = values.length ? Math.max(...values) : 0;
  const pad = Math.max(0.3, (max - min) * 0.15);
  const lo = min - pad;
  const hi = max + pad;
  return { points, from, to: today, latest, delta, lo, hi, guides: weightGuides(lo, hi) };
}

/** Whole kilograms inside the axis, at most two (the outermost, so they frame the line). */
export function weightGuides(lo: number, hi: number): number[] {
  const whole: number[] = [];
  for (let v = Math.ceil(lo); v <= Math.floor(hi); v += 1) whole.push(v);
  if (whole.length === 0) {
    const half = Math.ceil(lo * 2) / 2;
    return half <= hi ? [half] : [];
  }
  if (whole.length <= 2) return whole;
  if (whole.length === 3) return [whole[0], whole[2]];
  // Many kilograms on the axis: two guides, about a third in from each edge.
  const step = Math.max(1, Math.floor((whole.length - 1) / 3));
  return [whole[step], whole[whole.length - 1 - step]];
}

/* ------------------------------------------------------------------ words */

/** First letter up: Ukrainian, Belarusian and Russian weekday and month names are lower case mid-sentence. */
export const cap1 = (s: string) => (s ? s[0].toLocaleUpperCase() + s.slice(1) : s);

/** "Sat", "сб": Intl's short weekday alone (never a formatted date split at a space, which keeps the comma). */
export function shortWeekday(epochDay: number, tag: string): string {
  return cap1(dateFormat(tag, { weekday: 'short' }).format(fromEpochDay(epochDay))).replace(/[.,]$/, '');
}

/** "Tuesday", "Вівторок": the name a day marker is read by. */
export function longWeekday(epochDay: number, tag: string): string {
  return cap1(dateFormat(tag, { weekday: 'long' }).format(fromEpochDay(epochDay)));
}

/**
 * "Tuesday 6 Oct", "Вівторок, 6 жовт.", "Вторник, 6 окт.": the chart header for
 * a past day and the tables' row names. Two Intl calls, as dayView's longDate:
 * formatted together with a day and month, Chromium declines the Ukrainian
 * weekday ("пʼятницю"), and a heading needs the nominative. The comma the
 * Slavic languages write after the weekday is put back (weekdaySeparator).
 */
export function dayLabel(epochDay: number, tag: string): string {
  const date = fromEpochDay(epochDay);
  const weekday = dateFormat(tag, { weekday: 'long' }).format(date);
  const dayMonth = dateFormat(tag, { day: 'numeric', month: 'short' }).format(date);
  return cap1(`${weekday}${weekdaySeparator(tag)}${dayMonth}`);
}

/** "9 Sep", "9 вер.": the weight chart's axis dates (day and month only, no weekday). */
export function axisDate(epochDay: number, tag: string): string {
  return dateFormat(tag, { day: 'numeric', month: 'short' }).format(fromEpochDay(epochDay));
}

/** "3–9 October", "28 September – 4 October", "3–9 жовтня": the week's eyebrow. */
export function weekRange(first: number, last: number, tag: string): string {
  return dateFormat(tag, { day: 'numeric', month: 'long' }).formatRange(fromEpochDay(first), fromEpochDay(last));
}
