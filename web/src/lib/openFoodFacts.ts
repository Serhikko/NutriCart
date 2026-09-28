import { OFF_FIELDS, barcodeCandidates, toProduct, type FoodProduct } from '../domain/openFoodFacts';

/**
 * Open Food Facts from the browser: the same two endpoints the phone uses,
 * the same field list, the same mapping rules. The API allows cross-origin
 * requests; a browser cannot set User-Agent, so the app identifies itself
 * through the documented `app_name` parameter instead.
 */
const BASE = 'https://world.openfoodfacts.org';
const APP = 'app_name=NutriCart&app_version=web';

export async function searchProducts(query: string, signal?: AbortSignal): Promise<FoodProduct[]> {
  const url = `${BASE}/cgi/search.pl?search_simple=1&action=process&json=1&page_size=25&search_terms=${encodeURIComponent(query)}&fields=${OFF_FIELDS}&${APP}`;
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`OFF search ${res.status}`);
  const body = (await res.json()) as { products?: Record<string, unknown>[] };
  return (body.products ?? []).map(toProduct).filter((p): p is FoodProduct => p !== null);
}

/** Tries every form the code may be stored under; a 404 means "try the next". */
export async function productByBarcode(scanned: string, signal?: AbortSignal): Promise<FoodProduct | null> {
  for (const code of barcodeCandidates(scanned)) {
    const res = await fetch(`${BASE}/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}&${APP}`, { signal });
    if (res.status === 404) continue;
    if (!res.ok) throw new Error(`OFF product ${res.status}`);
    const body = (await res.json()) as { status?: number; product?: Record<string, unknown> };
    if (!body.product) continue;
    const product = toProduct(body.product);
    if (product) return product;
  }
  return null;
}
