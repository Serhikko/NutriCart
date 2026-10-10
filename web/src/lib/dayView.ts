import { formatDay, formatTime, fromEpochDay } from './dates';
import { MEAL_SLOTS, dayLine, onePerDay, type DaySummary, type FoodLogEntry, type MealSlot, type WaterEntry, type WeightEntry } from './diary';
import { ADHERENCE_TOLERANCE } from '../domain/habits';
import type { FoodProduct } from '../domain/openFoodFacts';
import { cap1 } from './weekView';

/**
 * The pure arithmetic and wording helpers behind the Day pages (My day and a
 * partner's day): dates in the page language, the "in the zone" band, the
 * macro totals, the moment back from Add food, the rows Undo and Edit amount
 * write, water glasses, the weight trend, when a nudge was sent, and the day
 * line the hero is named by. No React here.
 */

/**
 * "Friday 9 October", "Пʼятниця, 9 жовтня": the page eyebrow. Two Intl calls, so
 * Ukrainian keeps the nominative weekday (one call would decline it).
 */
export function longDate(epochDay: number, tag: string): string {
  const date = fromEpochDay(epochDay);
  const weekday = new Intl.DateTimeFormat(tag, { weekday: 'long' }).format(date);
  const dayMonth = new Intl.DateTimeFormat(tag, { day: 'numeric', month: 'long' }).format(date);
  return cap1(`${weekday}${tag.startsWith('uk') ? ',' : ''} ${dayMonth}`);
}

/** "8 October", "8 жовтня": a day inside a sentence ("Weight on 8 October"). */
export function dayMonth(epochDay: number, tag: string): string {
  return new Intl.DateTimeFormat(tag, { day: 'numeric', month: 'long' }).format(fromEpochDay(epochDay));
}

/** "Thu 8 Oct", "Чт, 8 жовт.": the day navigator's label for a day that is not today (first letter up). */
export function shortDate(epochDay: number, tag: string): string {
  return cap1(formatDay(epochDay, tag));
}

/**
 * When a nudge went out: the time alone for today, the date before the time
 * otherwise ("8 Oct, 21:05"), so a list spanning two days never reads as one.
 */
export function sentAt(iso: string, tag: string, now: Date = new Date()): string {
  const when = new Date(iso);
  const sameDay = when.getFullYear() === now.getFullYear() && when.getMonth() === now.getMonth() && when.getDate() === now.getDate();
  if (sameDay) return formatTime(iso, tag);
  return new Intl.DateTimeFormat(tag, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(when);
}

/* ------------------------------------------------------------ the day line */

type Translate = (key: string, vars?: Record<string, string | number>) => string;

/**
 * "1,230 / 2,100 kcal, 870 left": the phone's number, never recomputed here.
 * The day hero's accessible name (the ring is a picture of it).
 */
export function dayLineText(t: Translate, tag: string, summary: DaySummary | null, eatenFallback: number): string {
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const line = dayLine(summary, eatenFallback);
  const eaten = nf.format(line.eaten);
  if (line.target === null) return t('day.line_no_target', { eaten });
  const target = nf.format(line.target);
  if (line.remaining! >= 0) return t('day.line_target', { eaten, target, remaining: nf.format(line.remaining!) });
  return t('day.line_over', { eaten, target, over: nf.format(-line.remaining!) });
}

/* ------------------------------------------------------------- the zone */

/** Eaten as a share of the target (0 without a target). */
export const progressOf = (eaten: number, target: number | null) => (target && target > 0 ? eaten / target : 0);

/** 90 % to 105 % of the target: the "In the zone" band (the upper edge is the adherence tolerance). */
export function inZone(eaten: number, target: number | null): boolean {
  const p = progressOf(eaten, target);
  return p >= 0.9 && p <= 1 + ADHERENCE_TOLERANCE;
}

/* ------------------------------------------------------------- macros */

export interface MacroTotals {
  protein: number;
  fat: number;
  carbs: number;
}

export function macroTotals(entries: FoodLogEntry[]): MacroTotals {
  const total = { protein: 0, fat: 0, carbs: 0 };
  for (const e of entries) {
    if (e.deleted_at !== null) continue;
    total.protein += e.protein_g || 0;
    total.fat += e.fat_g || 0;
    total.carbs += e.carbs_g || 0;
  }
  return total;
}

/* --------------------------------------------------- back from Add food */

/**
 * What Add food hands the day when it navigates back after logging
 * (location.state.justAdded): the day, the meal, the product's name and its
 * kcal. The day plays its "back from Add" moment once with it.
 */
export interface Arrival {
  epochDay: number;
  slot: MealSlot;
  name: string;
  kcal: number;
}

/** The arrival in a router state, or null when there is none (or it has the wrong shape). */
export function readArrival(state: unknown): Arrival | null {
  if (!state || typeof state !== 'object') return null;
  const raw = (state as { justAdded?: unknown }).justAdded;
  if (!raw || typeof raw !== 'object') return null;
  const a = raw as Record<string, unknown>;
  if (typeof a.epochDay !== 'number' || typeof a.kcal !== 'number' || typeof a.name !== 'string') return null;
  if (!MEAL_SLOTS.includes(a.slot as MealSlot)) return null;
  return { epochDay: a.epochDay, slot: a.slot as MealSlot, name: a.name, kcal: a.kcal };
}

/**
 * The diary row the arrival stands for: the newest live entry of that meal
 * with that name and (to a rounding) those kcal.
 */
export function findArrival(entries: FoodLogEntry[], arrival: Arrival): FoodLogEntry | null {
  let found: FoodLogEntry | null = null;
  for (const e of entries) {
    if (e.deleted_at !== null || e.epoch_day !== arrival.epochDay || e.meal !== arrival.slot || e.name !== arrival.name) continue;
    if (Math.abs(e.kcal - arrival.kcal) > 1) continue;
    if (!found || e.logged_at > found.logged_at) found = e;
  }
  return found;
}

/* ---------------------------------------------------------- the diary */

/** The extra columns a diary row has in the database beyond FoodLogEntry (select('*') returns them). */
type FullEntry = FoodLogEntry & {
  fiber_g?: number | null;
  sugars_g?: number | null;
  salt_g?: number | null;
  saturated_fat_g?: number | null;
};

const per100 = (value: number | null | undefined, grams: number) => (value == null ? null : (value * 100) / grams);

/**
 * A logged row as a product, so it can be logged again at another amount
 * ("Edit amount"): its nutrition per 100 g is the snapshot divided by its grams.
 * Null for a row without grams (nothing to scale from).
 */
export function entryAsProduct(entry: FoodLogEntry): FoodProduct | null {
  const grams = entry.grams;
  if (grams == null || !(grams > 0)) return null;
  const full = entry as FullEntry;
  return {
    id: `entry:${entry.id}`,
    barcode: '',
    name: entry.name,
    brand: null,
    kcalPer100g: (entry.kcal * 100) / grams,
    proteinPer100g: (entry.protein_g * 100) / grams,
    fatPer100g: (entry.fat_g * 100) / grams,
    carbsPer100g: (entry.carbs_g * 100) / grams,
    servingSizeG: null,
    liquid: false,
    fiberPer100g: per100(full.fiber_g, grams),
    sugarsPer100g: per100(full.sugars_g, grams),
    saltPer100g: per100(full.salt_g, grams),
    saturatedFatPer100g: per100(full.saturated_fat_g, grams),
    additives: [],
  };
}

/** A diary row with every column the page writes when it logs a row itself. */
export type EntryRow = FoodLogEntry & {
  fiber_g: number | null;
  sugars_g: number | null;
  salt_g: number | null;
  saturated_fat_g: number | null;
};

/**
 * The same food, meal, day and time as `entry` under a new id: what Undo
 * writes back after a delete. A new row rather than the old one revived,
 * because a phone keeps a row it once deleted deleted, while it takes a new
 * website row as it is (docs/cloud-sync.md, the pull rules).
 */
export function entryCopy(entry: FoodLogEntry, id: string): EntryRow {
  const full = entry as FullEntry;
  return {
    id,
    owner_id: entry.owner_id,
    epoch_day: entry.epoch_day,
    meal: entry.meal,
    name: entry.name,
    grams: entry.grams,
    servings: entry.servings,
    kcal: entry.kcal,
    protein_g: entry.protein_g,
    fat_g: entry.fat_g,
    carbs_g: entry.carbs_g,
    fiber_g: full.fiber_g ?? null,
    sugars_g: full.sugars_g ?? null,
    salt_g: full.salt_g ?? null,
    saturated_fat_g: full.saturated_fat_g ?? null,
    logged_at: entry.logged_at,
    deleted_at: null,
  };
}

/**
 * `entry` at another amount ("Edit amount") under a new id: every nutrient
 * scaled by the grams, same meal and time, so the row keeps its place in the
 * list. Null for a row without grams (nothing to scale from).
 */
export function entryAtGrams(entry: FoodLogEntry, grams: number, id: string): EntryRow | null {
  if (entry.grams == null || !(entry.grams > 0)) return null;
  const f = grams / entry.grams;
  const row = entryCopy(entry, id);
  const scale = (v: number | null) => (v == null ? null : v * f);
  return {
    ...row,
    grams,
    servings: null,
    kcal: row.kcal * f,
    protein_g: row.protein_g * f,
    fat_g: row.fat_g * f,
    carbs_g: row.carbs_g * f,
    fiber_g: scale(row.fiber_g),
    sugars_g: scale(row.sugars_g),
    salt_g: scale(row.salt_g),
    saturated_fat_g: scale(row.saturated_fat_g),
  };
}

/* ------------------------------------------------------- phone activity */

/** When the phone last published the day (the summary row's updated_at, which the type leaves out). */
export function publishedAt(summary: DaySummary | null | undefined): string | null {
  const at = (summary as (DaySummary & { updated_at?: unknown }) | null | undefined)?.updated_at;
  return typeof at === 'string' ? at : null;
}

/* --------------------------------------------------------------- water */

export const GLASS_ML = 250;
export const GLASSES = 8;

/** How full glass `index` (0-based) is for `ml` in the day: 0 to 1. */
export function glassFill(ml: number, index: number): number {
  return Math.max(0, Math.min(1, (ml - index * GLASS_ML) / GLASS_ML));
}

/** When the last live glass was logged (the row's logged_at, which the type leaves out). */
export function lastWaterAt(water: WaterEntry[]): string | null {
  let last: string | null = null;
  for (const w of water) {
    if (w.deleted_at !== null) continue;
    const at = (w as WaterEntry & { logged_at?: unknown }).logged_at;
    if (typeof at === 'string' && (last === null || at > last)) last = at;
  }
  return last;
}

/* -------------------------------------------------------------- weight */

/** The weight logged on that day (a manual entry wins), or null. */
export function weightOn(weights: WeightEntry[], epochDay: number): number | null {
  return onePerDay(weights).find((w) => w.epoch_day === epochDay)?.weight_kg ?? null;
}

/**
 * The change over the week up to `epochDay`: the latest weight on or before
 * it minus the latest one on or before a week earlier, to 0.1 kg. Null when
 * either is missing or they are the same entry.
 */
export function weekChange(weights: WeightEntry[], epochDay: number): number | null {
  const days = onePerDay(weights);
  const atOrBefore = (d: number) => [...days].reverse().find((w) => w.epoch_day <= d) ?? null;
  const now = atOrBefore(epochDay);
  const before = atOrBefore(epochDay - 7);
  if (!now || !before || now.epoch_day === before.epoch_day) return null;
  return Math.round((now.weight_kg - before.weight_kg) * 10) / 10;
}

/** "64.2" / "64,2": a weight typed in either decimal mark, or NaN. */
export const parseKg = (text: string) => Number(text.trim().replace(',', '.'));

/** The weights the site accepts, as before: 30 to 300 kg. */
export const validKg = (kg: number) => Number.isFinite(kg) && kg >= 30 && kg <= 300;
