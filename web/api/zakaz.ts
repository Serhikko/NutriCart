// process.env below is Node's: say so here rather than rely on another
// package's types pulling Node's in (Vercel's npm leaves those out).
/// <reference types="node" />
import type { VercelRequest, VercelResponse } from '@vercel/node';

/**
 * Asks Ukrainian supermarkets whether they list one scanned barcode: the
 * product cards of Auchan, Novus, METRO, EKO Market and other chains on
 * zakaz.ua, the shop backend they share. The site calls this only after Open
 * Food Facts had no usable product, and only for codes the shops may have
 * (not Belarusian or UK numbers); src/domain/zakaz.ts turns the cards into a
 * product or a prefill.
 *
 * GET /api/zakaz?code=<8 to 14 digits> answers
 * `{ code, answered, failed, results: [{ store, chain, product }] }`, where
 * `answered` counts the stores that answered (listed, 404, or a 200 without a
 * card), `failed` the ones that did not, and `results` holds the cards of the
 * stores that list the code, in store-preference order, cut down to the few
 * fields the site reads. The browser may keep the answer for an hour only
 * when every store answered.
 *
 * The zakaz.ua API is unofficial and undocumented. This is a point lookup per
 * scan: one store list a day, then one request per chosen store for the one
 * code, with our own User-Agent and nothing posing as a shop's web page.
 * Nothing is stored, nothing is passed on to Open Food Facts, nothing is
 * crawled. Only the barcode leaves for zakaz.ua: no user, no IP of the
 * visitor, no token.
 *
 * Self-contained on purpose: Vercel compiles each api/ file on its own and the
 * package is an ES module, so an import from ../src would fail at runtime.
 */
const ORIGIN = 'https://stores-api.zakaz.ua';
const USER_AGENT = 'NutriCart/0.1 (web; https://github.com/Serhikko/NutriCart)';
const HEADERS = { Accept: 'application/json', 'Accept-Language': 'uk', 'User-Agent': USER_AGENT };

/** The chains worth asking, best catalogue first. */
const CHAINS = ['auchan', 'novus', 'metro', 'megamarket', 'ekomarket', 'tavriav', 'ultramarket', 'epicentr', 'vostorg', 'chudomarket', 'zaraz'];
const MAX_STORES = 6;
/** A product card is the same in every store of a chain; a Kyiv store has the widest range. */
const KYIV = /ки[їє]в|kyiv|kiev/i;

interface Store {
  id: string;
  chain: string;
}

/** Stores known to answer, for when the store list cannot be had. */
const FALLBACK_STORES: Store[] = [
  { id: '48246401', chain: 'auchan' },
  { id: '48201031', chain: 'novus' },
  { id: '48215611', chain: 'metro' },
  { id: '48280214', chain: 'ekomarket' },
  { id: '48277601', chain: 'ultramarket' },
];

/** The whole function stays under ~8 s; a single store gets at most 6 s of that. */
const BUDGET_MS = 7_500;
const STORE_TIMEOUT_MS = 6_000;
const AUTH_TIMEOUT_MS = 3_000;
const STORE_LIST_TIMEOUT_MS = 2_500;
const STORE_LIST_TTL_MS = 24 * 60 * 60 * 1000;
const STORE_LIST_RETRY_MS = 10 * 60 * 1000;

/** Same rule as src/domain/zakaz.ts: an EAN-8, UPC-A, EAN-13 or GTIN-14 as zakaz.ua stores it, a GTIN-14. */
export function zakazCode(code: string): string | null {
  if (!/^\d+$/.test(code) || ![8, 12, 13, 14].includes(code.length)) return null;
  return code.padStart(14, '0');
}

function chooseStores(json: unknown): Store[] {
  if (!Array.isArray(json)) return [];
  const chosen = new Map<string, Store & { kyiv: boolean }>();
  for (const entry of json) {
    if (!entry || typeof entry !== 'object') continue;
    const { id, retail_chain: chainName, city, is_active: active } = entry as Record<string, unknown>;
    if (active !== true || typeof id !== 'string' || id === '' || typeof chainName !== 'string') continue;
    const chain = chainName.toLowerCase();
    if (!CHAINS.includes(chain)) continue;
    const kyiv = typeof city === 'string' && KYIV.test(city);
    const current = chosen.get(chain);
    // The first active store of a chain, unless a later one is in Kyiv and the first is not.
    if (!current || (kyiv && !current.kyiv)) chosen.set(chain, { id, chain, kyiv });
  }
  return CHAINS.flatMap((chain) => {
    const store = chosen.get(chain);
    return store ? [{ id: store.id, chain }] : [];
  }).slice(0, MAX_STORES);
}

/**
 * The stores to ask, from GET /stores/: active ones of the chains above, one
 * per chain (a Kyiv store when there is one), in chain-preference order, at
 * most six. Anything unusable gives an empty list, and the caller falls back.
 */
export function pickZakazStores(json: unknown): string[] {
  return chooseStores(json).map((store) => store.id);
}

type Value = string | number;
const text = (v: unknown, max: number) => (typeof v === 'string' ? v.slice(0, max) : undefined);
const count = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);
const value = (v: unknown): Value | undefined => text(v, 100) ?? count(v);

/** What the site reads from a card; everything else in it stays here. */
export interface ZakazCard {
  title?: string;
  producer: { trademark?: string };
  weight?: number;
  volume?: number;
  unit?: string;
  nutrition_facts: { ingredient_energy?: Value; ingredient_protein?: Value; ingredient_fat?: Value; ingredient_carbohydrates?: Value };
}

/** The card, unwrapped from `{ product: {...} }` when it comes that way, cut down to strings and numbers the site reads. */
export function trimCard(body: unknown): ZakazCard | null {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
  const wrapped = (body as Record<string, unknown>).product;
  const card = (wrapped && typeof wrapped === 'object' && !Array.isArray(wrapped) ? wrapped : body) as Record<string, unknown>;
  const producer = card.producer && typeof card.producer === 'object' ? (card.producer as Record<string, unknown>) : {};
  const facts = card.nutrition_facts && typeof card.nutrition_facts === 'object' ? (card.nutrition_facts as Record<string, unknown>) : {};
  return {
    title: text(card.title, 500),
    producer: { trademark: text(producer.trademark, 200) },
    weight: count(card.weight),
    volume: count(card.volume),
    unit: text(card.unit, 20),
    nutrition_facts: {
      ingredient_energy: value(facts.ingredient_energy),
      ingredient_protein: value(facts.ingredient_protein),
      ingredient_fat: value(facts.ingredient_fat),
      ingredient_carbohydrates: value(facts.ingredient_carbohydrates),
    },
  };
}

/** GET with a hard timeout that also covers reading the body. Throws on a network error, a timeout or bad JSON. */
async function getJson(url: string, headers: Record<string, string>, timeoutMs: number): Promise<{ status: number; body: unknown }> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), Math.max(1, timeoutMs));
  try {
    const res = await fetch(url, { headers, signal: controller.signal });
    if (res.status !== 200) {
      await res.body?.cancel().catch(() => undefined); // never read, never echoed
      return { status: res.status, body: null };
    }
    return { status: 200, body: await res.json() };
  } finally {
    clearTimeout(timer);
  }
}

// The store list, kept by the warm instance: fetched at most once a day, and
// after a failure not again for ten minutes (the built-in stores serve
// meanwhile, as on the phone).
let storeList: { stores: Store[]; at: number } | null = null;
let storeListFailedAt = Number.NEGATIVE_INFINITY;

async function storesToAsk(timeoutMs: number): Promise<Store[]> {
  const now = Date.now();
  if (storeList && now - storeList.at < STORE_LIST_TTL_MS) return storeList.stores;
  if (now - storeListFailedAt >= STORE_LIST_RETRY_MS) {
    try {
      const { status, body } = await getJson(`${ORIGIN}/stores/`, HEADERS, Math.min(STORE_LIST_TIMEOUT_MS, timeoutMs));
      const stores = status === 200 ? chooseStores(body) : [];
      if (stores.length > 0) {
        storeList = { stores, at: Date.now() };
        return stores;
      }
    } catch {
      // unreachable or not JSON: the same as an unusable list
    }
    storeListFailedAt = Date.now();
  }
  // The same stores as the phone asks in this case, so both give the same answer.
  return FALLBACK_STORES;
}

type StoreAnswer = { kind: 'listed'; store: Store; product: ZakazCard } | { kind: 'absent' } | { kind: 'failed' };

async function askStore(store: Store, code: string, timeoutMs: number): Promise<StoreAnswer> {
  try {
    const { status, body } = await getJson(`${ORIGIN}/stores/${encodeURIComponent(store.id)}/products/${code}/`, HEADERS, timeoutMs);
    if (status === 404) return { kind: 'absent' };
    if (status !== 200) return { kind: 'failed' };
    // The store answered. JSON that holds no card (an array, a string, null)
    // lists nothing usable, which is a miss, as on the phone.
    const product = trimCard(body);
    return product ? { kind: 'listed', store, product } : { kind: 'absent' };
  } catch {
    return { kind: 'failed' };
  }
}

/**
 * The relay is for the site's own visitors: the request must carry the
 * visitor's Supabase access token, checked with Supabase's own user endpoint.
 * A deployment without the Supabase address or key (a bare preview) has
 * nothing to check the token against, so the check is skipped there; the
 * relay then stays as open as api/off.ts is, still limited to one code per
 * request.
 */
async function authorised(req: VercelRequest, timeoutMs: number): Promise<boolean> {
  // An empty variable counts as missing, like an absent one.
  const url = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
  const anonKey = process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY;
  if (!url || !anonKey) return true;
  const header = req.headers.authorization;
  const token = typeof header === 'string' ? /^Bearer\s+(\S+)$/i.exec(header.trim())?.[1] : undefined;
  if (!token) return false;
  try {
    const { status } = await getJson(`${url.replace(/\/+$/, '')}/auth/v1/user`, { apikey: anonKey, Authorization: `Bearer ${token}` }, timeoutMs);
    return status === 200;
  } catch {
    return false;
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const deadline = Date.now() + BUDGET_MS;
  const left = () => deadline - Date.now();
  try {
    const raw = typeof req.query.code === 'string' ? req.query.code : '';
    const code = /^\d{8,14}$/.test(raw) ? zakazCode(raw) : null;
    if (code === null) {
      res.setHeader('Cache-Control', 'no-store');
      res.status(400).json({ error: 'code must be an EAN-8, UPC-A, EAN-13 or GTIN-14' });
      return;
    }
    if (!(await authorised(req, Math.min(AUTH_TIMEOUT_MS, left())))) {
      res.setHeader('Cache-Control', 'no-store');
      res.status(401).json({ error: 'sign-in required' });
      return;
    }

    const stores = await storesToAsk(left());
    const timeout = Math.min(STORE_TIMEOUT_MS, left());
    const answers = await Promise.all(stores.map((store) => askStore(store, code, timeout)));
    const failed = answers.filter((a) => a.kind === 'failed').length;
    const answered = answers.length - failed;
    const results = answers.flatMap((a) => (a.kind === 'listed' ? [{ store: a.store.id, chain: a.store.chain, product: a.product }] : []));

    // Only a full answer is worth keeping in the browser for an hour. One with a
    // store missing is not: that store may list the code once it is back.
    res.setHeader('Cache-Control', answered > 0 && failed === 0 ? 'private, max-age=3600' : 'no-store');
    res.status(200).json({ code, answered, failed, results });
  } catch {
    if (!res.headersSent) {
      res.setHeader('Cache-Control', 'no-store');
      res.status(502).json({ error: 'shops unreachable' });
    }
  }
}
