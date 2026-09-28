import { describe, expect, it } from 'vitest';
import {
  MIN_KCAL_FEMALE,
  MIN_KCAL_MALE,
  adjustedTargetKcal,
  ageYears,
  baseTargetKcal,
  bmr,
  caloriesOut,
  dayTargetKcal,
  goalDeltaKcal,
  isAdult,
  macroTargets,
  referenceKcal,
  tdee,
  type TargetProfile,
} from '../calories';

/** The phone's CalorieCalculatorTest, translated. Reference person: male, 80 kg, 180 cm, 25 y -> BMR 1805. */
describe('CalorieCalculator port', () => {
  it('bmr for male matches hand-computed value', () => {
    expect(bmr('MALE', 80, 180, 25)).toBeCloseTo(1805, 2);
  });
  it('bmr for female is 166 kcal lower than male', () => {
    expect(bmr('FEMALE', 80, 180, 25)).toBeCloseTo(1639, 2);
  });
  it('tdee multiplies bmr by the activity multiplier', () => {
    expect(tdee(1805, 'SEDENTARY')).toBeCloseTo(2166, 2);
    expect(tdee(1805, 'MODERATE')).toBeCloseTo(2797.75, 2);
  });
  it('goal deltas', () => {
    expect(goalDeltaKcal('LOSE', 0.5)).toBeCloseTo(-550, 2);
    expect(goalDeltaKcal('GAIN', 0.5)).toBeCloseTo(550, 2);
    expect(goalDeltaKcal('MAINTAIN', 1.0)).toBeCloseTo(0, 2);
  });
  it('base target is tdee plus delta when above the floor', () => {
    expect(baseTargetKcal('MALE', 80, 180, 25, 'MODERATE', 'LOSE', 0.5)).toBeCloseTo(2797.75 - 550, 2);
  });
  it('female target never drops below 1200', () => {
    expect(baseTargetKcal('FEMALE', 45, 155, 30, 'SEDENTARY', 'LOSE', 1.0)).toBeCloseTo(MIN_KCAL_FEMALE, 2);
  });
  it('male target never drops below 1500', () => {
    expect(baseTargetKcal('MALE', 60, 165, 40, 'SEDENTARY', 'LOSE', 1.0)).toBeCloseTo(MIN_KCAL_MALE, 2);
  });
  it('adjusted target uses sedentary baseline plus measured active kcal', () => {
    expect(adjustedTargetKcal('MALE', 1805, 'LOSE', 0.5, 400)).toBeCloseTo(2016, 2);
  });
  it('adjusted target also respects the safety floor', () => {
    expect(adjustedTargetKcal('FEMALE', 1107.75, 'LOSE', 1.0, 0)).toBeCloseTo(MIN_KCAL_FEMALE, 2);
  });
  it('calories out prefers watch data, else tdee', () => {
    expect(caloriesOut(1805, 'MODERATE', 400)).toBeCloseTo(1805 * 1.2 + 400, 2);
    expect(caloriesOut(1805, 'MODERATE', null)).toBeCloseTo(2797.75, 2);
  });
  it('macros for a normal case', () => {
    expect(macroTargets(2000, 80)).toEqual({ kcal: 2000, proteinG: 144, fatG: 56, carbsG: 231 });
  });
  it('macros never go negative for a heavy user at the safety floor', () => {
    const t = macroTargets(1200, 130);
    expect(t.proteinG).toBe(225);
    expect(t.fatG).toBe(33);
    expect(t.carbsG).toBe(0);
  });
  it('seventeen year old is blocked even one day before the birthday', () => {
    expect(ageYears(new Date(2008, 7, 2), new Date(2026, 7, 1))).toBe(17);
    expect(isAdult(new Date(2008, 7, 2), new Date(2026, 7, 1))).toBe(false);
  });
  it('eighteenth birthday unlocks the app', () => {
    expect(ageYears(new Date(2008, 7, 1), new Date(2026, 7, 1))).toBe(18);
    expect(isAdult(new Date(2008, 7, 1), new Date(2026, 7, 1))).toBe(true);
  });
});

/** The phone's DailyTargetMathTest, translated. Male, 80 kg, 180 cm, 30 y -> BMR 1780. */
describe('DailyTargetMath port', () => {
  const profile = (customKcalTarget: number | null = null, level: TargetProfile['level'] = 'MODERATE'): TargetProfile => ({
    sex: 'MALE',
    weightKg: 80,
    heightCm: 180,
    ageYears: 30,
    level,
    goal: 'MAINTAIN',
    targetKgPerWeek: 0,
    customKcalTarget,
  });

  it('no data means the plain base formula', () => {
    expect(dayTargetKcal(profile(), null, 0)).toBeCloseTo(1780 * 1.55, 3);
  });
  it('watch data switches to the adjusted formula', () => {
    expect(dayTargetKcal(profile(), 600, 0)).toBeCloseTo(1780 * 1.2 + 600, 3);
  });
  it('manual workouts always add on top', () => {
    expect(dayTargetKcal(profile(), null, 250)).toBeCloseTo(1780 * 1.55 + 250, 3);
    expect(dayTargetKcal(profile(), 600, 250)).toBeCloseTo(1780 * 1.2 + 600 + 250, 3);
  });
  it('a custom target replaces the base formula and disables the watch switch', () => {
    expect(dayTargetKcal(profile(2200), null, 0)).toBeCloseTo(2200, 3);
    expect(dayTargetKcal(profile(2200), 600, 0)).toBeCloseTo(2200, 3);
    expect(dayTargetKcal(profile(2200), null, 250)).toBeCloseTo(2450, 3);
  });
  it('reference equals the day target on a plain day', () => {
    expect(referenceKcal(profile())).toBeCloseTo(dayTargetKcal(profile(), null, 0), 3);
  });
});
