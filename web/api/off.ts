import type { VercelRequest, VercelResponse } from '@vercel/node';

/**
 * Same-origin door to Open Food Facts for the browser. The site could call
 * OFF directly, but a browser cannot set the User-Agent OFF asks apps to
 * send, some networks (VPNs, corporate proxies) get its bot protection, and
 * every visitor would hit OFF's per-IP rate limit alone. Going through here
 * fixes all three and lets Vercel's cache absorb repeated lookups.
 *
 * Only the two endpoints the app uses are forwarded; anything else is 400.
 */
const ORIGIN = 'https://world.openfoodfacts.org';
const USER_AGENT = 'NutriCart/0.1 (web; https://github.com/Serhikko/NutriCart)';
const ALLOWED = ['/cgi/search.pl?', '/api/v2/product/'];

/**
 * How long Vercel's cache may keep an answer. Products change rarely, search
 * results a little more often; "unknown barcode" (404) for ten minutes, so a
 * product added to OFF shows up soon. Anything else (OFF's rate limit, an
 * overload, an error) is never cached: a 429 kept for a day would turn into
 * a day of "not found" for everyone scanning that product.
 */
export function cacheControl(path: string, status: number): string {
  if (status === 200) {
    const maxAge = path.startsWith('/api/v2/product/') ? 86_400 : 600;
    return `public, s-maxage=${maxAge}, stale-while-revalidate=${maxAge}`;
  }
  return status === 404 ? 'public, s-maxage=600' : 'no-store';
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const path = typeof req.query.path === 'string' ? req.query.path : '';
  if (!ALLOWED.some((prefix) => path.startsWith(prefix)) || path.includes('..')) {
    res.status(400).json({ error: 'unsupported path' });
    return;
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12_000);
  try {
    const upstream = await fetch(ORIGIN + path, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal: controller.signal,
    });
    const body = await upstream.text();
    res.setHeader('Cache-Control', cacheControl(path, upstream.status));
    res.setHeader('Content-Type', upstream.headers.get('content-type') ?? 'application/json; charset=utf-8');
    // A busy OFF (429 / 503) says when to come back; the browser cannot read it on a direct request.
    const retryAfter = upstream.headers.get('retry-after');
    if (retryAfter) res.setHeader('Retry-After', retryAfter);
    res.status(upstream.status).send(body);
  } catch (e) {
    res.setHeader('Cache-Control', 'no-store');
    res.status(502).json({ error: 'open food facts unreachable', detail: e instanceof Error ? e.name : 'unknown' });
  } finally {
    clearTimeout(timer);
  }
}
