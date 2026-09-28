import { describe, expect, it } from 'vitest';
import { forGrams, kcalFromKj, per100gFromServing, resolveKcalPer100g, scalePer100g, servingsToGrams } from '../food';
import { barcodeCandidates, expandUpcE, gtinCheckDigitValid, lenientNumber, toProduct } from '../openFoodFacts';

describe('FoodMath port', () => {
  it('80 grams of a 250 kcal product is 200 kcal', () => {
    expect(forGrams(250, 10, 5, 40, 80)).toEqual({ kcal: 200, proteinG: 8, fatG: 4, carbsG: 32 });
  });
  it('two portions of 55 grams is 110 grams', () => expect(servingsToGrams(2, 55)).toBe(110));
  it('optional nutrients keep null', () => {
    expect(scalePer100g(null, 80)).toBeNull();
    expect(scalePer100g(10, 50)).toBe(5);
  });
});

describe('NutritionLabelMath port', () => {
  it('kJ to kcal uses the label factor', () => expect(kcalFromKj(1046)).toBeCloseTo(250, 1));
  it('per-serving rescales, null on unknown serving', () => {
    expect(per100gFromServing(120, 30)).toBe(400);
    expect(per100gFromServing(120, null)).toBeNull();
    expect(per100gFromServing(120, 0)).toBeNull();
  });
  it('energy resolution order: kcal, kJ, serving, macros', () => {
    const base = { kcalPer100g: null, kjPer100g: null, kcalPerServing: null, kjPerServing: null, servingSizeG: null, proteinPer100g: null, fatPer100g: null, carbsPer100g: null };
    expect(resolveKcalPer100g({ ...base, kcalPer100g: 250, kjPer100g: 9999 })).toBe(250);
    expect(resolveKcalPer100g({ ...base, kjPer100g: 1046 })).toBeCloseTo(250, 1);
    expect(resolveKcalPer100g({ ...base, kcalPerServing: 120, servingSizeG: 30 })).toBe(400);
    expect(resolveKcalPer100g({ ...base, proteinPer100g: 10, fatPer100g: 5, carbsPer100g: 40 })).toBe(245);
    expect(resolveKcalPer100g({ ...base, proteinPer100g: 10, fatPer100g: 5 })).toBeNull();
  });
});

describe('Open Food Facts mapping port', () => {
  it('a complete product maps', () => {
    const p = toProduct({
      code: '5000112637922', product_name: 'Baked Beans', brands: 'Heinz, Kraft', serving_quantity: '207', additives_tags: ['en:e412'],
      nutriments: { 'energy-kcal_100g': 81, proteins_100g: 4.7, fat_100g: 0.2, carbohydrates_100g: 12.9, fiber_100g: 3.7 },
    })!;
    expect(p.id).toBe('off:5000112637922');
    expect(p.brand).toBe('Heinz');
    expect(p.servingSizeG).toBe(207);
    expect(p.additives).toEqual(['E412']);
  });
  it('kJ-only, generic energy, per-serving-only and macro-only energy are all usable', () => {
    expect(toProduct({ code: '1', product_name: 'Oatcakes', nutriments: { 'energy-kj_100g': 1841, proteins_100g: 10, fat_100g: 18, carbohydrates_100g: 60 } })!.kcalPer100g).toBeCloseTo(440, 0);
    expect(toProduct({ code: '1', product_name: 'Crisps', nutriments: { energy_100g: 2200, proteins_100g: 6, fat_100g: 30, carbohydrates_100g: 50 } })!.kcalPer100g).toBeCloseTo(525.8, 1);
    const bar = toProduct({ code: '2', product_name: 'Bar', serving_quantity: 30, nutriments: { 'energy-kcal_serving': 120, proteins_serving: 3, fat_serving: 4.5, carbohydrates_serving: 18, sugars_serving: 9 } })!;
    expect(bar.kcalPer100g).toBe(400);
    expect(bar.proteinPer100g).toBe(10);
    expect(bar.sugarsPer100g).toBe(30);
    expect(bar.fiberPer100g).toBeNull();
    expect(toProduct({ code: '3', product_name: 'Rice', nutriments: { proteins_100g: 7, fat_100g: 1, carbohydrates_100g: 78 } })!.kcalPer100g).toBe(349);
  });
  it('drops products missing a macro or a serving size for per-serving data, keeps the English name fallback', () => {
    expect(toProduct({ code: '4', product_name: 'Mystery', nutriments: { 'energy-kcal_100g': 100, proteins_100g: 1, fat_100g: 1 } })).toBeNull();
    expect(toProduct({ code: '2', product_name: 'Bar', nutriments: { 'energy-kcal_serving': 120, proteins_serving: 3, fat_serving: 4.5, carbohydrates_serving: 18 } })).toBeNull();
    expect(toProduct({ code: '5', product_name: '', product_name_en: 'Hummus', nutriments: { 'energy-kcal_100g': 300, proteins_100g: 8, fat_100g: 25, carbohydrates_100g: 10 } })!.name).toBe('Hummus');
  });
  it('malformed values become unknown', () => {
    const p = toProduct({ code: '6', product_name: 'Yoghurt', nutriments: { 'energy-kcal_100g': '95', proteins_100g: '4,2', fat_100g: 3.5, carbohydrates_100g: 12, sugars_100g: '', fiber_100g: '<0.5', salt_100g: null } })!;
    expect(p.kcalPer100g).toBe(95);
    expect(p.proteinPer100g).toBe(4.2);
    expect(p.sugarsPer100g).toBeNull();
    expect(p.fiberPer100g).toBeNull();
    expect(lenientNumber('NaN')).toBeNull();
    expect(lenientNumber(' 12,5 ')).toBe(12.5);
  });
});

describe('BarcodeNormalizer port', () => {
  it('candidates by length', () => {
    expect(barcodeCandidates('5000112637922')).toEqual(['5000112637922']);
    expect(barcodeCandidates('012345678905')).toEqual(['012345678905', '0012345678905']);
    expect(barcodeCandidates('0012345678905')).toEqual(['0012345678905', '012345678905']);
    expect(barcodeCandidates('96385074')).toEqual(['96385074', '0000096385074']);
    expect(barcodeCandidates('01234565')).toEqual(['01234565', '012345000065', '0012345000065', '0000001234565']);
    expect(barcodeCandidates('15000112637929')).toEqual(['15000112637929', '5000112637929']);
    expect(barcodeCandidates(' 5000-1126-37922 ')).toEqual(['5000112637922']);
    expect(barcodeCandidates('abc')).toEqual([]);
  });
  it('UPC-E expansion covers every last-digit rule and rejects non-UPC-E', () => {
    expect(expandUpcE('01234505')).toBe('012000003455');
    expect(expandUpcE('01234531')).toBe('012300000451');
    expect(expandUpcE('01234543')).toBe('012340000053');
    expect(expandUpcE('01234565')).toBe('012345000065');
    expect(expandUpcE('96385074')).toBeNull();
    expect(expandUpcE('01234564')).toBeNull();
  });
  it('GTIN check digit', () => {
    expect(gtinCheckDigitValid('5000112637922')).toBe(true);
    expect(gtinCheckDigitValid('5000112637923')).toBe(false);
  });
});
