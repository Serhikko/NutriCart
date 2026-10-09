import { describe, expect, it, vi } from 'vitest';
import { OffError, lookupBarcode } from '../openFoodFacts';
import { rowToProduct } from '../../domain/customProduct';

/** A fake Open Food Facts: code -> answer. A missing code is a 404. */
function off(answers: Record<string, { status?: number; product?: Record<string, unknown> } | 'error'>) {
  const asked: string[] = [];
  const fetchOff = vi.fn(async (path: string) => {
    const code = decodeURIComponent(path.split('/api/v2/product/')[1].split('?')[0]);
    asked.push(code);
    const answer = answers[code];
    if (answer === 'error') throw new OffError('server: HTTP 503', 503, 'server');
    if (!answer) return { status: 404, json: async () => ({ status: 0 }) } as unknown as Response;
    return { status: 200, json: async () => answer } as unknown as Response;
  });
  return { fetchOff, asked };
}

const complete = (code: string, name = 'Kefir') => ({
  status: 1,
  product: { code, product_name: name, nutriments: { 'energy-kcal_100g': 50, proteins_100g: 3, fat_100g: 1, carbohydrates_100g: 4 } },
});
const incomplete = (code: string, name: string) => ({
  status: 1,
  product: { code, product_name_uk: name, nutriments: { 'energy-kcal_100g': 240 } },
});
const saved = rowToProduct({
  barcode: '0012345678905',
  name: 'Мій хліб',
  brand: null,
  kcal_per_100g: 240,
  protein_per_100g: 8,
  fat_per_100g: 1,
  carbs_per_100g: 48,
  serving_size_g: null,
  liquid: false,
  fiber_per_100g: null,
  sugars_per_100g: null,
  salt_per_100g: null,
  saturated_fat_per_100g: null,
});

describe('barcode lookup order', () => {
  it("the user's own product wins over Open Food Facts, under any form of the code", async () => {
    const { fetchOff } = off({ '012345678905': complete('012345678905') });
    const savedFor = vi.fn(async (candidates: string[]) => (candidates.includes('0012345678905') ? saved : null));
    const result = await lookupBarcode('012345678905', { saved: savedFor, fetchOff });
    expect(result).toEqual({ kind: 'found', product: saved });
    expect(savedFor).toHaveBeenCalledWith(['012345678905', '0012345678905']);
    expect(fetchOff).not.toHaveBeenCalled();
  });

  it('a failing saved-products lookup never blocks the scan', async () => {
    const { fetchOff } = off({ '5000112637922': complete('5000112637922') });
    const result = await lookupBarcode('5000112637922', { saved: () => Promise.reject(new Error('relation does not exist')), fetchOff });
    expect(result.kind).toBe('found');
  });

  it('an incomplete form, then a complete one: found', async () => {
    const { fetchOff, asked } = off({
      '012345678905': incomplete('012345678905', 'Хліб'),
      '0012345678905': complete('0012345678905', 'Bread'),
    });
    const result = await lookupBarcode('012345678905', { saved: async () => null, fetchOff });
    expect(result.kind === 'found' && result.product.name).toBe('Bread');
    expect(asked).toEqual(['012345678905', '0012345678905']);
  });

  it('only incomplete forms: the first prefill', async () => {
    const { fetchOff } = off({
      '012345678905': incomplete('012345678905', 'Перший'),
      '0012345678905': incomplete('0012345678905', 'Другий'),
    });
    const result = await lookupBarcode('012345678905', { fetchOff });
    expect(result.kind).toBe('incomplete');
    expect(result.kind === 'incomplete' && result.prefill).toMatchObject({ barcode: '012345678905', name: 'Перший', kcalPer100g: 240, proteinPer100g: null });
  });

  it('a 404 and a "status 0" are both "try the next form"', async () => {
    const { fetchOff, asked } = off({
      '012345678905': { status: 0, product: { code: '012345678905', product_name: 'ghost' } },
      '0012345678905': complete('0012345678905'),
    });
    expect((await lookupBarcode('012345678905', { fetchOff })).kind).toBe('found');
    expect(asked).toEqual(['012345678905', '0012345678905']);
  });

  it('nothing anywhere: not found, with the scanned digits and their origin', async () => {
    expect(await lookupBarcode('4820024700016', { saved: async () => null, fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE' });
    expect(await lookupBarcode('4810268000013', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '4810268000013', country: 'BELARUS' });
    expect(await lookupBarcode('14820024700013', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '14820024700013', country: 'UKRAINE' });
    expect(await lookupBarcode('012345678905', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '012345678905', country: null });
    expect(await lookupBarcode(' 482-0024-700016 ', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE' });
  });

  it('a product without a name is incomplete, not missing', async () => {
    const { fetchOff } = off({ '4820024700016': { status: 1, product: { code: '4820024700016', nutriments: { 'energy-kcal_100g': 50, proteins_100g: 3, fat_100g: 1, carbohydrates_100g: 4 } } } });
    const result = await lookupBarcode('4820024700016', { fetchOff });
    expect(result.kind === 'incomplete' && result.prefill).toMatchObject({ name: null, kcalPer100g: 50, carbsPer100g: 4 });
  });

  it('a Ukrainian product with only a Ukrainian name is found', async () => {
    const { fetchOff } = off({
      '4820024700016': { status: 1, product: { code: '4820024700016', product_name: '', product_name_uk: 'Кефір 2,5%', quantity: '900 мл', nutriments: { 'energy-kcal_100g': 53, proteins_100g: 2.8, fat_100g: 2.5, carbohydrates_100g: 4.7 } } },
    });
    const result = await lookupBarcode('4820024700016', { fetchOff });
    expect(result.kind === 'found' && result.product).toMatchObject({ id: 'off:4820024700016', name: 'Кефір 2,5%', liquid: true });
  });

  it('Open Food Facts failing is still an error (the offline message)', async () => {
    const { fetchOff } = off({ '4820024700016': 'error' });
    await expect(lookupBarcode('4820024700016', { saved: async () => null, fetchOff })).rejects.toBeInstanceOf(OffError);
  });

  it('asks for the per-language name fields', async () => {
    const { fetchOff } = off({});
    await lookupBarcode('4820024700016', { fetchOff });
    const path = fetchOff.mock.calls[0][0];
    expect(path).toContain('product_name_uk');
    expect(path).toContain('generic_name_be');
  });
});
