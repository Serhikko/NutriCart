import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { OffError, type BarcodeLookup, type LookupSources } from '../../lib/openFoodFacts';
import type { FoodProduct } from '../../domain/openFoodFacts';
import { toPrefill } from '../../domain/openFoodFacts';
import { fromZakazCard } from '../../domain/zakaz';

const lookupBarcode = vi.fn<(code: string, sources?: LookupSources) => Promise<BarcodeLookup>>();
const searchProducts = vi.fn<(q: string) => Promise<FoodProduct[]>>();
const saveCustomProduct = vi.fn<(userId: string | null, p: FoodProduct) => Promise<void>>();
const searchCustomProducts = vi.fn<(userId: string | null, q: string) => Promise<FoodProduct[]>>();

vi.mock('../../lib/openFoodFacts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/openFoodFacts')>()),
  lookupBarcode: (code: string, sources?: LookupSources) => lookupBarcode(code, sources),
  searchProducts: (q: string) => searchProducts(q),
}));
vi.mock('../../lib/customProducts', () => ({
  savedProductFor: async () => null,
  saveCustomProduct: (userId: string | null, p: FoodProduct) => saveCustomProduct(userId, p),
  searchCustomProducts: (userId: string | null, q: string) => searchCustomProducts(userId, q),
}));
vi.mock('../../lib/myTargets', () => ({ useMyTargets: () => ({ userId: 'u1', summary: { targetKcal: 2000, primaryClient: 'web' } }) }));
vi.mock('../../lib/tracker', () => ({ useLogFood: () => ({ mutate: vi.fn() }) }));
const session = vi.hoisted(() => ({ current: null as { access_token: string } | null }));
vi.mock('../../lib/session', () => ({ useSession: () => ({ session: session.current }) }));

const { AddFood } = await import('../AddFood');

function renderPage() {
  localStorage.setItem('nutricart.locale', 'en');
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={['/me/add/20000/LUNCH']}>
        <Routes>
          <Route path="/me/add/:epochDay/:slot" element={<AddFood />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

function typeBarcode(code: string) {
  fireEvent.change(screen.getByLabelText('Or type the barcode'), { target: { value: code } });
  fireEvent.click(screen.getByRole('button', { name: 'Look up' }));
}

const field = (label: string) => screen.getByLabelText(label) as HTMLInputElement;

beforeEach(() => {
  vi.clearAllMocks();
  searchCustomProducts.mockResolvedValue([]);
  session.current = null;
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

const nobodyFailed = { offUnavailable: false, shops: 'NOT_ASKED' } as const;

describe('AddFood barcode outcomes', () => {
  it('not found: the origin message, then an empty form with the barcode, and logging even when saving fails', async () => {
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE', ...nobodyFailed });
    saveCustomProduct.mockRejectedValue(new Error('relation "custom_products" does not exist'));
    renderPage();
    typeBarcode('4820024700016');

    expect(await screen.findByText(/This Ukrainian product isn't in Open Food Facts yet/)).toBeInTheDocument();
    expect(screen.getByText('Barcode 4820024700016 (Ukraine)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add this product' }));

    expect(screen.getByText('Barcode 4820024700016 (Ukraine)')).toBeInTheDocument();
    const save = screen.getByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    fireEvent.change(field('Name'), { target: { value: 'Ряжанка' } });
    fireEvent.click(screen.getByLabelText('A drink (amounts in ml)'));
    fireEvent.change(field('Calories (kcal / 100 ml)'), { target: { value: '85' } });
    fireEvent.change(field('Protein (g / 100 ml)'), { target: { value: '2,9' } });
    fireEvent.change(field('Fat (g / 100 ml)'), { target: { value: '4' } });
    fireEvent.change(field('Carbs (g / 100 ml)'), { target: { value: '4,2' } });
    expect(save).toBeEnabled();
    fireEvent.click(save);

    expect(await screen.findByText(/couldn't be saved for the next scan/)).toBeInTheDocument();
    expect(saveCustomProduct).toHaveBeenCalledWith('u1', expect.objectContaining({ id: 'local:barcode:4820024700016', name: 'Ряжанка', liquid: true, proteinPer100g: 2.9, carbsPer100g: 4.2 }));
    expect(screen.getByRole('dialog', { name: 'Ряжанка' })).toBeInTheDocument();
    expect(screen.getByText('Per 100 ml')).toBeInTheDocument();
  });

  it('incomplete: the form is prefilled, saved, and goes straight to the amount', async () => {
    const prefill = toPrefill({ code: '4810268000013', product_name_be: 'Сырок', brands: 'Савушкин', nutriments: { 'energy-kcal_100g': 350, proteins_100g: 8 } }, '4810268000013');
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill, from: 'off' });
    saveCustomProduct.mockResolvedValue();
    renderPage();
    typeBarcode('4810268000013');

    expect(await screen.findByText(/Open Food Facts knows this product, but not all of its nutrition/)).toBeInTheDocument();
    expect(screen.getByText('Barcode 4810268000013 (Belarus)')).toBeInTheDocument();
    expect(field('Name').value).toBe('Сырок');
    expect(field('Brand (optional)').value).toBe('Савушкин');
    expect(field('Calories (kcal / 100 g)').value).toBe('350');
    expect(field('Protein (g / 100 g)').value).toBe('8');
    expect(field('Fat (g / 100 g)').value).toBe('');
    fireEvent.change(field('Fat (g / 100 g)'), { target: { value: '23' } });
    fireEvent.change(field('Carbs (g / 100 g)'), { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('dialog', { name: 'Сырок' })).toBeInTheDocument();
    expect(screen.queryByText(/couldn't be saved/)).not.toBeInTheDocument();
    expect(saveCustomProduct).toHaveBeenCalledWith('u1', expect.objectContaining({ id: 'local:barcode:4810268000013', brand: 'Савушкин', fatPer100g: 23 }));
  });

  it.each([
    ['succeeds', () => saveCustomProduct.mockResolvedValue()],
    ['fails', () => saveCustomProduct.mockRejectedValue(new Error('relation "custom_products" does not exist'))],
  ])('after saving a not-found product (save %s), cancelling the amount does not bring back the not-found card', async (_label, arrange) => {
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE', ...nobodyFailed });
    arrange();
    renderPage();
    typeBarcode('4820024700016');
    expect(await screen.findByText(/This Ukrainian product isn't in Open Food Facts yet/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add this product' }));
    fireEvent.change(field('Name'), { target: { value: 'Кефір' } });
    fireEvent.change(field('Calories (kcal / 100 g)'), { target: { value: '50' } });
    fireEvent.change(field('Protein (g / 100 g)'), { target: { value: '3' } });
    fireEvent.change(field('Fat (g / 100 g)'), { target: { value: '1' } });
    fireEvent.change(field('Carbs (g / 100 g)'), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    const dialog = await screen.findByRole('dialog', { name: 'Кефір' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(screen.getByLabelText('Search food…')).toBeInTheDocument();
    expect(screen.queryByText(/isn't in Open Food Facts yet/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add this product' })).not.toBeInTheDocument();
    expect(screen.queryByText(/couldn't be saved/)).not.toBeInTheDocument();
  });

  it('cancel goes back to the search screen', async () => {
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '5000112637922', country: null, ...nobodyFailed });
    renderPage();
    typeBarcode('5000112637922');
    expect(await screen.findByText(/^This product isn't in Open Food Facts yet/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add this product' }));
    expect(screen.getByText('Barcode 5000112637922')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByLabelText('Search food…')).toBeInTheDocument();
    expect(screen.queryByText(/isn't in Open Food Facts yet/)).not.toBeInTheDocument();
  });

  it("name search lists the user's own products before Open Food Facts", async () => {
    const mine = { id: 'local:barcode:4820024700016', barcode: '4820024700016', name: 'Мій кефір', brand: null, kcalPer100g: 50, proteinPer100g: 3, fatPer100g: 1, carbsPer100g: 4, servingSizeG: null, liquid: true, fiberPer100g: null, sugarsPer100g: null, saltPer100g: null, saturatedFatPer100g: null, additives: [] };
    searchCustomProducts.mockResolvedValue([mine]);
    searchProducts.mockResolvedValue([{ ...mine, id: 'off:1', barcode: '1', name: 'Kefir OFF' }]);
    renderPage();
    fireEvent.change(screen.getByLabelText('Search food…'), { target: { value: 'кеф' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(screen.getByText('Kefir OFF')).toBeInTheDocument());
    const names = screen.getAllByRole('button').map((b) => b.textContent ?? '').filter((t) => t.includes('Мій кефір') || t.includes('Kefir OFF'));
    expect(names[0]).toContain('Мій кефір');
    expect(searchCustomProducts).toHaveBeenCalledWith('u1', 'кеф');
  });
});

const UA = '4823090100292';
const nutellaCard = {
  title: 'Нутелла паста горіхова 350г',
  producer: { trademark: 'Nutella' },
  unit: 'pcs',
  nutrition_facts: { ingredient_energy: '539.00ккал', ingredient_protein: '6,3г', ingredient_fat: '30,9г', ingredient_carbohydrates: '57,5г' },
};

describe('AddFood: which sources answered', () => {
  // Like the phone: every message has the Add action and nothing else; a source that did not answer is said so.
  it.each([
    ['OFF_DOWN', { country: null, offUnavailable: true, shops: 'NOT_ASKED' }, /^Open Food Facts didn't answer \(it may be busy\)/],
    ['OFF_DOWN_SHOPS_MISS', { country: 'UKRAINE', offUnavailable: true, shops: 'MISS' }, /^Ukrainian shops don't list this barcode, and Open Food Facts didn't answer/],
    ['NOT_FOUND_UKRAINE_SHOPS', { country: 'UKRAINE', offUnavailable: false, shops: 'MISS' }, /^This Ukrainian product isn't in Open Food Facts or Ukrainian shops' catalogues yet/],
    ['NOT_FOUND_OTHER_SHOPS', { country: null, offUnavailable: false, shops: 'MISS' }, /^This product isn't in Open Food Facts or Ukrainian shops' catalogues yet/],
    ['NOT_FOUND_SHOPS_DOWN', { country: 'UKRAINE', offUnavailable: false, shops: 'UNAVAILABLE' }, /^This product isn't in Open Food Facts, and Ukrainian shops didn't answer/],
    ['NOT_FOUND_BELARUS', { country: 'BELARUS', offUnavailable: false, shops: 'NOT_ASKED' }, /^This Belarusian product isn't in Open Food Facts yet/],
  ] as const)('%s: its message and the Add button', async (_notice, outcome, text) => {
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: UA, ...outcome });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add this product' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('a code too long to add a product under: OFF not answering is still said, never "no product"', async () => {
    const long = '482002470001612';
    lookupBarcode.mockResolvedValueOnce({ kind: 'not_found', barcode: long, country: 'UKRAINE', offUnavailable: true, shops: 'NOT_ASKED' });
    renderPage();
    typeBarcode(long);
    expect(await screen.findByText(/^Open Food Facts didn't answer/)).toBeInTheDocument();
    expect(screen.queryByText('No product with that barcode.')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add this product' })).not.toBeInTheDocument();

    lookupBarcode.mockResolvedValueOnce({ kind: 'not_found', barcode: long, country: 'UKRAINE', ...nobodyFailed });
    typeBarcode(long);
    expect(await screen.findByText('No product with that barcode.')).toBeInTheDocument();
  });

  it('a product found in Open Food Facts never shows the shop line', async () => {
    lookupBarcode.mockResolvedValue({ kind: 'found', product: { id: 'off:5000112637922', barcode: '5000112637922', name: 'Tea', brand: null, kcalPer100g: 1, proteinPer100g: 0, fatPer100g: 0, carbsPer100g: 0, servingSizeG: null, liquid: true, fiberPer100g: null, sugarsPer100g: null, saltPer100g: null, saturatedFatPer100g: null, additives: [] } });
    renderPage();
    typeBarcode('5000112637922');
    expect(await screen.findByRole('dialog', { name: 'Tea' })).toBeInTheDocument();
    expect(screen.queryByText('Source: Ukrainian shop catalogue (zakaz.ua)')).not.toBeInTheDocument();
  });

  it("a shop's partial product says it comes from a shop, not Open Food Facts", async () => {
    const { ingredient_carbohydrates: _dropped, ...facts } = nutellaCard.nutrition_facts;
    const match = fromZakazCard({ ...nutellaCard, nutrition_facts: facts }, UA);
    if (match?.kind !== 'prefill') throw new Error('expected a prefill');
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill: match.prefill, from: 'shops' });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText(/^A Ukrainian shop lists this product, but not all of its nutrition/)).toBeInTheDocument();
    expect(screen.queryByText(/Open Food Facts/)).not.toBeInTheDocument();
    expect(field('Name').value).toBe('Нутелла паста горіхова 350г');
    expect(field('Calories (kcal / 100 g)').value).toBe('539');
    expect(field('Carbs (g / 100 g)').value).toBe('');
  });

  it("a shop's values that don't add up: nothing is missing, so the line asks for a check", async () => {
    const match = fromZakazCard({ title: 'Шпинат', unit: 'kg', nutrition_facts: { ingredient_energy: '96ккал', ingredient_protein: '2,9', ingredient_fat: '0,4', ingredient_carbohydrates: '3,6' } }, UA);
    if (match?.kind !== 'prefill') throw new Error('expected a prefill');
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill: match.prefill, from: 'shops' });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText(/^A Ukrainian shop lists this product, but its nutrition values don't add up\. Check them against the label/)).toBeInTheDocument();
    expect(screen.queryByText(/not all of its nutrition/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Open Food Facts/)).not.toBeInTheDocument();
    expect(field('Name').value).toBe('Шпинат');
    expect(field('Calories (kcal / 100 g)').value).toBe('96');
    expect(field('Carbs (g / 100 g)').value).toBe('3.6');
  });

  it("OFF's estimates for every core value: the line says they are estimates to check", async () => {
    const estimates = { 'energy-kcal_100g': 400, proteins_100g: 5, fat_100g: 20, carbohydrates_100g: 50 };
    const prefill = toPrefill({ code: UA, product_name_uk: 'Цукерки', nutriments: {}, nutriments_estimated: estimates }, UA);
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill, from: 'off' });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText(/^Open Food Facts only estimates this product's nutrition from its ingredients\. Check the values/)).toBeInTheDocument();
    expect(field('Calories (kcal / 100 g)').value).toBe('400');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
  });

  it('estimates that leave a value missing still ask for the missing values', async () => {
    const prefill = toPrefill({ code: UA, product_name_uk: 'Цукерки', nutriments: {}, nutriments_estimated: { 'energy-kcal_100g': 400 } }, UA);
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill, from: 'off' });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText(/^Open Food Facts knows this product, but not all of its nutrition/)).toBeInTheDocument();
  });

  it('a product found in the shops says where its values come from', async () => {
    const match = fromZakazCard(nutellaCard, UA);
    if (match?.kind !== 'product') throw new Error('expected a product');
    lookupBarcode.mockResolvedValue({ kind: 'found', product: match.product });
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByRole('dialog', { name: 'Нутелла паста горіхова 350г' })).toBeInTheDocument();
    expect(screen.getByText('Source: Ukrainian shop catalogue (zakaz.ua)')).toBeInTheDocument();
  });

  it('offline everywhere is still the offline message', async () => {
    lookupBarcode.mockRejectedValue(new OffError('server: network; direct: network', null, 'direct'));
    renderPage();
    typeBarcode(UA);
    expect(await screen.findByText('Could not reach Open Food Facts.')).toBeInTheDocument();
    expect(screen.getByText('server: network; direct: network')).toBeInTheDocument();
  });

  it("asks the shops with the session's access token on the deployed site", async () => {
    vi.stubEnv('DEV', false);
    session.current = { access_token: 'jwt-1' };
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) => new Response(JSON.stringify({ answered: 1, failed: 0, results: [] }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: UA, country: 'UKRAINE', ...nobodyFailed });
    renderPage();
    typeBarcode(UA);
    await screen.findByText(/This Ukrainian product isn't in Open Food Facts yet/);
    const sources = lookupBarcode.mock.calls[0][1];
    expect(await sources?.shops?.('04823090100292')).toEqual({ kind: 'miss' });
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('authorization')).toBe('Bearer jwt-1');
  });

  it('does not ask the shops on the dev server, which has no functions', async () => {
    session.current = { access_token: 'jwt-1' };
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: UA, country: 'UKRAINE', ...nobodyFailed });
    renderPage();
    typeBarcode(UA);
    await screen.findByText(/This Ukrainian product isn't in Open Food Facts yet/);
    expect(lookupBarcode.mock.calls[0][1]?.shops).toBeUndefined();
  });
});
