import { OFF_FIELDS, barcodeCandidates, toPrefill, toProduct, type FoodProduct, type ProductPrefill } from '../domain/openFoodFacts';
import { countryOf, type BarcodeCountry } from '../domain/barcodeOrigin';

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

/**
 * What a scanned code turned out to be: a product to log, a product Open Food
 * Facts knows only in part (the form starts from what it knows), or nothing,
 * with the scanned digits and their GS1 origin for the "add it" message.
 */
export type BarcodeLookup =
  | { kind: 'found'; product: FoodProduct }
  | { kind: 'incomplete'; prefill: ProductPrefill }
  | { kind: 'not_found'; barcode: string; country: BarcodeCountry | null };

export interface LookupSources {
  /** The user's own product saved under one of these codes (custom_products). A failure counts as none. */
  saved?: (candidates: string[]) => Promise<FoodProduct | null>;
  /** Open Food Facts; offFetch unless a test says otherwise. */
  fetchOff?: (path: string, signal?: AbortSignal) => Promise<Response>;
}

/**
 * Barcode lookup, in the phone's order: the user's own product under any form
 * of the code wins; then Open Food Facts, every form in turn, where a usable
 * product ends the search and an incomplete one is remembered (the first such)
 * while the other forms are tried, since another form may be complete; a 404
 * or "status 0" means "try the next". Failures of OFF itself throw OffError.
 */
export async function lookupBarcode(scanned: string, sources: LookupSources = {}, signal?: AbortSignal): Promise<BarcodeLookup> {
  const candidates = barcodeCandidates(scanned);
  const barcode = candidates[0] ?? '';
  const notFound: BarcodeLookup = { kind: 'not_found', barcode, country: countryOf(barcode) };
  if (candidates.length === 0) return notFound;

  if (sources.saved) {
    let saved: FoodProduct | null = null;
    try {
      saved = await sources.saved(candidates);
    } catch {
      saved = null; // never let the user's own table block a scan
    }
    if (saved) return { kind: 'found', product: saved };
  }

  const get = sources.fetchOff ?? offFetch;
  let prefill: ProductPrefill | null = null;
  for (const code of candidates) {
    const res = await get(`/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}&${APP}`, signal);
    if (res.status === 404) continue;
    const body = (await res.json()) as { status?: number; product?: unknown };
    if (body.status === 0 || !body.product || typeof body.product !== 'object') continue;
    const raw = body.product as Record<string, unknown>;
    const product = toProduct(raw);
    if (product) return { kind: 'found', product };
    prefill ??= toPrefill(raw, code);
  }
  return prefill ? { kind: 'incomplete', prefill } : notFound;
}
