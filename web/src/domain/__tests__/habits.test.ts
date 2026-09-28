import { describe, expect, it } from 'vitest';
import { dayState, guessMealSlot, streak } from '../habits';

describe('StreakCalculator port', () => {
  const today = 20000;
  it('no logged days means no streak', () => expect(streak(new Set(), today)).toBe(0));
  it('today plus two previous days is three', () => expect(streak(new Set([today, today - 1, today - 2]), today)).toBe(3));
  it("an empty today does not break yesterday's streak", () => expect(streak(new Set([today - 1, today - 2, today - 3]), today)).toBe(3));
  it('a gap resets the streak', () => expect(streak(new Set([today, today - 2, today - 3]), today)).toBe(1));
  it('old history without today or yesterday gives zero', () => expect(streak(new Set([today - 5, today - 6]), today)).toBe(0));
});

describe('AdherenceCalculator port', () => {
  it('empty, broken target, good, tolerance, over', () => {
    expect(dayState(null, 2000)).toBe('EMPTY');
    expect(dayState(0, 2000)).toBe('EMPTY');
    expect(dayState(1500, 0)).toBe('EMPTY');
    expect(dayState(1800, 2000)).toBe('GOOD');
    expect(dayState(2100, 2000)).toBe('GOOD');
    expect(dayState(2101, 2000)).toBe('OVER');
  });
});

describe('MealSlotGuess port', () => {
  it('every boundary falls on the later meal', () => {
    expect(guessMealSlot(4)).toBe('SNACK');
    expect(guessMealSlot(5)).toBe('BREAKFAST');
    expect(guessMealSlot(10)).toBe('BREAKFAST');
    expect(guessMealSlot(11)).toBe('LUNCH');
    expect(guessMealSlot(15)).toBe('LUNCH');
    expect(guessMealSlot(16)).toBe('DINNER');
    expect(guessMealSlot(21)).toBe('DINNER');
    expect(guessMealSlot(22)).toBe('SNACK');
    expect(guessMealSlot(0)).toBe('SNACK');
  });
  it('a full day reaches all four slots', () => {
    expect(new Set(Array.from({ length: 24 }, (_, h) => guessMealSlot(h))).size).toBe(4);
  });
});
