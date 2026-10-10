import { describe, expect, it } from 'vitest';
import {
  checkDraft,
  customProductId,
  draftFromPrefill,
  escapeLike,
  isStorableBarcode,
  parseDecimal,
  pickSaved,
  productToRow,
  rowToProduct,
  type CustomProductDraft,
  type CustomProductRow,
} from '../customProduct';
import { toPrefill } from '../openFoodFacts';

const row = (barcode: string, name = 'Хліб'): CustomProductRow => ({
  barcode,
  name,
  brand: null,
  kcal_per_100g: 240,
  protein_per_100g: 8,
  fat_per_100g: 1,
  carbs_per_100g: 48,
  serving_size_g: null,
  liquid: false,
  fiber_per_100g: null,
  sugars_per_100g: null,
  salt_per_100g: 1.2,
  saturated_fat_per_100g: null,
});

const blank: CustomProductDraft = draftFromPrefill(null);
const filled: CustomProductDraft = { ...blank, name: ' Хліб житній ', brand: ' ', kcal: '240', protein: '8,5', fat: '1', carbs: '48' };

describe('custom products: storage shape', () => {
  it('ids match the phone', () => expect(customProductId('4820024700016')).toBe('local:barcode:4820024700016'));
  it('a row maps to a product and back', () => {
    const p = rowToProduct(row('4820024700016'));
    expect(p.id).toBe('local:barcode:4820024700016');
    expect(p.saltPer100g).toBe(1.2);
    expect(p.additives).toEqual([]);
    expect(productToRow(p)).toEqual(row('4820024700016'));
  });
  it('the most likely form of the code wins', () => {
    const rows = [row('0012345678905', 'second'), row('012345678905', 'first')];
    expect(pickSaved(rows, ['012345678905', '0012345678905'])!.name).toBe('first');
    expect(pickSaved(rows, ['5000112637922'])).toBeNull();
    expect(pickSaved([], ['012345678905'])).toBeNull();
  });
  it('barcodes the table accepts', () => {
    expect(isStorableBarcode('48212345')).toBe(true);
    expect(isStorableBarcode('14820024700013')).toBe(true);
    expect(isStorableBarcode('4821234')).toBe(false);
    expect(isStorableBarcode('482002470001612')).toBe(false);
    expect(isStorableBarcode('48200247000a')).toBe(false);
    expect(isStorableBarcode('')).toBe(false);
  });
  it('search text is literal inside ILIKE', () => {
    expect(escapeLike('50%_off')).toBe('50\\%\\_off');
    expect(escapeLike('a\\b')).toBe('a\\\\b');
    expect(escapeLike('Молоко')).toBe('Молоко');
  });
});

describe('custom products: the form', () => {
  it('decimal comma, blanks and junk', () => {
    expect(parseDecimal('12,5')).toBe(12.5);
    expect(parseDecimal(' 12.5 ')).toBe(12.5);
    expect(parseDecimal('0')).toBe(0);
    expect(parseDecimal('.5')).toBe(0.5);
    expect(parseDecimal('')).toBeNull();
    expect(parseDecimal('  ')).toBeNull();
    expect(parseDecimal('1e3')).toBeNull();
    expect(parseDecimal('0x10')).toBeNull();
    expect(parseDecimal('abc')).toBeNull();
    expect(parseDecimal('1,2,3')).toBeNull();
  });

  it('a filled form becomes a product under the barcode', () => {
    const { product } = checkDraft(filled, '4820024700016');
    expect(product).toEqual({
      id: 'local:barcode:4820024700016',
      barcode: '4820024700016',
      name: 'Хліб житній',
      brand: null,
      kcalPer100g: 240,
      proteinPer100g: 8.5,
      fatPer100g: 1,
      carbsPer100g: 48,
      servingSizeG: null,
      liquid: false,
      fiberPer100g: null,
      sugarsPer100g: null,
      saltPer100g: null,
      saturatedFatPer100g: null,
      additives: [],
    });
  });

  it("the phone's ranges", () => {
    const at = (patch: Partial<CustomProductDraft>) => checkDraft({ ...filled, ...patch }, '4820024700016');
    expect(at({ name: '  ' }).product).toBeNull();
    expect(at({ name: '  ' }).ok.name).toBe(false);
    expect(at({ kcal: '900' }).product).not.toBeNull();
    expect(at({ kcal: '900,1' }).ok.kcal).toBe(false);
    expect(at({ kcal: '' }).ok.kcal).toBe(false);
    expect(at({ protein: '100' }).product).not.toBeNull();
    expect(at({ protein: '101' }).ok.protein).toBe(false);
    expect(at({ fat: '-1' }).ok.fat).toBe(false);
    expect(at({ carbs: 'x' }).ok.carbs).toBe(false);
    expect(at({ serving: '' }).product!.servingSizeG).toBeNull();
    expect(at({ serving: '0,5' }).ok.serving).toBe(false);
    expect(at({ serving: '1' }).product!.servingSizeG).toBe(1);
    expect(at({ serving: '5000' }).product!.servingSizeG).toBe(5000);
    expect(at({ serving: '5001' }).ok.serving).toBe(false);
    expect(at({ fiber: '' }).ok.fiber).toBe(true);
    expect(at({ sugars: '100,5' }).ok.sugars).toBe(false);
    expect(at({ salt: '2,5', saturatedFat: '0' }).product).toMatchObject({ saltPer100g: 2.5, saturatedFatPer100g: 0 });
    expect(at({ liquid: true, brand: ' Галичина ' }).product).toMatchObject({ liquid: true, brand: 'Галичина' });
  });

  it('starts from what Open Food Facts knows', () => {
    const prefill = toPrefill({ code: '4820024700016', product_name_uk: 'Хліб', brands: 'Київхліб, Інше', nutriments: { 'energy-kcal_100g': 240.4, proteins_100g: 8 } }, '4820024700016');
    const draft = draftFromPrefill(prefill);
    expect(draft).toEqual({ ...blank, name: 'Хліб', brand: 'Київхліб', kcal: '240', protein: '8' });
    // Fat and carbs are missing: the user must fill them in before saving.
    expect(checkDraft(draft, prefill.barcode).product).toBeNull();
    expect(checkDraft({ ...draft, fat: '1', carbs: '48' }, prefill.barcode).product).toMatchObject({ name: 'Хліб', kcalPer100g: 240, proteinPer100g: 8 });
  });

  it('a drink and a portion carry over; long fractions are shortened', () => {
    const draft = draftFromPrefill(
      toPrefill({ code: '4820024700016', quantity: '500 мл', serving_quantity: 250, nutriments: { 'energy-kcal_serving': 100, proteins_serving: 1 } }, '4820024700016'),
    );
    expect(draft).toMatchObject({ liquid: true, serving: '250', kcal: '40', protein: '0.4' });
    expect(draftFromPrefill(toPrefill({ code: '1', serving_quantity: 30, nutriments: { proteins_serving: 1 } }, '1')).protein).toBe('3.33');
  });

  it('an empty form for a product nobody knows', () => {
    expect(blank).toEqual({ name: '', brand: '', kcal: '', protein: '', fat: '', carbs: '', serving: '', liquid: false, fiber: '', sugars: '', salt: '', saturatedFat: '' });
    expect(checkDraft(blank, '4820024700016').product).toBeNull();
  });
});
