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

  // Shared with the phone's LiquidDetectorTest: Ukrainian and Belarusian packs.
  it.each(['500 мл', '0,5 л', '1л', '1,5 Л', '2 літри', '1 литр', '330 ML'])('Cyrillic and upper-case volumes are drinks: %s', (q) => {
    expect(isLiquid(null, q, null)).toBe(true);
    expect(isLiquid(null, null, q)).toBe(true);
  });
  it.each(['450 г', '1 лист', '3 ложки', '12 шт', '5 lb', '1 large'])('grams, pieces and words that only start like a unit are food: %s', (q) => {
    expect(isLiquid(null, q, null)).toBe(false);
    expect(isLiquid(null, null, q)).toBe(false);
  });
  it('a number glued to a preceding letter is not a size', () => {
    expect(isLiquid(null, 'ж1л', null)).toBe(false);
    expect(isLiquid(null, 'x2 l', null)).toBe(false);
    expect(isLiquid(null, 'пляшка 1,5л', null)).toBe(true);
    expect(isLiquid(null, '(1 л)', null)).toBe(true);
  });
});
