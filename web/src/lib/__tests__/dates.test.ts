import { describe, expect, it } from 'vitest';
import { fromEpochDay, lastDays, toEpochDay } from '../dates';

describe('epoch days match java.time.LocalDate.toEpochDay', () => {
  it('1970-01-01 is day 0 and 2026-09-28 is day 20724', () => {
    expect(toEpochDay(new Date(1970, 0, 1))).toBe(0);
    expect(toEpochDay(new Date(2026, 8, 28))).toBe(20724);
  });

  it('uses the local calendar date, not UTC', () => {
    // 23:30 local on the 28th is still the 28th, whatever the zone.
    expect(toEpochDay(new Date(2026, 8, 28, 23, 30))).toBe(20724);
    expect(toEpochDay(new Date(2026, 8, 29, 0, 10))).toBe(20725);
  });

  it('round-trips', () => {
    for (const day of [0, 19000, 20724, 21000]) {
      expect(toEpochDay(fromEpochDay(day))).toBe(day);
    }
  });

  it('lastDays ends today, oldest first', () => {
    expect(lastDays(3, 100)).toEqual([98, 99, 100]);
  });
});
