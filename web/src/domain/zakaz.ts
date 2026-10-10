import { gs1Prefix, type BarcodeCountry } from './barcodeOrigin';
import { isLiquid } from './liquid';
import type { FoodProduct, ProductPrefill } from './openFoodFacts';

/**
 * The second source for a scanned code: the product cards of Ukrainian
 * supermarkets (Auchan, Novus, METRO, EKO Market...) on zakaz.ua, the
 * white-label shop backend those chains share. Open Food Facts knows a few
 * thousand Ukrainian products; the shops list most of what is on their
 * shelves, with the nutrition from the label typed in as text.
 *
 * The API is unofficial and undocumented, so every field is treated as
 * untrusted: numbers are pulled out of strings like "197.00ккал", "9,95г"
 * or "389,2/1625,6", checked against the ranges a label can have, and the
 * four values must agree with each other before the product is logged
 * as is. Anything less becomes a prefill the user checks against the label.
 * Same rules and test vectors as the phone.
 *
 * The site only ever asks for the one code that was scanned, through
 * api/zakaz.ts, and only after Open Food Facts had nothing usable.
 */

/** The id a product found in the shops' catalogue gets, like "off:<code>" for Open Food Facts. */
export const ZAKAZ_ID_PREFIX = 'zakaz:';
export const zakazProductId = (barcode: string) => `${ZAKAZ_ID_PREFIX}${barcode}`;
export const isZakazProduct = (product: { id: string }) => product.id.startsWith(ZAKAZ_ID_PREFIX);

/**
 * The code as zakaz.ua stores it: a GTIN-14, the retail code left-padded with
 * zeros. Only an all-digit EAN-8, UPC-A, EAN-13 or GTIN-14 has one.
 */
export function zakazCode(code: string): string | null {
  if (!/^\d+$/.test(code) || ![8, 12, 13, 14].includes(code.length)) return null;
  return code.padStart(14, '0');
}

/**
 * Worth asking the shops about? Not for Belarusian (481) or UK (500-509)
 * numbers: the shops list practically none (two Belarusian items, about
 * 180 British ones besides alcohol), so asking would only cost the user
 * time and zakaz.ua a request. Ukrainian codes and imports (German,
 * Polish, US...) are asked.
 */
export function shouldAskShops(scannedCode: string): boolean {
  if (zakazCode(scannedCode) === null) return false;
  const prefix = gs1Prefix(scannedCode);
  if (prefix === null) return false;
  const n = Number(prefix);
  return !(n === 481 || (n >= 500 && n <= 509));
}

/** The first number in a value: `12.5`, `"197.00ккал"`, `"9,95г"`, `"389,2/1625,6"`; negative or none -> null. */
export function zakazNumber(value: unknown): number | null {
  let n: number | null = null;
  if (typeof value === 'number') n = Number.isFinite(value) ? value : null;
  else if (typeof value === 'string') {
    const match = value.replace(/,/g, '.').match(/-?\d+(?:\.\d+)?/);
    n = match ? Number(match[0]) : null;
  }
  return n !== null && n >= 0 ? n : null;
}

const inRange = (v: number | null, max: number) => (v !== null && v >= 0 && v <= max ? v : null);

/**
 * Energy per 100 g in kcal. A value written only in kJ ("2252 кДж") is
 * converted; one that names both ("389 ккал / 1628 кДж") is read as kcal,
 * the first number. Outside 0..900 kcal (pure fat is ~900) -> null.
 */
export function zakazKcal(value: unknown): number | null {
  let n = zakazNumber(value);
  if (n !== null && typeof value === 'string' && /kj|кдж/i.test(value) && !/kcal|ккал/i.test(value)) {
    n = Math.round((n / 4.184) * 10) / 10;
  }
  return inRange(n, 900);
}

/** Protein, fat or carbs per 100 g: 0..100, else null. */
export function zakazMacro(value: unknown): number | null {
  return inRange(zakazNumber(value), 100);
}

/** Energy of alcohol per 1 % ABV in 100 ml: 0.789 g of ethanol at about 7 kcal/g. */
const KCAL_PER_ABV_PERCENT = 5.53;

/**
 * Do the four values agree? Energy within 20 kcal plus 35 % of the 4/9/4
 * sum (fibre and polyols explain the rest), and the macros no more than
 * 105 g in 100 g. A typing slip in a shop's card (a value per pack, a field
 * shifted by one) almost never passes both.
 *
 * Alcohol is the one big gap in 4/9/4 (about 7 kcal/g): a correct wine
 * label (about 80 kcal, 0/0/0.3) or vodka label (about 220 kcal, 0/0/0)
 * would always fail. So a drink whose title states its strength (`abv`, see
 * zakazAbv) also passes when its energy matches the sum plus the alcohol.
 * Only ever an extra way to pass, never a reason to fail.
 */
export function zakazConsistent(kcal: number, protein: number, fat: number, carbs: number, abv: number | null = null): boolean {
  if (protein + fat + carbs > 105) return false;
  const expected = 4 * protein + 9 * fat + 4 * carbs;
  const matches = (sum: number) => Math.abs(kcal - sum) <= 20 + 0.35 * Math.max(kcal, sum);
  return matches(expected) || (abv !== null && matches(expected + KCAL_PER_ABV_PERCENT * abv));
}

/**
 * The alcohol strength a drink's title states ("Вино ... 13% 0,75л" -> 13),
 * for zakazConsistent: the first "number %" in the title, at most 80. null
 * for food (a "70%" chocolate is not a drink) and for titles without one; a
 * "100%" juice says nothing about alcohol and is past the cap.
 */
export function zakazAbv(title: string | null, liquid: boolean): number | null {
  if (!liquid || title === null) return null;
  const match = /(\d+(?:[.,]\d+)?)\s*%/.exec(title);
  const stated = match ? Number(match[1].replace(',', '.')) : null;
  return stated !== null && stated <= 80 ? stated : null;
}

/** The title, whitespace runs collapsed; blank -> null. */
export function zakazName(title: unknown): string | null {
  if (typeof title !== 'string') return null;
  const s = title.replace(/\s+/g, ' ').trim();
  return s === '' ? null : s;
}

/** The shops' way of saying "no brand". */
const NO_BRAND = new Set(['без тм', 'без торгової марки']);

/** The producer's trademark, trimmed; "без тм" ("no brand": fresh produce, the shop's own counter) -> null. */
export function zakazBrand(producer: unknown): string | null {
  if (!producer || typeof producer !== 'object') return null;
  const trademark = (producer as Record<string, unknown>).trademark;
  if (typeof trademark !== 'string') return null;
  const s = trademark.trim();
  return s === '' || NO_BRAND.has(s.toLowerCase()) ? null : s;
}

/** A drink: a volume in ml; never for goods sold by the kilo; else the size in the title ("0,5л", "900мл"), as for OFF. */
export function zakazLiquid(card: Record<string, unknown>): boolean {
  if (typeof card.volume === 'number' && card.volume > 0) return true;
  if (card.unit === 'kg') return false;
  return isLiquid(null, zakazName(card.title), null);
}

/** The card itself, also when the answer wraps it as `{ product: {...} }`. */
function unwrap(body: unknown): Record<string, unknown> | null {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
  const inner = (body as Record<string, unknown>).product;
  if (inner && typeof inner === 'object' && !Array.isArray(inner)) return inner as Record<string, unknown>;
  return body as Record<string, unknown>;
}

/** A shop's card turned into something the scan can use. */
export type ZakazMatch = { kind: 'product'; product: FoodProduct } | { kind: 'prefill'; prefill: ProductPrefill };

/**
 * A usable product when the card has a name and four valid values that agree
 * with each other; else a prefill with whatever is valid (also four values
 * that disagree: the user checks them against the label); else null. Shops
 * do not list fibre, sugars, salt or saturated fat, so those stay unknown.
 */
export function fromZakazCard(body: unknown, barcode: string): ZakazMatch | null {
  const card = unwrap(body);
  if (!card) return null;
  const facts = card.nutrition_facts && typeof card.nutrition_facts === 'object' ? (card.nutrition_facts as Record<string, unknown>) : {};
  const name = zakazName(card.title);
  const kcal = zakazKcal(facts.ingredient_energy);
  const protein = zakazMacro(facts.ingredient_protein);
  const fat = zakazMacro(facts.ingredient_fat);
  const carbs = zakazMacro(facts.ingredient_carbohydrates);
  const liquid = zakazLiquid(card);
  const known = {
    barcode,
    brand: zakazBrand(card.producer),
    servingSizeG: null,
    liquid,
    fiberPer100g: null,
    sugarsPer100g: null,
    saltPer100g: null,
    saturatedFatPer100g: null,
  };
  if (name !== null && kcal !== null && protein !== null && fat !== null && carbs !== null && zakazConsistent(kcal, protein, fat, carbs, zakazAbv(name, liquid))) {
    return {
      kind: 'product',
      product: { ...known, id: zakazProductId(barcode), name, kcalPer100g: kcal, proteinPer100g: protein, fatPer100g: fat, carbsPer100g: carbs, additives: [] },
    };
  }
  if (name === null && kcal === null && protein === null && fat === null && carbs === null) return null;
  return { kind: 'prefill', prefill: { ...known, name, kcalPer100g: kcal, proteinPer100g: protein, fatPer100g: fat, carbsPer100g: carbs } };
}

/** What the shops answered for one code. `cards` are the shops' 200 answers, in store-preference order. */
export type ShopAnswer = { kind: 'hits'; cards: unknown[] } | { kind: 'miss' } | { kind: 'unavailable' };

/** The first usable product in preference order, else the first prefill, else null (a listing with nothing usable is a miss). */
export function pickShopMatch(cards: readonly unknown[], barcode: string): ZakazMatch | null {
  const matches = cards.map((card) => fromZakazCard(card, barcode));
  return matches.find((m) => m?.kind === 'product') ?? matches.find((m) => m?.kind === 'prefill') ?? null;
}

/** Whether the shops were asked about a code nobody knew, and how that went. */
export type ShopsStatus = 'NOT_ASKED' | 'MISS' | 'UNAVAILABLE';

/** The message for a code nobody knew. */
export type LookupNotice =
  | 'OFF_DOWN'
  | 'OFF_DOWN_SHOPS_MISS'
  | 'NOT_FOUND_UKRAINE'
  | 'NOT_FOUND_BELARUS'
  | 'NOT_FOUND_OTHER'
  | 'NOT_FOUND_UKRAINE_SHOPS'
  | 'NOT_FOUND_OTHER_SHOPS'
  | 'NOT_FOUND_SHOPS_DOWN';

/**
 * Which message a "not found" gets. It only ever says a source does not have
 * the product when that source answered: Open Food Facts being busy or down
 * is said as such, and so are shops that did not answer.
 */
export function lookupNotice(country: BarcodeCountry | null, offUnavailable: boolean, shops: ShopsStatus): LookupNotice {
  if (offUnavailable) return shops === 'MISS' ? 'OFF_DOWN_SHOPS_MISS' : 'OFF_DOWN';
  switch (shops) {
    case 'MISS':
      return country === 'UKRAINE' ? 'NOT_FOUND_UKRAINE_SHOPS' : 'NOT_FOUND_OTHER_SHOPS';
    case 'UNAVAILABLE':
      return 'NOT_FOUND_SHOPS_DOWN';
    case 'NOT_ASKED':
      return country === 'UKRAINE' ? 'NOT_FOUND_UKRAINE' : country === 'BELARUS' ? 'NOT_FOUND_BELARUS' : 'NOT_FOUND_OTHER';
  }
}

/**
 * The notices that come from a source not answering (their text says to try
 * again later). A code too odd to add a product under still gets one of
 * these, never "no product with that barcode".
 */
export const UNANSWERED_NOTICES: readonly LookupNotice[] = ['OFF_DOWN', 'OFF_DOWN_SHOPS_MISS', 'NOT_FOUND_SHOPS_DOWN'];
