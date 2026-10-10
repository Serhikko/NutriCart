import { roundToInt } from './model';

/**
 * Port of NutrientTargets: daily guides for the detail nutrients, derived
 * from the kcal target. Fiber 14 g per 1000 kcal; free sugars and saturated
 * fat at most 10% of energy (WHO); salt at most 5 g/day (WHO).
 */
export const FIBER_G_PER_1000_KCAL = 14;
export const SUGAR_SHARE_OF_KCAL = 0.1;
export const SAT_FAT_SHARE_OF_KCAL = 0.1;
export const SALT_LIMIT_G = 5;
const KCAL_PER_G_SUGAR = 4;
const KCAL_PER_G_FAT = 9;

export type NutrientState = 'NEUTRAL' | 'GOOD' | 'WARN' | 'OVER';

export const fiberTargetG = (kcalTarget: number) => roundToInt((kcalTarget * FIBER_G_PER_1000_KCAL) / 1000);
export const sugarLimitG = (kcalTarget: number) => roundToInt((kcalTarget * SUGAR_SHARE_OF_KCAL) / KCAL_PER_G_SUGAR);
export const saturatedFatLimitG = (kcalTarget: number) => roundToInt((kcalTarget * SAT_FAT_SHARE_OF_KCAL) / KCAL_PER_G_FAT);

/** A nutrient to REACH: neutral below 90%, good in the 90–110% band, over above. */
export function targetState(consumedG: number, targetG: number): NutrientState {
  if (targetG <= 0) return 'NEUTRAL';
  const ratio = consumedG / targetG;
  if (ratio < 0.9) return 'NEUTRAL';
  if (ratio <= 1.1) return 'GOOD';
  return 'OVER';
}

/** A nutrient to STAY UNDER: good up to the limit, warning up to 120%, over beyond. */
export function limitState(consumedG: number, limitG: number): NutrientState {
  if (limitG <= 0) return 'NEUTRAL';
  const ratio = consumedG / limitG;
  if (ratio <= 1.0) return 'GOOD';
  if (ratio <= 1.2) return 'WARN';
  return 'OVER';
}
