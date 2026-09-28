import type { MealSlot } from '../lib/diary';

/** Port of StreakCalculator: days in a row with at least one diary entry. */
export function streak(loggedDays: Set<number>, today: number): number {
  let day = loggedDays.has(today) ? today : today - 1;
  let count = 0;
  while (loggedDays.has(day)) {
    count += 1;
    day -= 1;
  }
  return count;
}

export type DayState = 'EMPTY' | 'GOOD' | 'OVER';
/** A small overshoot is life, not failure. */
export const ADHERENCE_TOLERANCE = 0.05;

/** Port of AdherenceCalculator.dayState. */
export function dayState(eatenKcal: number | null, targetKcal: number): DayState {
  if (eatenKcal === null || eatenKcal <= 0) return 'EMPTY';
  if (targetKcal <= 0) return 'EMPTY';
  if (eatenKcal <= targetKcal * (1 + ADHERENCE_TOLERANCE)) return 'GOOD';
  return 'OVER';
}

/** Port of MealSlotGuess: breakfast 05–10, lunch 11–15, dinner 16–21, else a snack. */
export function guessMealSlot(hour: number): MealSlot {
  if (hour >= 5 && hour <= 10) return 'BREAKFAST';
  if (hour >= 11 && hour <= 15) return 'LUNCH';
  if (hour >= 16 && hour <= 21) return 'DINNER';
  return 'SNACK';
}
