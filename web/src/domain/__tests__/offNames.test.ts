import { describe, expect, it } from 'vitest';
import { OFF_FIELDS, nameLanguages, productName, toPrefill, toProduct } from '../openFoodFacts';

const UA = '4820024700016';
const BY = '4810268000013';
const UK = '5000112637922';
const complete = { 'energy-kcal_100g': 100, proteins_100g: 3, fat_100g: 2, carbohydrates_100g: 10 };

// Shared vectors with the phone's ProductDtoNameTest: the name never depends on the UI language.
describe('product name resolution', () => {
  it.each([
    ['1: blank product_name, Ukrainian name', UA, { product_name: '', product_name_uk: 'Молоко 2,5%' }, 'Молоко 2,5%'],
    ['2: Ukrainian code prefers uk over en', UA, { product_name: '', product_name_en: 'Milk', product_name_uk: 'Молоко' }, 'Молоко'],
    ['3: other codes keep English first', UK, { product_name: '', product_name_en: 'Milk', product_name_uk: 'Молоко' }, 'Milk'],
    ['4: Belarusian code prefers be over ru', BY, { product_name: '', product_name_ru: 'Сырок', product_name_be: 'Сырок беларускі' }, 'Сырок беларускі'],
    ['5: product_name wins', UA, { product_name: 'Kefir', product_name_uk: 'Кефір' }, 'Kefir'],
    ['6: generic name by language', UA, { generic_name: '', generic_name_uk: 'Сир кисломолочний' }, 'Сир кисломолочний'],
    ['7: whitespace collapsed', UA, { product_name: '  Хліб\n  житній  ' }, 'Хліб житній'],
    ['8: whitespace-only is blank', UA, { product_name: '   ', product_name_ru: 'Хлеб' }, 'Хлеб'],
  ] as const)('%s', (_label, code, fields, name) => {
    expect(productName({ code, ...fields }, code)).toBe(name);
    // The same name through the real mapping paths.
    expect(toPrefill({ code, ...fields }, code).name).toBe(name);
    expect(toProduct({ code, ...fields, nutriments: complete })!.name).toBe(name);
  });

  it('every product name beats every generic name', () => {
    expect(productName({ product_name_be: 'Хлеб', generic_name: 'Bread' }, UA)).toBe('Хлеб');
  });
  it('no name at all is no name, and the product is dropped', () => {
    expect(productName({ product_name: ' ', generic_name: '\t' }, UA)).toBeNull();
    expect(toProduct({ code: UA, nutriments: complete })).toBeNull();
    expect(toPrefill({ code: UA, nutriments: complete }, UA).name).toBeNull();
  });
  it('language order by origin', () => {
    expect(nameLanguages(UA)).toEqual(['uk', 'ru', 'en', 'be']);
    expect(nameLanguages(BY)).toEqual(['be', 'ru', 'en', 'uk']);
    expect(nameLanguages(UK)).toEqual(['en', 'uk', 'ru', 'be']);
    expect(nameLanguages('482002470001')).toEqual(['en', 'uk', 'ru', 'be']);
  });
  it('asks Open Food Facts for every name field', () => {
    const fields = OFF_FIELDS.split(',');
    for (const f of ['code', 'product_name', 'product_name_en', 'product_name_uk', 'product_name_ru', 'product_name_be', 'generic_name', 'generic_name_en', 'generic_name_uk', 'generic_name_ru', 'generic_name_be', 'brands', 'nutriments', 'serving_quantity', 'serving_size', 'quantity', 'nutrition_data_per', 'additives_tags']) {
      expect(fields).toContain(f);
    }
  });
});

describe('prefill of an incomplete product', () => {
  const bread = { code: UA, product_name_uk: 'Хліб', brands: 'Київхліб, Інше', nutriments: { 'energy-kcal_100g': 240, proteins_100g: 8 } };

  it('the shared vector: dropped by the full mapping, kept as a prefill', () => {
    expect(toProduct(bread)).toBeNull();
    expect(toPrefill(bread, UA)).toEqual({
      barcode: UA,
      name: 'Хліб',
      brand: 'Київхліб',
      kcalPer100g: 240,
      proteinPer100g: 8,
      fatPer100g: null,
      carbsPer100g: null,
      servingSizeG: null,
      liquid: false,
      fiberPer100g: null,
      sugarsPer100g: null,
      saltPer100g: null,
      saturatedFatPer100g: null,
    });
  });
  it('uses the looked-up code when the answer has none', () => {
    const { code: _ignored, ...noCode } = bread;
    expect(toPrefill(noCode, UA).barcode).toBe(UA);
    expect(toPrefill(noCode, UA).name).toBe('Хліб');
  });
  it('resolves values exactly like the full mapping: per serving, kJ, macros, drinks', () => {
    const perServing = toPrefill({ code: UA, serving_quantity: 30, nutriments: { 'energy-kcal_serving': 120, proteins_serving: 3 } }, UA);
    expect(perServing.kcalPer100g).toBe(400);
    expect(perServing.proteinPer100g).toBe(10);
    expect(perServing.servingSizeG).toBe(30);
    expect(toPrefill({ code: UA, nutriments: { 'energy-kj_100g': 1046 } }, UA).kcalPer100g).toBeCloseTo(250, 1);
    expect(toPrefill({ code: UA, quantity: '0,5 л', nutriments: {} }, UA).liquid).toBe(true);
    expect(toPrefill({ code: UA }, UA)).toMatchObject({ name: null, brand: null, kcalPer100g: null, liquid: false });
    // A complete product gives the same numbers both ways.
    const raw = { code: UA, product_name_uk: 'Кефір', quantity: '900 мл', serving_quantity: '200', nutriments: { 'energy-kj_100g': 230, proteins_100g: '2,8', fat_100g: 1, carbohydrates_100g: 4.1, salt_100g: 0.1 } };
    const { id: _id, additives: _a, ...product } = toProduct(raw)!;
    expect(product).toEqual(toPrefill(raw, UA));
    expect(product.liquid).toBe(true);
  });
});
