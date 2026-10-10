import { describe, expect, it } from 'vitest';
import { dayLine, groupByMeal, onePerDay, sumKcal, sumWater, type FoodLogEntry } from '../diary';

const entry = (over: Partial<FoodLogEntry>): FoodLogEntry => ({
  id: 'x',
  owner_id: 'o',
  epoch_day: 20724,
  meal: 'LUNCH',
  name: 'Apple',
  grams: 100,
  servings: null,
  kcal: 52,
  protein_g: 0.3,
  fat_g: 0.2,
  carbs_g: 14,
  logged_at: '2026-09-28T10:00:00Z',
  deleted_at: null,
  ...over,
});

describe('diary arithmetic', () => {
  it('groups live entries by meal in logging order and ignores tombstones', () => {
    const groups = groupByMeal([
      entry({ id: 'b', meal: 'LUNCH', logged_at: '2026-09-28T12:30:00Z', name: 'Rice' }),
      entry({ id: 'a', meal: 'LUNCH', logged_at: '2026-09-28T12:00:00Z', name: 'Chicken' }),
      entry({ id: 'c', meal: 'BREAKFAST', name: 'Oats' }),
      entry({ id: 'd', meal: 'DINNER', name: 'Gone', deleted_at: '2026-09-28T20:00:00Z' }),
    ]);
    expect(groups.get('LUNCH')!.map((e) => e.name)).toEqual(['Chicken', 'Rice']);
    expect(groups.get('BREAKFAST')!.map((e) => e.name)).toEqual(['Oats']);
    expect(groups.get('DINNER')).toEqual([]);
    expect(groups.get('SNACK')).toEqual([]);
  });

  it('sums skip deleted rows', () => {
    expect(sumKcal([entry({ kcal: 100 }), entry({ kcal: 50, deleted_at: 'x' })])).toBe(100);
    expect(sumWater([{ id: '1', epoch_day: 1, ml: 250, deleted_at: null }, { id: '2', epoch_day: 1, ml: 500, deleted_at: 'x' }])).toBe(250);
  });

  it('keeps one weight per day, manual over watch, oldest first', () => {
    const rows = onePerDay([
      { epoch_day: 2, source: 'HEALTH_CONNECT', weight_kg: 80.5 },
      { epoch_day: 2, source: 'MANUAL', weight_kg: 80.0 },
      { epoch_day: 1, source: 'HEALTH_CONNECT', weight_kg: 81.0 },
    ]);
    expect(rows.map((r) => [r.epoch_day, r.weight_kg])).toEqual([
      [1, 81.0],
      [2, 80.0],
    ]);
  });

  it('day line prefers the phone summary and marks going over', () => {
    expect(dayLine({ epoch_day: 1, target_kcal: 2100, eaten_kcal: 1230, active_kcal: null, steps: null, workout_kcal: 0 }, 999)).toEqual({
      eaten: 1230,
      target: 2100,
      remaining: 870,
    });
    expect(dayLine({ epoch_day: 1, target_kcal: 2000, eaten_kcal: 2300, active_kcal: null, steps: null, workout_kcal: 0 }, 0).remaining).toBe(-300);
    expect(dayLine(null, 1020.4)).toEqual({ eaten: 1020, target: null, remaining: null });
  });
});
