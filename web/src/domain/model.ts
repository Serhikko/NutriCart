/**
 * The vocabulary shared with the phone. Enum NAMES are what the database
 * stores and what the Kotlin enums are called, so the two clients never
 * disagree about a value.
 */
export type Sex = 'MALE' | 'FEMALE';
export type Goal = 'LOSE' | 'MAINTAIN' | 'GAIN';
export type ActivityLevel = 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'ACTIVE' | 'VERY_ACTIVE';

/** Standard TDEE multipliers, the same as ActivityLevel.multiplier on the phone. */
export const ACTIVITY_MULTIPLIER: Record<ActivityLevel, number> = {
  SEDENTARY: 1.2,
  LIGHT: 1.375,
  MODERATE: 1.55,
  ACTIVE: 1.725,
  VERY_ACTIVE: 1.9,
};

export const ACTIVITY_LEVELS: ActivityLevel[] = ['SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE', 'VERY_ACTIVE'];
export const GOALS: Goal[] = ['LOSE', 'MAINTAIN', 'GAIN'];

export interface DailyTargets {
  kcal: number;
  proteinG: number;
  fatG: number;
  carbsG: number;
}

/** Shared limits and choices for the profile forms (ProfileOptions on the phone). */
export const PROFILE_OPTIONS = {
  heightCm: { min: 100, max: 250 },
  weightKg: { min: 30, max: 300 },
  defaultRateKgPerWeek: 0.5,
  rateOptions: [0.25, 0.5, 0.75, 1.0],
} as const;

/** Kotlin's Double.roundToInt(): nearest integer, ties towards positive infinity. */
export function roundToInt(value: number): number {
  return Math.round(value);
}
