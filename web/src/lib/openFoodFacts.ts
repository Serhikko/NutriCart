import { OFF_FIELDS, barcodeCandidates, knowsAnything, offCodes, toPrefill, toProduct, type FoodProduct, type ProductPrefill } from '../domain/openFoodFacts';
import { countryOf, type BarcodeCountry } from '../domain/barcodeOrigin';
import { pickShopMatch, shouldAskShops, zakazCode, type ShopAnswer, type ShopsStatus } from '../domain/zakaz';

/**
 * Open Food Facts: the same two endpoints the phone uses, the same field
 * list, the same mapping rules. On the deployed site the requests go through
 * the same-origin function in api/off.ts first, which adds the User-Agent OFF
 * asks for and caches answers; if that route fails (the function is down, or
 * OFF refuses the server's address) the browser tries OFF directly, and the
 * other way round on the dev server, which has no functions. When both fail,
 * the error says what each route did, so the screen can say what happened.
 */
const DIRECT = 'https://world.openfoodfacts.org';
const APP = 'app_name=NutriCart&app_version=web';

export class OffError extends Error {
  constructor(
    message: string,
    /** The HTTP status a route answered with; null when no route could be reached at all. */
    readonly status: number | null,
    readonly via: 'server' | 'direct',
    /** Retry-After in whole seconds, when a busy OFF (429 / 503) said so. */
    readonly retryAfter: number | null = null,
  ) {
    super(message);
    this.name = 'OffError';
  }
}

const isAbort = (e: unknown) => e instanceof DOMException && e.name === 'AbortError';
/** OFF's rate limit (429) or a short overload (503): worth one more try after a pause. */
const isBusy = (status: number | null) => status === 429 || status === 503;

/** Retry-After as whole seconds; the HTTP-date form, or nothing, -> null. */
function retryAfterOf(headers: Headers | undefined): number | null {
  const value = headers?.get('retry-after')?.trim() ?? '';
  return /^\d+$/.test(value) ? Number(value) : null;
}

async function attempt(url: string, via: 'server' | 'direct', signal?: AbortSignal): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(url, { signal });
  } catch (e) {
    if (isAbort(e)) throw e;
    throw new OffError(`${via}: network`, null, via);
  }
  // 404 is a real answer (unknown barcode); anything else that is not ok is a failure of this route.
  // The server route passes OFF's Retry-After on; the direct one cannot (CORS hides the header).
  if (!res.ok && res.status !== 404) throw new OffError(`${via}: HTTP ${res.status}`, res.status, via, retryAfterOf(res.headers));
  if (res.ok && !(res.headers.get('content-type') ?? '').includes('json')) {
    throw new OffError(`${via}: not JSON`, res.status, via); // the SPA fallback page, for instance
  }
  return res;
}

/**
 * Tries the preferred route, then the other one. When both fail, the thrown
 * error names both and carries the most telling status: OFF saying it is
 * busy (429 / 503) on either route, so the lookup can wait and ask again;
 * else the last route that answered at all, since OFF's 500 through the
 * server says more than the direct route's network error and means the
 * device is online. Status null is left for "neither route was reached".
 */
export async function offFetch(path: string, signal?: AbortSignal): Promise<Response> {
  const server = `/api/off?path=${encodeURIComponent(path)}`;
  const direct = `${DIRECT}${path}`;
  const order: [string, 'server' | 'direct'][] = import.meta.env.DEV ? [[direct, 'direct'], [server, 'server']] : [[server, 'server'], [direct, 'direct']];
  const failures: OffError[] = [];
  for (const [url, via] of order) {
    try {
      return await attempt(url, via, signal);
    } catch (e) {
      if (isAbort(e)) throw e;
      failures.push(e as OffError);
    }
  }
  const telling =
    failures.find((f) => isBusy(f.status)) ?? failures.filter((f) => f.status !== null).at(-1) ?? failures[failures.length - 1];
  throw new OffError(failures.map((f) => f.message).join('; '), telling.status, telling.via, telling.retryAfter);
}

export async function searchProducts(query: string, signal?: AbortSignal): Promise<FoodProduct[]> {
  const res = await offFetch(`/cgi/search.pl?search_simple=1&action=process&json=1&page_size=25&search_terms=${encodeURIComponent(query)}&fields=${OFF_FIELDS}&${APP}`, signal);
  const body = (await res.json()) as { products?: Record<string, unknown>[] };
  return (body.products ?? []).map(toProduct).filter((p): p is FoodProduct => p !== null);
}

/**
 * What a scanned code turned out to be: a product to log, a product a source
 * knows only in part (the form starts from what it knows; `from` says which
 * source, for the message), or nothing. "Nothing" carries the scanned digits
 * and their GS1 origin, whether Open Food Facts failed to answer, and what
 * the Ukrainian shops said, so the message never claims a source lacks a
 * product when that source did not answer (domain/zakaz.ts lookupNotice).
 */
export type BarcodeLookup =
  | { kind: 'found'; product: FoodProduct }
  | { kind: 'incomplete'; prefill: ProductPrefill; from: 'off' | 'shops' }
  | { kind: 'not_found'; barcode: string; country: BarcodeCountry | null; offUnavailable: boolean; shops: ShopsStatus };

export interface LookupSources {
  /** The user's own product saved under one of these codes (custom_products). A failure counts as none. */
  saved?: (candidates: string[]) => Promise<FoodProduct | null>;
  /** Open Food Facts; offFetch unless a test says otherwise. */
  fetchOff?: (path: string, signal?: AbortSignal) => Promise<Response>;
  /** The Ukrainian shops (lib/zakaz.ts fetchShops), asked with a GTIN-14. Without it the shops are not asked. */
  shops?: (code14: string, signal?: AbortSignal) => Promise<ShopAnswer>;
  /** Waits before asking a busy OFF again; a real timer unless a test says otherwise. */
  pause?: (seconds: number, signal?: AbortSignal) => Promise<void>;
}

/** What Open Food Facts answered for ONE code: the phone's OffAnswer. */
type OffAnswer =
  | { kind: 'product'; raw: Record<string, unknown> }
  /** 404 / status 0 / no product: not under this form, try the next one. */
  | { kind: 'unknown' }
  /** 429 / 503: OFF's rate limit or a short overload. */
  | { kind: 'busy'; retryAfter: number | null }
  /** No route reached anything: the device may be offline. */
  | { kind: 'offline'; error: OffError }
  /** Any other failure (5xx, a 400, an answer that is not JSON). */
  | { kind: 'failed' };

async function askOff(get: NonNullable<LookupSources['fetchOff']>, code: string, signal?: AbortSignal): Promise<OffAnswer> {
  let res: Response;
  try {
    res = await get(`/api/v2/product/${encodeURIComponent(code)}?fields=${OFF_FIELDS}&${APP}`, signal);
  } catch (e) {
    if (isAbort(e)) throw e;
    if (e instanceof OffError && isBusy(e.status)) return { kind: 'busy', retryAfter: e.retryAfter };
    if (e instanceof OffError && e.status === null) return { kind: 'offline', error: e };
    return { kind: 'failed' };
  }
  if (res.status === 404) return { kind: 'unknown' };
  if (isBusy(res.status)) return { kind: 'busy', retryAfter: retryAfterOf(res.headers) };
  if (res.status < 200 || res.status > 299) return { kind: 'failed' };
  let body: { status?: unknown; product?: unknown } | null;
  try {
    body = (await res.json()) as typeof body;
  } catch (e) {
    if (isAbort(e)) throw e;
    return { kind: 'failed' };
  }
  if (!body || typeof body !== 'object') return { kind: 'failed' };
  if (body.status === 0 || !body.product || typeof body.product !== 'object') return { kind: 'unknown' };
  return { kind: 'product', raw: body.product as Record<string, unknown> };
}

/** A real wait that ends early, with an AbortError, when the scan is abandoned. */
function sleep(seconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const aborted = () => new DOMException('Aborted', 'AbortError');
    if (signal?.aborted) return reject(aborted());
    const onAbort = () => {
      clearTimeout(timer);
      reject(aborted());
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, seconds * 1000);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

/** One retry per scan, and only for a short wait: a longer Retry-After is OFF asking us to stay away. */
const MAX_RETRY_WAIT_S = 5;
const DEFAULT_RETRY_WAIT_S = 2;

/**
 * Barcode lookup, in the phone's order (lookUpBarcode in BarcodeLookup.kt):
 *
 *  1. The user's own product under any form of the code wins.
 *  2. Open Food Facts, once per form that differs by more than leading zeros
 *     (offCodes). A usable product ends the search; an incomplete one is
 *     remembered (the first such, unless it is a bare stub with neither a
 *     name nor a core value) while the other forms are tried; a 404 or
 *     "status 0" means "try the next". A busy OFF (429 / 503) is asked again
 *     once per scan after a short pause; busy again, or any other failure,
 *     ends the OFF part, remembered, and the scan carries on.
 *  3. The Ukrainian shops, for codes they may have (shouldAskShops): a usable
 *     card is found ("zakaz:<code>"), a partial one is remembered.
 *  4. OFF's partial product, else the shops' one: incomplete.
 *  5. OFF unreachable on both routes, with the shops not asked or not
 *     answering either: the OffError is thrown, and the screen says the
 *     device seems offline, never "not found".
 *  6. Otherwise not found, saying which sources did not answer.
 *
 * Only an AbortError from the caller's signal escapes otherwise.
 */
export async function lookupBarcode(scanned: string, sources: LookupSources = {}, signal?: AbortSignal): Promise<BarcodeLookup> {
  const candidates = barcodeCandidates(scanned);
  const barcode = candidates[0] ?? '';
  const country = countryOf(barcode);
  if (candidates.length === 0) return { kind: 'not_found', barcode, country, offUnavailable: false, shops: 'NOT_ASKED' };

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
  const pause = sources.pause ?? sleep;
  let offPrefill: ProductPrefill | null = null;
  let offTrouble: Exclude<OffAnswer, { kind: 'product' } | { kind: 'unknown' }> | null = null;
  let retried = false;
  for (const code of offCodes(candidates)) {
    let answer = await askOff(get, code, signal);
    if (answer.kind === 'busy' && !retried && (answer.retryAfter ?? 0) <= MAX_RETRY_WAIT_S) {
      retried = true;
      await pause(Math.max(1, answer.retryAfter ?? DEFAULT_RETRY_WAIT_S), signal);
      answer = await askOff(get, code, signal);
    }
    if (answer.kind === 'product') {
      const product = toProduct(answer.raw);
      if (product) return { kind: 'found', product };
      // A bare stub (no name, no core value) is no prefill: a later form, or the shops, may know more.
      const prefill = toPrefill(answer.raw, code);
      if (knowsAnything(prefill)) offPrefill ??= prefill;
    } else if (answer.kind !== 'unknown') {
      offTrouble = answer; // stop asking OFF: it is busy, failing or out of reach
      break;
    }
  }

  let shops: ShopsStatus = 'NOT_ASKED';
  let shopPrefill: ProductPrefill | null = null;
  const code14 = zakazCode(barcode);
  if (sources.shops && code14 !== null && shouldAskShops(barcode)) {
    let answer: ShopAnswer;
    try {
      answer = await sources.shops(code14, signal);
    } catch (e) {
      if (isAbort(e)) throw e;
      answer = { kind: 'unavailable' };
    }
    if (answer.kind === 'hits') {
      const match = pickShopMatch(answer.cards, barcode);
      if (match?.kind === 'product') return { kind: 'found', product: match.product };
      if (match?.kind === 'prefill') shopPrefill = match.prefill;
      shops = 'MISS';
    } else {
      shops = answer.kind === 'miss' ? 'MISS' : 'UNAVAILABLE';
    }
  }

  if (offPrefill) return { kind: 'incomplete', prefill: offPrefill, from: 'off' };
  if (shopPrefill) return { kind: 'incomplete', prefill: shopPrefill, from: 'shops' };
  if (offTrouble?.kind === 'offline' && (shops === 'NOT_ASKED' || shops === 'UNAVAILABLE')) throw offTrouble.error;
  return { kind: 'not_found', barcode, country, offUnavailable: offTrouble !== null, shops };
}
