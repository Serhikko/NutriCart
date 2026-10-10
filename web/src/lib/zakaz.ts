import type { ShopAnswer } from '../domain/zakaz';

/**
 * The Ukrainian shops (zakaz.ua), asked through the site's own function in
 * api/zakaz.ts: it holds the store list, asks a handful of chains in
 * parallel for the one scanned code and answers with their cards. The
 * browser never calls zakaz.ua itself.
 *
 * The function wants the visitor's Supabase access token, so that it relays
 * lookups for the site's users only. The dev server has no functions, so
 * there the shops are simply not asked (shopsSource returns undefined).
 */

/** Longer than the function's own budget (about 8 s), so its answer normally arrives first. */
const CLIENT_TIMEOUT_MS = 12_000;

/**
 * The function's answer as a ShopAnswer: the cards of the stores that list
 * the code, in preference order; a miss when stores answered but none lists
 * it; unavailable when no store answered, or the answer makes no sense.
 */
export function shopAnswerFromJson(body: unknown): ShopAnswer {
  if (!body || typeof body !== 'object') return { kind: 'unavailable' };
  const { answered, results } = body as { answered?: unknown; results?: unknown };
  const cards = Array.isArray(results)
    ? results.filter((r): r is { product: unknown } => !!r && typeof r === 'object' && 'product' in r).map((r) => r.product)
    : [];
  if (cards.length > 0) return { kind: 'hits', cards };
  return typeof answered === 'number' && answered > 0 ? { kind: 'miss' } : { kind: 'unavailable' };
}

/**
 * Asks the shops about one GTIN-14. Never throws, except an AbortError when
 * the caller's signal fires: a network error, a timeout or any status but
 * 200 is "unavailable", which the screen reports as such.
 */
export async function fetchShops(code14: string, accessToken: string, signal?: AbortSignal): Promise<ShopAnswer> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  const forward = () => controller.abort();
  signal?.addEventListener('abort', forward, { once: true });
  try {
    const res = await fetch(`/api/zakaz?code=${encodeURIComponent(code14)}`, {
      headers: { Accept: 'application/json', Authorization: `Bearer ${accessToken}` },
      signal: controller.signal,
    });
    if (res.status !== 200) return { kind: 'unavailable' };
    return shopAnswerFromJson(await res.json());
  } catch {
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    return { kind: 'unavailable' }; // our own timeout, a network error or a body that is not JSON
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', forward);
  }
}

/** The shops source for lookupBarcode: none on the dev server, or without a session to vouch for the request. */
export function shopsSource(accessToken: string | null): ((code14: string, signal?: AbortSignal) => Promise<ShopAnswer>) | undefined {
  if (import.meta.env.DEV || !accessToken) return undefined;
  return (code14, signal) => fetchShops(code14, accessToken, signal);
}
