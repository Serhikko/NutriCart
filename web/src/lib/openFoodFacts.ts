import { OFF_FIELDS, barcodeCandidates, toProduct, type FoodProduct } from '../domain/openFoodFacts';

/**
 * Open Food Facts: the same two endpoints the phone uses, the same field
 * list, the same mapping rules. On the deployed site the requests go through
 * the same-origin function in api/off.ts first, which adds the User-Agent OFF
 * asks for and caches answers; if that route fails (the function is down, or
 * OFF refuses the server's address) the browser tries OFF directly, and the
 * other way round on the dev server, which has no functions. Whatever fails
 * last is reported with its status so the screen can say what happened.
 */
const DIRECT = 'https://world.openfoodfacts.org';
const APP = 'app_name=NutriCart&app_version=web';

export class OffError extends Error {
  constructor(message: string, readonly status: number | null, readonly via: 'server' | 'direct') {
    super(message);
    this.name = 'OffError';
  }
}

async function attempt(url: string, via: 'server' | 'direct', signal?: AbortSignal): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(url, { signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new OffError(`${via}: network`, null, via);
  }
  // 404 is a real answer (unknown barcode); anything else that is not ok is a failure of this route.
  if (!res.ok && res.status !== 404) throw new OffError(`${via}: HTTP ${res.status}`, res.status, via);
  if (res.ok && !(res.headers.get('content-type') ?? '').includes('json')) {
    throw new OffError(`${via}: not JSON`, res.status, via); // the SPA fallback page, for instance
  }
  return res;
}

/** Tries the preferred route, then the other one; throws the last failure. */
export async function offFetch(path: string, signal?: AbortSignal): Promise<Response> {
  const server = `/api/off?path=${encodeURIComponent(path)}`;
  const direct = `${DIRECT}${path}`;
  const order: [string, 'server' | 'direct'][] = import.meta.env.DEV ? [[direct, 'direct'], [server, 'server']] : [[server, 'server'], [direct, 'direct']];
  let last: unknown;
  for (const [url, via] of order) {
    try {
      return await attempt(url, via, signal);
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') throw e;
      last = e;
    }
  }
  throw last;
}

export async function searchProducts(query: string, signal?: AbortSignal): Promise<FoodProduct[]> {
  const res = await offFetch(`/cgi/search.pl?search_simple=1&action=process&json=1&page_size=25&search_terms=${encodeURIComponent(query)}&fields=${OFF_FIELDS}&${APP}`, signal);
  const body = (await res.json()) as { products?: Record<string, unknown>[] };
  return (body.products ?? []).map(toProduct).filter((p): p is FoodProduct => p !== null);
}

/** Tries every form the code may be stored under; a 404 means "try the next". */
export async function productByBarcode(scanned: string, signal?: AbortSignal): Promise<FoodProduct | null> {
  for (const code of barcodeCandidates(scanned)) {
    const res = await offFetch(`/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}&${APP}`, signal);
    if (res.status === 404) continue;
    const body = (await res.json()) as { status?: number; product?: Record<string, unknown> };
    if (!body.product) continue;
    const product = toProduct(body.product);
    if (product) return product;
  }
  return null;
}
