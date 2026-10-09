import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchShops, shopAnswerFromJson, shopsSource } from '../zakaz';

const card = { title: 'Нутелла', nutrition_facts: { ingredient_energy: '539ккал' } };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

describe("the shops function's answer", () => {
  it('cards of the stores that list the code, in order', () => {
    const body = { code: '04823090100292', answered: 3, failed: 1, results: [{ store: '4', chain: 'auchan', product: card }, { store: '2', chain: 'novus', product: { title: 'B' } }] };
    expect(shopAnswerFromJson(body)).toEqual({ kind: 'hits', cards: [card, { title: 'B' }] });
  });
  it('stores answered, none lists it: a miss', () => {
    expect(shopAnswerFromJson({ answered: 2, failed: 3, results: [] })).toEqual({ kind: 'miss' });
  });
  it('no store answered, or an answer that makes no sense: unavailable', () => {
    expect(shopAnswerFromJson({ answered: 0, failed: 5, results: [] })).toEqual({ kind: 'unavailable' });
    expect(shopAnswerFromJson(null)).toEqual({ kind: 'unavailable' });
    expect(shopAnswerFromJson('ok')).toEqual({ kind: 'unavailable' });
    expect(shopAnswerFromJson({ answered: '2', results: 'none' })).toEqual({ kind: 'unavailable' });
  });
});

describe('asking the shops function', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('sends the code and the access token, and maps the answer', async () => {
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) => json({ answered: 1, failed: 0, results: [{ store: '4', chain: 'auchan', product: card }] }));
    vi.stubGlobal('fetch', fetchMock);
    expect(await fetchShops('04823090100292', 'token-1')).toEqual({ kind: 'hits', cards: [card] });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/zakaz?code=04823090100292');
    expect(new Headers(init?.headers).get('authorization')).toBe('Bearer token-1');
  });

  it.each([
    ['a 401', async () => json({ error: 'sign-in required' }, 401)],
    ['a 502', async () => json({ error: 'shops unreachable' }, 502)],
    ['a network error', async () => Promise.reject(new TypeError('Failed to fetch'))],
    ['a body that is not JSON', async () => new Response('<html>', { status: 200 })],
  ])('%s is "unavailable"', async (_label, answer) => {
    vi.stubGlobal('fetch', vi.fn(answer));
    expect(await fetchShops('04823090100292', 't')).toEqual({ kind: 'unavailable' });
  });

  it("the caller's abort still aborts", async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        controller.abort();
        throw new DOMException('Aborted', 'AbortError');
      }),
    );
    await expect(fetchShops('04823090100292', 't', controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('is not asked on the dev server, which has no functions, nor without a session', () => {
    vi.stubEnv('DEV', true);
    expect(shopsSource('token')).toBeUndefined();
    vi.stubEnv('DEV', false);
    expect(shopsSource(null)).toBeUndefined();
    expect(shopsSource('token')).toBeTypeOf('function');
  });
});
