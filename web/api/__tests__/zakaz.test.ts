// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';
import { shopAnswerFromJson } from '../../src/lib/zakaz';

// Vercel turns every file under api/ into a function, except paths starting
// with "_": this folder is tests only, never deployed.

const ORIGIN = 'https://stores-api.zakaz.ua';
const SUPABASE = 'https://project.supabase.example';

/** A fresh module per test: the store list lives in a module-level variable on a warm instance. */
async function load() {
  vi.resetModules();
  return import('../zakaz');
}

interface Reply {
  statusCode: number;
  headers: Record<string, string>;
  body: unknown;
}

async function call(handler: (req: VercelRequest, res: VercelResponse) => Promise<void>, query: Record<string, string>, authorization?: string): Promise<Reply> {
  const reply: Reply = { statusCode: 200, headers: {}, body: undefined };
  const res = {
    headersSent: false,
    setHeader(name: string, value: string) {
      reply.headers[name.toLowerCase()] = value;
      return res;
    },
    status(code: number) {
      reply.statusCode = code;
      return res;
    },
    json(body: unknown) {
      reply.body = body;
      res.headersSent = true;
      return res;
    },
  };
  const req = { query, headers: authorization ? { authorization } : {} };
  await handler(req as unknown as VercelRequest, res as unknown as VercelResponse);
  return reply;
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

const STORES = [
  { id: '1', retail_chain: 'novus', city: 'lviv', is_active: true },
  { id: '2', retail_chain: 'Novus', city: 'kiev', is_active: true },
  { id: '3', retail_chain: 'auchan', city: 'kiev', is_active: false },
  { id: '4', retail_chain: 'auchan', city: 'dnipro', is_active: true },
  { id: '5', retail_chain: 'metro', city: null, is_active: true },
  { id: '6', retail_chain: 'silpo', city: 'kiev', is_active: true },
];

const nutella = {
  id: '04823090100292',
  title: 'Нутелла',
  ean: '04823090100292',
  price: 18999,
  producer: { trademark: 'Nutella', trademark_slug: 'nutella', country: 'IT' },
  weight: 350,
  volume: null,
  unit: 'pcs',
  nutrition_facts: { ingredient_energy: '539.00ккал', ingredient_protein: '6,3г', ingredient_fat: '30,9г', ingredient_carbohydrates: '57,5г', ingredients: 'цукор, олія' },
  img: { s350x350: 'https://img.example/x.jpg' },
};

/** A fake zakaz.ua and Supabase. `products` maps a store id to its answer for any code; a missing store is a 404. */
function upstream(options: { stores?: unknown | 'down'; products?: Record<string, Response | 'down' | (() => Response)>; user?: number } = {}) {
  const fetchMock = vi.fn(async (input: string | URL | Request, _init?: RequestInit) => {
    const url = String(input);
    if (url === `${SUPABASE}/auth/v1/user`) return json({ id: 'u1' }, options.user ?? 200);
    if (url === `${ORIGIN}/stores/`) {
      if (options.stores === 'down') throw new TypeError('fetch failed');
      return json(options.stores ?? STORES);
    }
    const m = /^https:\/\/stores-api\.zakaz\.ua\/stores\/([^/]+)\/products\/(\d{14})\/$/.exec(url);
    if (m) {
      const answer = options.products?.[decodeURIComponent(m[1])];
      if (answer === 'down') throw new TypeError('fetch failed');
      if (typeof answer === 'function') return answer();
      return answer ?? json({ detail: 'Not found.' }, 404);
    }
    throw new Error(`unexpected request ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const productCalls = (fetchMock: ReturnType<typeof upstream>) => fetchMock.mock.calls.map((c) => String(c[0])).filter((u) => u.includes('/products/'));

beforeEach(() => {
  vi.stubEnv('SUPABASE_URL', undefined);
  vi.stubEnv('VITE_SUPABASE_URL', undefined);
  vi.stubEnv('SUPABASE_ANON_KEY', undefined);
  vi.stubEnv('VITE_SUPABASE_ANON_KEY', undefined);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('store choice', () => {
  it('the shared vector: active stores of known chains, a Kyiv one per chain when there is one, in chain order', async () => {
    const { pickZakazStores } = await load();
    expect(pickZakazStores(STORES)).toEqual(['4', '2', '5']);
  });

  it('anything unusable is an empty list', async () => {
    const { pickZakazStores } = await load();
    expect(pickZakazStores(null)).toEqual([]);
    expect(pickZakazStores({ results: STORES })).toEqual([]);
    expect(pickZakazStores([{ id: 7, retail_chain: 'auchan', is_active: true }, { id: '8', retail_chain: 'silpo', is_active: true }, 'x'])).toEqual([]);
  });

  it('at most six stores', async () => {
    const { pickZakazStores } = await load();
    const chains = ['auchan', 'novus', 'metro', 'megamarket', 'ekomarket', 'tavriav', 'ultramarket', 'epicentr'];
    const many = chains.map((chain, i) => ({ id: `s${i}`, retail_chain: chain, city: 'kyiv', is_active: true })).reverse();
    expect(pickZakazStores(many)).toEqual(['s0', 's1', 's2', 's3', 's4', 's5']);
  });

  it('the same code form as the site', async () => {
    const { zakazCode } = await load();
    expect(zakazCode('4823090100292')).toBe('04823090100292');
    expect(zakazCode('40111445')).toBe('00000040111445');
    expect(zakazCode('012345678905')).toBe('00012345678905');
    expect(zakazCode('123')).toBeNull();
  });
});

describe('cards are cut down to what the site reads', () => {
  it('unwraps { product } and keeps strings and numbers only', async () => {
    const { trimCard } = await load();
    const expected = {
      title: 'Нутелла',
      producer: { trademark: 'Nutella' },
      weight: 350,
      volume: undefined,
      unit: 'pcs',
      nutrition_facts: { ingredient_energy: '539.00ккал', ingredient_protein: '6,3г', ingredient_fat: '30,9г', ingredient_carbohydrates: '57,5г' },
    };
    expect(trimCard(nutella)).toEqual(expected);
    expect(trimCard({ product: nutella })).toEqual(expected);
    expect(trimCard({ title: { evil: true }, producer: 'x', weight: '350', nutrition_facts: { ingredient_fat: 2 } })).toEqual({
      title: undefined,
      producer: { trademark: undefined },
      weight: undefined,
      volume: undefined,
      unit: undefined,
      nutrition_facts: { ingredient_energy: undefined, ingredient_protein: undefined, ingredient_fat: 2, ingredient_carbohydrates: undefined },
    });
    expect(trimCard([nutella])).toBeNull();
    expect(trimCard('card')).toBeNull();
  });
});

describe('GET /api/zakaz', () => {
  it.each(['', '123', '123456789', '12345678901', '123456789012345', 'abc4823090100', '4823090100292 ', '-4823090100292'])('rejects the code %j without asking anyone', async (code) => {
    const fetchMock = upstream();
    const { default: handler } = await load();
    const reply = await call(handler, { code });
    expect(reply.statusCode).toBe(400);
    expect(reply.headers['cache-control']).toBe('no-store');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('asks the chosen stores for the GTIN-14 and answers with trimmed cards, in preference order', async () => {
    const fetchMock = upstream({ products: { '2': json(nutella), '4': json({ product: { ...nutella, title: 'Нутелла Ашан' } }) } });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.statusCode).toBe(200);
    expect(reply.headers['cache-control']).toBe('private, max-age=3600');
    expect(reply.body).toMatchObject({
      code: '04823090100292',
      answered: 3,
      failed: 0,
      results: [
        { store: '4', chain: 'auchan', product: { title: 'Нутелла Ашан' } },
        { store: '2', chain: 'novus', product: { title: 'Нутелла', producer: { trademark: 'Nutella' } } },
      ],
    });
    const product = (reply.body as { results: { product: Record<string, unknown> }[] }).results[1].product;
    expect(Object.keys(product).sort()).toEqual(['nutrition_facts', 'producer', 'title', 'unit', 'volume', 'weight']);
    expect(productCalls(fetchMock).sort()).toEqual([
      `${ORIGIN}/stores/2/products/04823090100292/`,
      `${ORIGIN}/stores/4/products/04823090100292/`,
      `${ORIGIN}/stores/5/products/04823090100292/`,
    ]);
  });

  it('sends our own User-Agent, asks in Ukrainian, and never poses as a shop page', async () => {
    const fetchMock = upstream();
    const { default: handler } = await load();
    await call(handler, { code: '4823090100292' });
    for (const [, init] of fetchMock.mock.calls) {
      const headers = new Headers(init?.headers);
      expect(headers.get('user-agent')).toBe('NutriCart/0.1 (web; https://github.com/Serhikko/NutriCart)');
      expect(headers.get('accept')).toBe('application/json');
      expect(headers.get('accept-language')).toBe('uk');
      expect(headers.get('origin')).toBeNull();
      expect(headers.get('referer')).toBeNull();
      expect(headers.get('x-chain')).toBeNull();
    }
  });

  it('stores that answer 404 are a miss; failures are counted, never echoed', async () => {
    upstream({ products: { '4': json({ detail: 'secret upstream detail' }, 500), '5': 'down' } });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.statusCode).toBe(200);
    expect(reply.body).toEqual({ code: '04823090100292', answered: 1, failed: 2, results: [] });
    expect(JSON.stringify(reply.body)).not.toContain('secret');
    // A store that failed may list the code once it is back: the browser must ask again.
    expect(reply.headers['cache-control']).toBe('no-store');
  });

  it('a full answer may be kept by the browser, also a miss', async () => {
    upstream();
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.body).toEqual({ code: '04823090100292', answered: 3, failed: 0, results: [] });
    expect(reply.headers['cache-control']).toBe('private, max-age=3600');
  });

  it('a hit with another store failing is not kept either', async () => {
    upstream({ products: { '2': json(nutella), '4': 'down' } });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.body).toMatchObject({ answered: 2, failed: 1, results: [{ store: '2' }] });
    expect(reply.headers['cache-control']).toBe('no-store');
  });

  it('no store answering is not cached', async () => {
    upstream({ products: { '2': 'down', '4': 'down', '5': json('<html>', 502) } });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.body).toEqual({ code: '04823090100292', answered: 0, failed: 3, results: [] });
    expect(reply.headers['cache-control']).toBe('no-store');
  });

  it('a 200 that is not JSON is a failure of that store', async () => {
    upstream({ products: { '4': () => new Response('<html>', { status: 200 }) } });
    const { default: handler } = await load();
    expect((await call(handler, { code: '4823090100292' })).body).toMatchObject({ answered: 2, failed: 1, results: [] });
  });

  // Same as the phone: a 200 is an answer, and one with no card in it lists nothing.
  it.each([[[]], ['card'], [null]])('a 200 holding %j is an answer without a hit, not a failure', async (body) => {
    upstream({ stores: 'down', products: { '48246401': json(body), '48201031': 'down', '48215611': 'down', '48280214': 'down', '48277601': 'down' } });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.body).toEqual({ code: '04823090100292', answered: 1, failed: 4, results: [] });
    expect(shopAnswerFromJson(reply.body)).toEqual({ kind: 'miss' });
  });

  it('the store list is fetched once and kept by the warm instance', async () => {
    const fetchMock = upstream();
    const { default: handler } = await load();
    await call(handler, { code: '4823090100292' });
    await call(handler, { code: '4006381333931' });
    expect(fetchMock.mock.calls.filter((c) => String(c[0]) === `${ORIGIN}/stores/`)).toHaveLength(1);
  });

  it('without a store list the built-in stores are asked, and the list is not asked for again for ten minutes', async () => {
    const fetchMock = upstream({ stores: 'down' });
    const { default: handler } = await load();
    const reply = await call(handler, { code: '4823090100292' });
    expect(reply.body).toMatchObject({ answered: 5, failed: 0 });
    expect(productCalls(fetchMock).map((u) => u.split('/')[4]).sort()).toEqual(['48201031', '48215611', '48246401', '48277601', '48280214']);
    await call(handler, { code: '4823090100292' });
    expect(fetchMock.mock.calls.filter((c) => String(c[0]) === `${ORIGIN}/stores/`)).toHaveLength(1);
  });

  it('an unusable store list falls back too', async () => {
    const fetchMock = upstream({ stores: { error: 'maintenance' } });
    const { default: handler } = await load();
    await call(handler, { code: '4823090100292' });
    expect(productCalls(fetchMock)).toHaveLength(5);
  });

  it('a day later, a store list that cannot be had means the built-in stores, not the old list (as on the phone)', async () => {
    const storesAsked = (fetchMock: ReturnType<typeof upstream>) => productCalls(fetchMock).map((u) => u.split('/')[4]);
    const day1 = upstream({ stores: [{ id: '4', retail_chain: 'auchan', city: 'kyiv', is_active: true }, { id: '2', retail_chain: 'novus', city: 'kyiv', is_active: true }] });
    const { default: handler } = await load();
    await call(handler, { code: '4823090100292' });
    expect(storesAsked(day1)).toEqual(['4', '2']);

    const later = Date.now() + 25 * 60 * 60 * 1000;
    vi.spyOn(Date, 'now').mockReturnValue(later);
    const day2 = upstream({ stores: 'down' });
    await call(handler, { code: '4823090100292' });
    expect(day2.mock.calls.filter((c) => String(c[0]) === `${ORIGIN}/stores/`)).toHaveLength(1);
    expect(storesAsked(day2)).toEqual(['48246401', '48201031', '48215611', '48280214', '48277601']);

    // Still the built-in stores for the next ten minutes, without asking for the list again.
    vi.spyOn(Date, 'now').mockReturnValue(later + 60 * 1000);
    const day2b = upstream({ stores: 'down' });
    await call(handler, { code: '4823090100292' });
    expect(day2b.mock.calls.filter((c) => String(c[0]) === `${ORIGIN}/stores/`)).toHaveLength(0);
    expect(storesAsked(day2b)).toEqual(['48246401', '48201031', '48215611', '48280214', '48277601']);
  });

  describe('with Supabase configured', () => {
    beforeEach(() => {
      vi.stubEnv('VITE_SUPABASE_URL', SUPABASE);
      vi.stubEnv('VITE_SUPABASE_ANON_KEY', 'anon-key');
    });

    it('needs a bearer token', async () => {
      const fetchMock = upstream();
      const { default: handler } = await load();
      const reply = await call(handler, { code: '4823090100292' });
      expect(reply.statusCode).toBe(401);
      expect(reply.headers['cache-control']).toBe('no-store');
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('checks the token with Supabase, and refuses one it rejects', async () => {
      const fetchMock = upstream({ user: 401 });
      const { default: handler } = await load();
      expect((await call(handler, { code: '4823090100292' }, 'Bearer stale')).statusCode).toBe(401);
      const [, init] = fetchMock.mock.calls[0];
      expect(new Headers(init?.headers).get('apikey')).toBe('anon-key');
      expect(new Headers(init?.headers).get('authorization')).toBe('Bearer stale');
      expect(productCalls(fetchMock)).toHaveLength(0);
    });

    it('a valid token gets the lookup, and the token never reaches zakaz.ua', async () => {
      const fetchMock = upstream({ products: { '2': json(nutella) } });
      const { default: handler } = await load();
      const reply = await call(handler, { code: '4823090100292' }, 'Bearer good');
      expect(reply.statusCode).toBe(200);
      expect(reply.body).toMatchObject({ results: [{ store: '2' }] });
      for (const [url, init] of fetchMock.mock.calls) {
        if (String(url).startsWith(ORIGIN)) expect(new Headers(init?.headers).get('authorization')).toBeNull();
      }
    });

    it('the server-side names win over the VITE_ ones', async () => {
      vi.stubEnv('SUPABASE_URL', 'https://other.supabase.example');
      const fetchMock = upstream();
      const { default: handler } = await load();
      const reply = await call(handler, { code: '4823090100292' }, 'Bearer good');
      expect(String(fetchMock.mock.calls[0][0])).toBe('https://other.supabase.example/auth/v1/user');
      expect(reply.statusCode).toBe(401); // the fake Supabase does not know that project
    });
  });
});
