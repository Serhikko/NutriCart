import { describe, expect, it } from 'vitest';
import { isLiquid } from '../liquid';

describe('liquid detection', () => {
  it('nutrition per 100 ml is a drink', () => expect(isLiquid('100ml', null, null)).toBe(true));
  it('a volume in the quantity or serving size is a drink', () => {
    expect(isLiquid('100g', '500 ml', null)).toBe(true);
    expect(isLiquid(null, '50cl', null)).toBe(true);
    expect(isLiquid(null, '1,5 L', null)).toBe(true);
    expect(isLiquid(null, null, '1 can (330 ml)')).toBe(true);
  });
  it('grams, pieces and blanks are food', () => {
    expect(isLiquid('100g', '500 g', '30 g')).toBe(false);
    expect(isLiquid(null, '6 pcs', '')).toBe(false);
    expect(isLiquid(null, null, null)).toBe(false);
    expect(isLiquid(null, 'small', 'medium')).toBe(false);
  });
});
