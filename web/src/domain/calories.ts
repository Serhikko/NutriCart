import { ACTIVITY_MULTIPLIER, roundToInt, type ActivityLevel, type DailyTargets, type Goal, type Sex } from './model';

/**
 * Port of the phone's CalorieCalculator and DailyTargetMath. Every function's
 * output depends only on its inputs; the tests are the Kotlin tests, translated.
 * A number that differs from the phone's by more than rounding is a bug here.
 */

/** Energy in roughly 1 kg of body fat: converts "kg per week" into "kcal per day". */
export const KCAL_PER_KG_BODY_FAT = 7700;
/** Hard safety floors: the target never goes below these, whatever the goal says. */
export const MIN_KCAL_MALE = 1500;
export const MIN_KCAL_FEMALE = 1200;
export const ADULT_AGE_YEARS = 18;

const PROTEIN_G_PER_KG = 1.8;
const FAT_SHARE_OF_KCAL = 0.25;
const KCAL_PER_G_PROTEIN = 4;
const KCAL_PER_G_FAT = 9;
const KCAL_PER_G_CARB = 4;
/** Baseline for non-exercise daily living. */
const SEDENTARY_MULTIPLIER = 1.2;

/** Mifflin-St Jeor: calories the body burns at complete rest. */
export function bmr(sex: Sex, weightKg: number, heightCm: number, ageYears: number): number {
  const sexTerm = sex === 'MALE' ? 5 : -161;
  return 10 * weightKg + 6.25 * heightCm - 5 * ageYears + sexTerm;
}

export function tdee(bmrKcal: number, level: ActivityLevel): number {
  return bmrKcal * ACTIVITY_MULTIPLIER[level];
}

/** Lose 0.5 kg/week -> -(0.5 * 7700 / 7) = -550 kcal/day. */
export function goalDeltaKcal(goal: Goal, targetKgPerWeek: number): number {
  switch (goal) {
    case 'LOSE':
      return (-targetKgPerWeek * KCAL_PER_KG_BODY_FAT) / 7;
    case 'MAINTAIN':
      return 0;
    case 'GAIN':
      return (targetKgPerWeek * KCAL_PER_KG_BODY_FAT) / 7;
  }
}

export function safetyFloorKcal(sex: Sex): number {
  return sex === 'MALE' ? MIN_KCAL_MALE : MIN_KCAL_FEMALE;
}

/** The BASE daily target: questionnaire only, no watch data. */
export function baseTargetKcal(
  sex: Sex,
  weightKg: number,
  heightCm: number,
  ageYears: number,
  level: ActivityLevel,
  goal: Goal,
  targetKgPerWeek: number,
): number {
  const raw = tdee(bmr(sex, weightKg, heightCm, ageYears), level) + goalDeltaKcal(goal, targetKgPerWeek);
  return Math.max(raw, safetyFloorKcal(sex));
}

/**
 * The ADJUSTED target for a day with measured active calories:
 * BMR * 1.2 + activeKcal + goal delta. The questionnaire multiplier already
 * contains exercise, so it is dropped to avoid counting workouts twice.
 */
export function adjustedTargetKcal(sex: Sex, bmrKcal: number, goal: Goal, targetKgPerWeek: number, activeKcal: number): number {
  const raw = bmrKcal * SEDENTARY_MULTIPLIER + activeKcal + goalDeltaKcal(goal, targetKgPerWeek);
  return Math.max(raw, safetyFloorKcal(sex));
}

/** "Calories out": sedentary baseline + measured active kcal, or the questionnaire estimate. */
export function caloriesOut(bmrKcal: number, level: ActivityLevel, activeKcal: number | null): number {
  return activeKcal !== null ? bmrKcal * SEDENTARY_MULTIPLIER + activeKcal : tdee(bmrKcal, level);
}

/**
 * Macro split, protein -> fat -> carbs: protein 1.8 g/kg (capped so protein
 * plus fat never exceed the budget), fat 25% of kcal, carbs the remainder,
 * never negative.
 */
export function macroTargets(kcalTarget: number, weightKg: number): DailyTargets {
  const fatKcal = kcalTarget * FAT_SHARE_OF_KCAL;
  const fatG = fatKcal / KCAL_PER_G_FAT;
  const proteinG = Math.min(PROTEIN_G_PER_KG * weightKg, (kcalTarget - fatKcal) / KCAL_PER_G_PROTEIN);
  const carbsG = Math.max(0, (kcalTarget - fatKcal - proteinG * KCAL_PER_G_PROTEIN) / KCAL_PER_G_CARB);
  return {
    kcal: roundToInt(kcalTarget),
    proteinG: roundToInt(proteinG),
    fatG: roundToInt(fatG),
    carbsG: roundToInt(carbsG),
  };
}

/** Calendar-correct age, like java.time.Period.between(birth, today).years. */
export function ageYears(birthDate: Date, today: Date): number {
  let years = today.getFullYear() - birthDate.getFullYear();
  const beforeBirthday =
    today.getMonth() < birthDate.getMonth() ||
    (today.getMonth() === birthDate.getMonth() && today.getDate() < birthDate.getDate());
  if (beforeBirthday) years -= 1;
  return years;
}

export function isAdult(birthDate: Date, today: Date): boolean {
  return ageYears(birthDate, today) >= ADULT_AGE_YEARS;
}

/** Everything the day target needs from the profile. */
export interface TargetProfile {
  sex: Sex;
  weightKg: number;
  heightCm: number;
  ageYears: number;
  level: ActivityLevel;
  goal: Goal;
  targetKgPerWeek: number;
  customKcalTarget: number | null;
}

/** The no-activity reference: manual override or the base formula. */
export function referenceKcal(p: TargetProfile): number {
  return p.customKcalTarget ?? baseTargetKcal(p.sex, p.weightKg, p.heightCm, p.ageYears, p.level, p.goal, p.targetKgPerWeek);
}

/**
 * The ONE rule for a day's kcal target (DailyTargetMath.dayTargetKcal): a
 * manual override replaces both formulas; otherwise measured activeKcal
 * switches to the adjusted formula; manual workouts always add on top.
 */
export function dayTargetKcal(p: TargetProfile, activeKcal: number | null, manualWorkoutKcal: number): number {
  const reference = referenceKcal(p);
  const base =
    p.customKcalTarget === null && activeKcal !== null
      ? adjustedTargetKcal(p.sex, bmr(p.sex, p.weightKg, p.heightCm, p.ageYears), p.goal, p.targetKgPerWeek, activeKcal)
      : reference;
  return manualWorkoutKcal + base;
}
