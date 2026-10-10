import { afterEach, describe, expect, it, vi } from 'vitest';
import { OffError, lookupBarcode, offFetch, type LookupSources } from '../openFoodFacts';
import { rowToProduct } from '../../domain/customProduct';
import type { ShopAnswer } from '../../domain/zakaz';

type Body = { status?: number; product?: Record<string, unknown> };
/** What the fake OFF does for one request. */
type Answer = Body | { busy: number | null } | 'busy response' | 'offline' | 'http 500' | 'not json';

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

/**
 * A fake Open Food Facts: code -> answer, or a list of answers for the first,
 * second... request of that code (the last one repeats). A missing code is a 404.
 */
function off(answers: Record<string, Answer | Answer[]>) {
  const asked: string[] = [];
  const fetchOff = vi.fn(async (path: string) => {
    const code = decodeURIComponent(path.split('/api/v2/product/')[1].split('?')[0]);
    const n = asked.filter((c) => c === code).length;
    asked.push(code);
    const entry = answers[code];
    const answer = Array.isArray(entry) ? entry[Math.min(n, entry.length - 1)] : entry;
    if (answer === undefined) return json({ status: 0 }, 404);
    if (answer === 'offline') throw new OffError('server: network; direct: network', null, 'direct');
    if (answer === 'http 500') throw new OffError('server: HTTP 500', 500, 'server');
    if (answer === 'busy response') return new Response('', { status: 503, headers: { 'retry-after': '1' } });
    if (answer === 'not json') return new Response('<html>', { status: 200 });
    if ('busy' in answer) throw new OffError('server: HTTP 429', 429, 'server', answer.busy);
    return json(answer);
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

const UA = '4823090100292';
const UK = '5000112637922';
const BY = '4810268000013';
const nutella = {
  title: 'Нутелла паста горіхова 350г',
  producer: { trademark: 'Nutella' },
  unit: 'pcs',
  nutrition_facts: { ingredient_energy: '539.00ккал', ingredient_protein: '6,3г', ingredient_fat: '30,9г', ingredient_carbohydrates: '57,5г' },
};

function shops(answer: ShopAnswer) {
  return vi.fn<(code14: string, signal?: AbortSignal) => Promise<ShopAnswer>>(async () => answer);
}
const noPause = () => vi.fn<(seconds: number, signal?: AbortSignal) => Promise<void>>(async () => undefined);

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
    const { fetchOff } = off({ [UK]: complete(UK) });
    const result = await lookupBarcode(UK, { saved: () => Promise.reject(new Error('relation does not exist')), fetchOff });
    expect(result.kind).toBe('found');
  });

  it('OFF is asked once per form that differs by more than leading zeros; own products under every form', async () => {
    const { fetchOff, asked } = off({});
    const savedFor = vi.fn(async () => null);
    await lookupBarcode('012345678905', { saved: savedFor, fetchOff });
    expect(asked).toEqual(['012345678905']);
    expect(savedFor).toHaveBeenCalledWith(['012345678905', '0012345678905']);

    const upcE = off({});
    await lookupBarcode('01234565', { fetchOff: upcE.fetchOff });
    expect(upcE.asked).toEqual(['01234565', '012345000065']);
  });

  it('an incomplete form, then a complete one: found', async () => {
    const { fetchOff, asked } = off({
      '14820024700013': incomplete('14820024700013', 'Хліб'),
      '4820024700013': complete('4820024700013', 'Bread'),
    });
    const result = await lookupBarcode('14820024700013', { saved: async () => null, fetchOff });
    expect(result.kind === 'found' && result.product.name).toBe('Bread');
    expect(asked).toEqual(['14820024700013', '4820024700013']);
  });

  it('only incomplete forms: the first prefill, from OFF', async () => {
    const { fetchOff } = off({
      '01234565': incomplete('01234565', 'Перший'),
      '012345000065': incomplete('012345000065', 'Другий'),
    });
    const result = await lookupBarcode('01234565', { fetchOff });
    expect(result).toMatchObject({ kind: 'incomplete', from: 'off', prefill: { barcode: '01234565', name: 'Перший', kcalPer100g: 240, proteinPer100g: null } });
  });

  it('a 404 and a "status 0" are both "try the next form"', async () => {
    const { fetchOff, asked } = off({
      '14820024700013': { status: 0, product: { code: '14820024700013', product_name: 'ghost' } },
      '4820024700013': complete('4820024700013'),
    });
    expect((await lookupBarcode('14820024700013', { fetchOff })).kind).toBe('found');
    expect(asked).toEqual(['14820024700013', '4820024700013']);
  });

  it('nothing anywhere: not found, with the scanned digits, their origin, and nobody failing', async () => {
    const nobody = { offUnavailable: false, shops: 'NOT_ASKED' };
    expect(await lookupBarcode('4820024700016', { saved: async () => null, fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE', ...nobody });
    expect(await lookupBarcode(BY, { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: BY, country: 'BELARUS', ...nobody });
    expect(await lookupBarcode('14820024700013', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '14820024700013', country: 'UKRAINE', ...nobody });
    expect(await lookupBarcode('012345678905', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '012345678905', country: null, ...nobody });
    expect(await lookupBarcode(' 482-0024-700016 ', { fetchOff: off({}).fetchOff })).toEqual({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE', ...nobody });
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

  it("OFF's estimates prefill the form, they never make a product", async () => {
    const { fetchOff } = off({
      [UA]: { status: 1, product: { code: UA, product_name_uk: 'Цукерки', nutriments: {}, nutriments_estimated: { 'energy-kcal_100g': 400, proteins_100g: 5, fat_100g: 20, carbohydrates_100g: 50 } } },
    });
    const result = await lookupBarcode(UA, { fetchOff });
    expect(result).toMatchObject({ kind: 'incomplete', from: 'off', prefill: { name: 'Цукерки', kcalPer100g: 400, proteinPer100g: 5, fatPer100g: 20, carbsPer100g: 50, estimated: true } });
  });

  // Shared with the phone's BarcodeLookupTest: an OFF record with no name and no core value is no prefill.
  it('a bare OFF stub is passed over for a later form that knows something', async () => {
    const { fetchOff } = off({
      '14820024700013': { status: 1, product: { code: '14820024700013' } },
      '4820024700013': incomplete('4820024700013', 'Хліб'),
    });
    const result = await lookupBarcode('14820024700013', { fetchOff });
    expect(result).toMatchObject({ kind: 'incomplete', from: 'off', prefill: { barcode: '4820024700013', name: 'Хліб', kcalPer100g: 240 } });
  });

  it('a bare OFF stub and nothing else: not found, not an empty form', async () => {
    const result = await lookupBarcode(UK, { fetchOff: off({ [UK]: { status: 1, product: { code: UK, brands: 'Somebody', nutriments: {} } } }).fetchOff });
    expect(result).toEqual({ kind: 'not_found', barcode: UK, country: null, offUnavailable: false, shops: 'NOT_ASKED' });
  });

  it('asks for the per-language name fields and the estimates', async () => {
    const { fetchOff } = off({});
    await lookupBarcode('4820024700016', { fetchOff });
    const path = fetchOff.mock.calls[0][0];
    expect(path).toContain('product_name_uk');
    expect(path).toContain('generic_name_be');
    expect(path).toContain('nutriments_estimated');
  });
});

describe('a busy or failing Open Food Facts', () => {
  it('busy, then an answer: asked again once, after a short pause', async () => {
    const { fetchOff, asked } = off({ [UK]: [{ busy: null }, complete(UK)] });
    const pause = noPause();
    const result = await lookupBarcode(UK, { fetchOff, pause });
    expect(result.kind).toBe('found');
    expect(asked).toEqual([UK, UK]);
    expect(pause).toHaveBeenCalledTimes(1);
    expect(pause.mock.calls[0][0]).toBe(2);
  });

  it("waits as long as OFF's Retry-After says, at least a second", async () => {
    const three = noPause();
    await lookupBarcode(UK, { fetchOff: off({ [UK]: [{ busy: 3 }, complete(UK)] }).fetchOff, pause: three });
    expect(three.mock.calls[0][0]).toBe(3);
    const zero = noPause();
    await lookupBarcode(UK, { fetchOff: off({ [UK]: [{ busy: 0 }, complete(UK)] }).fetchOff, pause: zero });
    expect(zero.mock.calls[0][0]).toBe(1);
  });

  it('a 503 answered as a response counts as busy too', async () => {
    const pause = noPause();
    const result = await lookupBarcode(UK, { fetchOff: off({ [UK]: ['busy response', complete(UK)] }).fetchOff, pause });
    expect(result.kind).toBe('found');
    expect(pause.mock.calls[0][0]).toBe(1);
  });

  it('a Retry-After over five seconds is not waited for: OFF counts as unavailable', async () => {
    const { fetchOff, asked } = off({ [UK]: [{ busy: 30 }, complete(UK)] });
    const pause = noPause();
    const result = await lookupBarcode(UK, { fetchOff, pause });
    expect(result).toEqual({ kind: 'not_found', barcode: UK, country: null, offUnavailable: true, shops: 'NOT_ASKED' });
    expect(asked).toEqual([UK]);
    expect(pause).not.toHaveBeenCalled();
  });

  it('busy twice: OFF stops, the shops are still asked, and the result says OFF did not answer', async () => {
    const { fetchOff, asked } = off({ [UA]: { busy: null } });
    const shopsMiss = shops({ kind: 'miss' });
    const result = await lookupBarcode(UA, { fetchOff, shops: shopsMiss, pause: noPause() });
    expect(asked).toEqual([UA, UA]);
    expect(shopsMiss).toHaveBeenCalledWith('04823090100292', undefined);
    expect(result).toEqual({ kind: 'not_found', barcode: UA, country: 'UKRAINE', offUnavailable: true, shops: 'MISS' });
  });

  it('only one retry per scan, across forms', async () => {
    const { fetchOff, asked } = off({ '14820024700013': [{ busy: null }, { status: 0 }], '4820024700013': { busy: null } });
    const pause = noPause();
    const result = await lookupBarcode('14820024700013', { fetchOff, pause });
    expect(asked).toEqual(['14820024700013', '14820024700013', '4820024700013']);
    expect(pause).toHaveBeenCalledTimes(1);
    expect(result).toMatchObject({ kind: 'not_found', offUnavailable: true });
  });

  it.each(['http 500', 'not json'] as const)('%s: no retry, OFF unavailable, the scan carries on', async (failure) => {
    const { fetchOff, asked } = off({ [UA]: failure });
    const pause = noPause();
    const result = await lookupBarcode(UA, { fetchOff, pause, shops: shops({ kind: 'miss' }) });
    expect(asked).toEqual([UA]);
    expect(pause).not.toHaveBeenCalled();
    expect(result).toMatchObject({ kind: 'not_found', offUnavailable: true, shops: 'MISS' });
  });

  it('a failure after a partial product still gives the partial product', async () => {
    const { fetchOff } = off({ '14820024700013': incomplete('14820024700013', 'Хліб'), '4820024700013': 'offline' });
    const result = await lookupBarcode('14820024700013', { fetchOff });
    expect(result).toMatchObject({ kind: 'incomplete', from: 'off', prefill: { name: 'Хліб' } });
  });

  it('offline, but the shops answered: not found, not "offline"', async () => {
    const result = await lookupBarcode(UA, { fetchOff: off({ [UA]: 'offline' }).fetchOff, shops: shops({ kind: 'miss' }) });
    expect(result).toEqual({ kind: 'not_found', barcode: UA, country: 'UKRAINE', offUnavailable: true, shops: 'MISS' });
  });

  it('offline with the shops not asked, or not answering: the offline error', async () => {
    await expect(lookupBarcode(UK, { fetchOff: off({ [UK]: 'offline' }).fetchOff, shops: shops({ kind: 'miss' }) })).rejects.toBeInstanceOf(OffError);
    await expect(lookupBarcode(UA, { fetchOff: off({ [UA]: 'offline' }).fetchOff })).rejects.toBeInstanceOf(OffError);
    await expect(lookupBarcode(UA, { fetchOff: off({ [UA]: 'offline' }).fetchOff, shops: shops({ kind: 'unavailable' }) })).rejects.toMatchObject({ status: null });
  });

  it('an abandoned scan stops while waiting', async () => {
    const controller = new AbortController();
    const fetchOff = vi.fn(async () => {
      controller.abort();
      throw new OffError('server: HTTP 429', 429, 'server');
    });
    await expect(lookupBarcode(UK, { fetchOff }, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetchOff).toHaveBeenCalledTimes(1);
  });
});

describe('the Ukrainian shops', () => {
  it('a usable card is found, with a zakaz id, once OFF has nothing', async () => {
    const ask = shops({ kind: 'hits', cards: [{ product: nutella }] });
    const result = await lookupBarcode(UA, { fetchOff: off({}).fetchOff, shops: ask });
    expect(ask).toHaveBeenCalledWith('04823090100292', undefined);
    expect(result).toMatchObject({ kind: 'found', product: { id: 'zakaz:4823090100292', barcode: UA, name: 'Нутелла паста горіхова 350г', brand: 'Nutella', kcalPer100g: 539, fiberPer100g: null } });
  });

  it('are not asked when OFF has a usable product', async () => {
    const ask = shops({ kind: 'hits', cards: [nutella] });
    expect((await lookupBarcode(UA, { fetchOff: off({ [UA]: complete(UA) }).fetchOff, shops: ask })).kind).toBe('found');
    expect(ask).not.toHaveBeenCalled();
  });

  it("OFF's partial product is preferred over a shop's partial one", async () => {
    const ask = shops({ kind: 'hits', cards: [{ title: 'Нутелла', nutrition_facts: { ingredient_energy: '539ккал' } }] });
    const result = await lookupBarcode(UA, { fetchOff: off({ [UA]: incomplete(UA, 'Нутела з OFF') }).fetchOff, shops: ask });
    expect(ask).toHaveBeenCalled();
    expect(result).toMatchObject({ kind: 'incomplete', from: 'off', prefill: { name: 'Нутела з OFF' } });
  });

  it("a shop's partial product beats a bare OFF stub", async () => {
    const { ingredient_carbohydrates: _dropped, ...facts } = nutella.nutrition_facts;
    const ask = shops({ kind: 'hits', cards: [{ ...nutella, nutrition_facts: facts }] });
    const result = await lookupBarcode(UA, { fetchOff: off({ [UA]: { status: 1, product: { code: UA } } }).fetchOff, shops: ask });
    expect(result).toEqual({
      kind: 'incomplete',
      from: 'shops',
      prefill: {
        barcode: UA,
        name: 'Нутелла паста горіхова 350г',
        brand: 'Nutella',
        kcalPer100g: 539,
        proteinPer100g: 6.3,
        fatPer100g: 30.9,
        carbsPer100g: null,
        servingSizeG: null,
        liquid: false,
        fiberPer100g: null,
        sugarsPer100g: null,
        saltPer100g: null,
        saturatedFatPer100g: null,
      },
    });
  });

  it("a shop's partial product when OFF has none, but a usable one beats OFF's partial product", async () => {
    const partial = shops({ kind: 'hits', cards: [{ title: 'Шпинат', unit: 'kg', nutrition_facts: { ingredient_energy: '96ккал', ingredient_protein: '2,9', ingredient_fat: '0,4', ingredient_carbohydrates: '3,6' } }] });
    expect(await lookupBarcode(UA, { fetchOff: off({}).fetchOff, shops: partial })).toMatchObject({ kind: 'incomplete', from: 'shops', prefill: { barcode: UA, name: 'Шпинат', kcalPer100g: 96 } });
    const usable = shops({ kind: 'hits', cards: [nutella] });
    expect(await lookupBarcode(UA, { fetchOff: off({ [UA]: incomplete(UA, 'Нутела') }).fetchOff, shops: usable })).toMatchObject({ kind: 'found', product: { id: 'zakaz:4823090100292' } });
  });

  it('a listing with nothing usable is a miss', async () => {
    const result = await lookupBarcode(UA, { fetchOff: off({}).fetchOff, shops: shops({ kind: 'hits', cards: [{}] }) });
    expect(result).toEqual({ kind: 'not_found', barcode: UA, country: 'UKRAINE', offUnavailable: false, shops: 'MISS' });
  });

  it('shops that do not answer, or throw, are unavailable', async () => {
    expect(await lookupBarcode(UA, { fetchOff: off({}).fetchOff, shops: shops({ kind: 'unavailable' }) })).toMatchObject({ kind: 'not_found', offUnavailable: false, shops: 'UNAVAILABLE' });
    const throwing: LookupSources['shops'] = async () => {
      throw new TypeError('Failed to fetch');
    };
    expect(await lookupBarcode(UA, { fetchOff: off({}).fetchOff, shops: throwing })).toMatchObject({ kind: 'not_found', shops: 'UNAVAILABLE' });
  });

  it.each([BY, UK, '5091234567890', '50123452'])('are never asked about %s (Belarusian and UK numbers)', async (code) => {
    const ask = shops({ kind: 'hits', cards: [nutella] });
    const result = await lookupBarcode(code, { fetchOff: off({}).fetchOff, shops: ask });
    expect(ask).not.toHaveBeenCalled();
    expect(result).toMatchObject({ kind: 'not_found', shops: 'NOT_ASKED' });
  });

  it('are asked about imports and UPC-A codes, with the GTIN-14', async () => {
    const ask = shops({ kind: 'miss' });
    await lookupBarcode('4006381333931', { fetchOff: off({}).fetchOff, shops: ask });
    await lookupBarcode('012345678905', { fetchOff: off({}).fetchOff, shops: ask });
    expect(ask.mock.calls.map((c) => c[0])).toEqual(['04006381333931', '00012345678905']);
  });
});

describe('offFetch: what a failure of both routes says', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('a route that answered beats one that could not be reached, with its Retry-After', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/off')) return new Response('', { status: 429, headers: { 'retry-after': '4' } });
        throw new TypeError('Failed to fetch');
      }),
    );
    const error = await offFetch('/api/v2/product/1').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(OffError);
    expect(error).toMatchObject({ status: 429, via: 'server', retryAfter: 4 });
    expect((error as OffError).message).toContain('direct: network');
  });

  it('busy on either route wins, so the lookup can wait and ask again', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => (url.startsWith('/api/off') ? new Response('', { status: 502 }) : new Response('', { status: 429 }))),
    );
    await expect(offFetch('/api/v2/product/1')).rejects.toMatchObject({ status: 429, via: 'direct' });
  });

  it('neither route reached: status null', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );
    await expect(offFetch('/api/v2/product/1')).rejects.toMatchObject({ status: null });
  });

  it('a Retry-After that is a date is ignored', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 503, headers: { 'retry-after': 'Wed, 21 Oct 2026 07:28:00 GMT' } })));
    await expect(offFetch('/api/v2/product/1')).rejects.toMatchObject({ status: 503, retryAfter: null });
  });
});
