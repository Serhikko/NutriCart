// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';
import handler, { cacheControl } from '../off';

interface Reply {
  statusCode: number;
  headers: Record<string, string>;
}

async function call(path: string): Promise<Reply> {
  const reply: Reply = { statusCode: 200, headers: {} };
  const res = {
    setHeader(name: string, value: string) {
      reply.headers[name.toLowerCase()] = value;
      return res;
    },
    status(code: number) {
      reply.statusCode = code;
      return res;
    },
    json: () => res,
    send: () => res,
  };
  await handler({ query: { path }, headers: {} } as unknown as VercelRequest, res as unknown as VercelResponse);
  return reply;
}

const PRODUCT = '/api/v2/product/4823090100292?fields=code';
const SEARCH = '/cgi/search.pl?search_terms=kefir';

describe('what Vercel may cache', () => {
  it.each([
    [PRODUCT, 200, 'public, s-maxage=86400, stale-while-revalidate=86400'],
    [SEARCH, 200, 'public, s-maxage=600, stale-while-revalidate=600'],
    [PRODUCT, 404, 'public, s-maxage=600'],
    [PRODUCT, 429, 'no-store'],
    [PRODUCT, 503, 'no-store'],
    [PRODUCT, 500, 'no-store'],
    [SEARCH, 400, 'no-store'],
  ])('%s answered %i -> %s', (path, status, expected) => expect(cacheControl(path, status)).toBe(expected));
});

describe('GET /api/off', () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes OFF's status and Retry-After on, and does not cache a busy answer", async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"status":"busy"}', { status: 429, headers: { 'retry-after': '3', 'content-type': 'application/json' } })));
    const reply = await call(PRODUCT);
    expect(reply.statusCode).toBe(429);
    expect(reply.headers['retry-after']).toBe('3');
    expect(reply.headers['cache-control']).toBe('no-store');
  });

  it('a product is cached for a day', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"status":1}', { status: 200, headers: { 'content-type': 'application/json' } })));
    const reply = await call(PRODUCT);
    expect(reply.headers['cache-control']).toBe('public, s-maxage=86400, stale-while-revalidate=86400');
    expect(reply.headers['retry-after']).toBeUndefined();
  });

  it('OFF out of reach is a 502 nobody caches', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('fetch failed');
      }),
    );
    const reply = await call(PRODUCT);
    expect(reply.statusCode).toBe(502);
    expect(reply.headers['cache-control']).toBe('no-store');
  });
});
