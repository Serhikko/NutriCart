/**
 * Shapes of the rows the phone publishes, and the little bit of arithmetic the
 * partner view needs. Numbers that need the profile (the target) are NOT
 * computed here: the phone publishes them in day_summaries with the same math
 * as its dashboard, so the website can never disagree with the phone.
 */

export const MEAL_SLOTS = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'] as const;
export type MealSlot = (typeof MEAL_SLOTS)[number];

export interface FoodLogEntry {
  id: string;
  owner_id: string;
  epoch_day: number;
  meal: MealSlot;
  name: string;
  grams: number | null;
  servings: number | null;
  kcal: number;
  protein_g: number;
  fat_g: number;
  carbs_g: number;
  logged_at: string;
  deleted_at: string | null;
}

export interface WaterEntry {
  id: string;
  epoch_day: number;
  ml: number;
  deleted_at: string | null;
}

export interface DaySummary {
  epoch_day: number;
  target_kcal: number;
  eaten_kcal: number;
  active_kcal: number | null;
  steps: number | null;
  workout_kcal: number;
}

export interface WeightEntry {
  epoch_day: number;
  source: 'MANUAL' | 'HEALTH_CONNECT';
  weight_kg: number;
}

export interface Nudge {
  id: string;
  owner_id: string;
  from_id: string;
  from_name: string;
  text: string;
  created_at: string;
  seen_at: string | null;
}

/** Live entries, grouped by meal in day order, each meal in logging order. */
export function groupByMeal(entries: FoodLogEntry[]): Map<MealSlot, FoodLogEntry[]> {
  const groups = new Map<MealSlot, FoodLogEntry[]>();
  for (const slot of MEAL_SLOTS) groups.set(slot, []);
  const live = entries.filter((e) => e.deleted_at === null);
  live.sort((a, b) => a.logged_at.localeCompare(b.logged_at));
  for (const e of live) groups.get(e.meal)!.push(e);
  return groups;
}

export function sumKcal(entries: FoodLogEntry[]): number {
  return entries.reduce((acc, e) => acc + (e.deleted_at === null ? e.kcal : 0), 0);
}

export function sumWater(entries: WaterEntry[]): number {
  return entries.reduce((acc, e) => acc + (e.deleted_at === null ? e.ml : 0), 0);
}

/** Same rule as the phone's WeightChart: one point per day, MANUAL wins. */
export function onePerDay(entries: WeightEntry[]): WeightEntry[] {
  const byDay = new Map<number, WeightEntry>();
  for (const e of entries) {
    const current = byDay.get(e.epoch_day);
    if (!current || (current.source !== 'MANUAL' && e.source === 'MANUAL')) byDay.set(e.epoch_day, e);
  }
  return [...byDay.values()].sort((a, b) => a.epoch_day - b.epoch_day);
}

/** "Eaten / target" with what is left; negative remaining means over. */
export function dayLine(summary: DaySummary | null, eatenFallback: number) {
  const eaten = summary ? summary.eaten_kcal : Math.round(eatenFallback);
  const target = summary ? summary.target_kcal : null;
  return { eaten, target, remaining: target === null ? null : target - eaten };
}
