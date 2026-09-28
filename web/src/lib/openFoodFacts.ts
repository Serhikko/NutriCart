import { OFF_FIELDS, barcodeCandidates, toProduct, type FoodProduct } from '../domain/openFoodFacts';

/**
 * Open Food Facts: the same two endpoints the phone uses, the same field
 * list, the same mapping rules. On the deployed site the requests go through
 * the same-origin function in api/off.ts, which adds the User-Agent OFF asks
 * for and caches answers; the dev server has no functions, so there the
 * browser calls OFF directly.
 */
const DIRECT = 'https://world.openfoodfacts.org';
const APP = 'app_name=NutriCart&app_version=web';

export function offUrl(path: string): string {
  return import.meta.env.DEV ? `${DIRECT}${path}` : `/api/off?path=${encodeURIComponent(path)}`;
}

export async function searchProducts(query: string, signal?: AbortSignal): Promise<FoodProduct[]> {
  const url = offUrl(`/cgi/search.pl?search_simple=1&action=process&json=1&page_size=25&search_terms=${encodeURIComponent(query)}&fields=${OFF_FIELDS}&${APP}`);
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`OFF search ${res.status}`);
  const body = (await res.json()) as { products?: Record<string, unknown>[] };
  return (body.products ?? []).map(toProduct).filter((p): p is FoodProduct => p !== null);
}

/** Tries every form the code may be stored under; a 404 means "try the next". */
export async function productByBarcode(scanned: string, signal?: AbortSignal): Promise<FoodProduct | null> {
  for (const code of barcodeCandidates(scanned)) {
    const res = await fetch(offUrl(`/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}&${APP}`), { signal });
    if (res.status === 404) continue;
    if (!res.ok) throw new Error(`OFF product ${res.status}`);
    const body = (await res.json()) as { status?: number; product?: Record<string, unknown> };
    if (!body.product) continue;
    const product = toProduct(body.product);
    if (product) return product;
  }
  return null;
}
