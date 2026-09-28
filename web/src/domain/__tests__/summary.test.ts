import { describe, expect, it } from 'vitest';
import { daySummaryPayload } from '../summary';

describe('day summary policy', () => {
  it('a web account writes target and eaten', () => {
    expect(daySummaryPayload({ targetKcal: 2100.4, primaryClient: 'web' }, 1229.6, null)).toEqual({ eaten_kcal: 1230, target_kcal: 2100 });
    expect(daySummaryPayload({ targetKcal: 2100, primaryClient: 'web' }, 1230, { target_kcal: 1999 })).toEqual({ eaten_kcal: 1230, target_kcal: 2100 });
  });
  it('a web account without a target writes nothing', () => {
    expect(daySummaryPayload({ targetKcal: null, primaryClient: 'web' }, 500, null)).toBeNull();
  });
  it("a phone account's existing row keeps the phone's target, only eaten moves", () => {
    expect(daySummaryPayload({ targetKcal: 2100, primaryClient: 'phone' }, 1230, { target_kcal: 2350 })).toEqual({ eaten_kcal: 1230 });
  });
  it('a phone account with no row yet gets the web target as a stand-in', () => {
    expect(daySummaryPayload({ targetKcal: 2100, primaryClient: 'phone' }, 1230, null)).toEqual({ eaten_kcal: 1230, target_kcal: 2100 });
    expect(daySummaryPayload({ targetKcal: null, primaryClient: 'phone' }, 1230, null)).toBeNull();
  });
});
