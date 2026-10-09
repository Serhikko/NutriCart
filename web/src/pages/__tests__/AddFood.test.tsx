import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import type { BarcodeLookup } from '../../lib/openFoodFacts';
import type { FoodProduct } from '../../domain/openFoodFacts';
import { toPrefill } from '../../domain/openFoodFacts';

const lookupBarcode = vi.fn<(code: string) => Promise<BarcodeLookup>>();
const searchProducts = vi.fn<(q: string) => Promise<FoodProduct[]>>();
const saveCustomProduct = vi.fn<(userId: string | null, p: FoodProduct) => Promise<void>>();
const searchCustomProducts = vi.fn<(userId: string | null, q: string) => Promise<FoodProduct[]>>();

vi.mock('../../lib/openFoodFacts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/openFoodFacts')>()),
  lookupBarcode: (code: string) => lookupBarcode(code),
  searchProducts: (q: string) => searchProducts(q),
}));
vi.mock('../../lib/customProducts', () => ({
  savedProductFor: async () => null,
  saveCustomProduct: (userId: string | null, p: FoodProduct) => saveCustomProduct(userId, p),
  searchCustomProducts: (userId: string | null, q: string) => searchCustomProducts(userId, q),
}));
vi.mock('../../lib/myTargets', () => ({ useMyTargets: () => ({ userId: 'u1', summary: { targetKcal: 2000, primaryClient: 'web' } }) }));
vi.mock('../../lib/tracker', () => ({ useLogFood: () => ({ mutate: vi.fn() }) }));

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
});

describe('AddFood barcode outcomes', () => {
  it('not found: the origin message, then an empty form with the barcode, and logging even when saving fails', async () => {
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE' });
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
    lookupBarcode.mockResolvedValue({ kind: 'incomplete', prefill });
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
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '4820024700016', country: 'UKRAINE' });
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
    lookupBarcode.mockResolvedValue({ kind: 'not_found', barcode: '5000112637922', country: null });
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
