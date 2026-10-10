import { countryOf } from './barcodeOrigin';
import { per100gFromServing, resolveKcalPer100g } from './food';
import { isLiquid } from './liquid';

/**
 * Port of the phone's ProductDto mapping and LenientDoubleSerializer: an Open
 * Food Facts product, community-filled and often incomplete, turned into a
 * usable product or dropped. Same rules as the phone, same test cases.
 *
 * Both mappings below share one resolution path: toProduct() is a usable
 * product or nothing, toPrefill() is everything OFF knows about a product
 * that is not complete yet, so the user can fill in the rest from the label.
 */

export interface FoodProduct {
  /**
   * "off:<barcode>" — the same id the phone uses in its cache; "local:barcode:<digits>" for one the user
   * added; "zakaz:<digits>" for one found in the Ukrainian shops' catalogue (domain/zakaz.ts).
   */
  id: string;
  barcode: string;
  name: string;
  brand: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  fatPer100g: number;
  carbsPer100g: number;
  servingSizeG: number | null;
  /** A drink: amounts are entered and shown in ml (1 ml ≈ 1 g, the numbers are unchanged). */
  liquid: boolean;
  fiberPer100g: number | null;
  sugarsPer100g: number | null;
  saltPer100g: number | null;
  saturatedFatPer100g: number | null;
  additives: string[];
}

/**
 * A product a source knows but cannot be logged yet (Open Food Facts, or a
 * Ukrainian shop's card): every value resolved by that source's rules, any
 * of them may be missing.
 */
export interface ProductPrefill {
  barcode: string;
  name: string | null;
  brand: string | null;
  kcalPer100g: number | null;
  proteinPer100g: number | null;
  fatPer100g: number | null;
  carbsPer100g: number | null;
  servingSizeG: number | null;
  liquid: boolean;
  fiberPer100g: number | null;
  sugarsPer100g: number | null;
  saltPer100g: number | null;
  saturatedFatPer100g: number | null;
  /**
   * A core value (energy, protein, fat or carbs) is Open Food Facts' estimate
   * from the ingredients, not a stated one. With all four core values known
   * (hasCoreValues) the form asks the user to check the numbers rather than
   * fill gaps. Absent means false.
   */
  estimated?: boolean;
}

/**
 * All four core values are filled in. In a prefill (a product that was not
 * usable as is) the numbers then mostly need checking rather than
 * completing: OFF's estimates, or shop values that don't add up.
 */
export const hasCoreValues = (p: ProductPrefill) => p.kcalPer100g !== null && p.proteinPer100g !== null && p.fatPer100g !== null && p.carbsPer100g !== null;

/** Any name or core value at all; an OFF record with neither is a bare stub, no better than nothing. */
export const knowsAnything = (p: ProductPrefill) =>
  p.name !== null || p.kcalPer100g !== null || p.proteinPer100g !== null || p.fatPer100g !== null || p.carbsPer100g !== null;

/** `12.5`, `"12.5"`, `"12,5"` -> number; `""`, `"<0.5"`, `"n/a"`, null -> null. */
export function lenientNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string') return null;
  const parsed = Number(value.trim().replace(',', '.'));
  return value.trim() !== '' && Number.isFinite(parsed) ? parsed : null;
}

type Raw = Record<string, unknown>;
const num = (n: Raw | null, key: string) => (n ? lenientNumber(n[key]) : null);

/**
 * The fields the phone asks for; the same `fields` parameter keeps answers
 * small, and OFF returns only what is asked. The per-language names are there
 * because Ukrainian and Belarusian products often have no `product_name`,
 * only `product_name_uk` / `_ru` / `_be`, or only a generic name.
 * `nutriments_estimated` is OFF's guess from the ingredient list: only ever
 * a starting value in the form the user checks (toPrefill), never a product.
 */
export const OFF_FIELDS = [
  'code',
  'product_name',
  'product_name_en',
  'product_name_uk',
  'product_name_ru',
  'product_name_be',
  'generic_name',
  'generic_name_en',
  'generic_name_uk',
  'generic_name_ru',
  'generic_name_be',
  'brands',
  'nutriments',
  'nutriments_estimated',
  'serving_quantity',
  'serving_size',
  'quantity',
  'nutrition_data_per',
  'additives_tags',
].join(',');

/**
 * Which language's name to prefer, by the barcode's GS1 origin. Never the
 * site's own language: the phone caches the name, and both must agree.
 */
export function nameLanguages(code: string): readonly string[] {
  switch (countryOf(code)) {
    case 'UKRAINE':
      return ['uk', 'ru', 'en', 'be'];
    case 'BELARUS':
      return ['be', 'ru', 'en', 'uk'];
    default:
      return ['en', 'uk', 'ru', 'be'];
  }
}

/** Trimmed, whitespace runs collapsed to one space; blank -> null. */
function cleanName(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const s = value.replace(/\s+/g, ' ').trim();
  return s === '' ? null : s;
}

/**
 * The product's name: `product_name`, then `product_name_<lang>` in the
 * order of nameLanguages(code), then `generic_name`, then `generic_name_<lang>`
 * in the same order. The first one that is not blank wins.
 */
export function productName(raw: Raw, code: string): string | null {
  const langs = nameLanguages(code);
  const keys = ['product_name', ...langs.map((l) => `product_name_${l}`), 'generic_name', ...langs.map((l) => `generic_name_${l}`)];
  for (const key of keys) {
    const name = cleanName(raw[key]);
    if (name !== null) return name;
  }
  return null;
}

function codeOf(raw: Raw): string {
  return typeof raw.code === 'string' ? raw.code.trim() : String(raw.code ?? '').trim();
}

/** The one resolution path: per 100 g, else per serving rescaled; energy kcal -> kJ -> serving -> macros. */
function resolve(raw: Raw, barcode: string): { prefill: ProductPrefill; additives: string[] } {
  const n = raw.nutriments && typeof raw.nutriments === 'object' ? (raw.nutriments as Raw) : null;

  const servingSizeG = (() => {
    const s = lenientNumber(raw.serving_quantity);
    return s !== null && s > 0 ? s : null;
  })();
  const core = (per100g: number | null, perServing: number | null) => per100g ?? per100gFromServing(perServing, servingSizeG);

  const protein = core(num(n, 'proteins_100g'), num(n, 'proteins_serving'));
  const fat = core(num(n, 'fat_100g'), num(n, 'fat_serving'));
  const carbs = core(num(n, 'carbohydrates_100g'), num(n, 'carbohydrates_serving'));
  const kcal = resolveKcalPer100g({
    kcalPer100g: num(n, 'energy-kcal_100g'),
    kjPer100g: num(n, 'energy-kj_100g') ?? num(n, 'energy_100g'),
    kcalPerServing: num(n, 'energy-kcal_serving'),
    kjPerServing: num(n, 'energy-kj_serving') ?? num(n, 'energy_serving'),
    servingSizeG,
    proteinPer100g: protein,
    fatPer100g: fat,
    carbsPer100g: carbs,
  });

  const brands = typeof raw.brands === 'string' ? raw.brands.split(',')[0]?.trim() : '';
  const additives = Array.isArray(raw.additives_tags)
    ? (raw.additives_tags as unknown[])
        .filter((t): t is string => typeof t === 'string')
        .map((t) => t.slice(t.indexOf(':') + 1).toUpperCase())
        .filter((t) => /^E\d+[A-Z]*$/.test(t))
    : [];

  return {
    prefill: {
      barcode,
      name: productName(raw, barcode),
      brand: brands || null,
      kcalPer100g: kcal,
      proteinPer100g: protein,
      fatPer100g: fat,
      carbsPer100g: carbs,
      servingSizeG,
      liquid: isLiquid(raw.nutrition_data_per, raw.quantity, raw.serving_size),
      fiberPer100g: core(num(n, 'fiber_100g'), num(n, 'fiber_serving')),
      sugarsPer100g: core(num(n, 'sugars_100g'), num(n, 'sugars_serving')),
      saltPer100g: core(num(n, 'salt_100g'), num(n, 'salt_serving')),
      saturatedFatPer100g: core(num(n, 'saturated-fat_100g'), num(n, 'saturated-fat_serving')),
    },
    additives,
  };
}

/** A usable product: a code, a name, energy and all three macros. Anything less is dropped (see toPrefill). */
export function toProduct(raw: Raw): FoodProduct | null {
  const barcode = codeOf(raw);
  if (!barcode) return null;
  const { prefill: p, additives } = resolve(raw, barcode);
  const { name, kcalPer100g, proteinPer100g, fatPer100g, carbsPer100g } = p;
  if (name === null || kcalPer100g === null || proteinPer100g === null || fatPer100g === null || carbsPer100g === null) return null;
  return { ...p, id: `off:${barcode}`, name, kcalPer100g, proteinPer100g, fatPer100g, carbsPer100g, additives };
}

/** The core values among ESTIMATED: one of them estimated makes the prefill `estimated`. */
const CORE = ['kcalPer100g', 'proteinPer100g', 'fatPer100g', 'carbsPer100g'] as const;

/** The per-100 g values OFF estimates from the ingredients, for the ones the label data lacks. */
const ESTIMATED = {
  kcalPer100g: 'energy-kcal_100g',
  proteinPer100g: 'proteins_100g',
  fatPer100g: 'fat_100g',
  carbsPer100g: 'carbohydrates_100g',
  fiberPer100g: 'fiber_100g',
  sugarsPer100g: 'sugars_100g',
  saltPer100g: 'salt_100g',
  saturatedFatPer100g: 'saturated-fat_100g',
} as const;

/**
 * What OFF knows about a product even when toProduct() drops it, to prefill
 * the "add this product" form. `lookedUpCode` stands in when OFF's answer
 * carries no code of its own.
 *
 * A value the label data cannot give in any form (per 100 g, per serving,
 * kJ) is taken from `nutriments_estimated` when OFF has one: a named product
 * with only estimates then opens the form with numbers the user confirms
 * against the label, instead of an empty one, and `estimated` tells the form
 * to ask for a check of those numbers. toProduct() never does this, so an
 * estimate is never logged without the user seeing it.
 */
export function toPrefill(raw: Raw, lookedUpCode: string): ProductPrefill {
  const prefill = resolve(raw, codeOf(raw) || lookedUpCode.trim()).prefill;
  const estimated = raw.nutriments_estimated && typeof raw.nutriments_estimated === 'object' ? (raw.nutriments_estimated as Raw) : null;
  if (!estimated) return prefill;
  const filled = { ...prefill };
  for (const [field, key] of Object.entries(ESTIMATED) as [keyof typeof ESTIMATED, string][]) {
    filled[field] ??= num(estimated, key);
  }
  // Set only when true, so a prefill without estimates looks exactly as before.
  if (CORE.some((field) => prefill[field] === null && filled[field] !== null)) filled.estimated = true;
  return filled;
}

/**
 * The codes worth asking OFF for, in order: OFF ignores leading zeros when
 * it looks a code up, so `012345678905` and `0012345678905` are one request,
 * not two. Asking once per stripped form keeps a scan within OFF's limit of
 * 15 product reads a minute per address. The user's own products are still
 * checked under every candidate.
 */
export function offCodes(candidates: readonly string[]): string[] {
  const seen = new Set<string>();
  return candidates.filter((code) => {
    const key = code.replace(/^0+/, '');
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/**
 * Port of BarcodeNormalizer.candidates: the forms a scanned code may be stored
 * under, most likely first. UPC-A gets its leading zero, an 8-digit UPC-E is
 * expanded, GTIN-14 wraps the retail code.
 */
export function barcodeCandidates(raw: string): string[] {
  const digits = raw.replace(/\D/g, '');
  if (!digits) return [];
  const out = [digits];
  switch (digits.length) {
    case 12:
      out.push(`0${digits}`);
      break;
    case 13:
      if (digits.startsWith('0')) out.push(digits.slice(1));
      break;
    case 8: {
      const upcA = expandUpcE(digits);
      if (upcA) out.push(upcA, `0${upcA}`);
      out.push(digits.padStart(13, '0'));
      break;
    }
    case 14:
      out.push(digits.slice(1));
      break;
  }
  return [...new Set(out)];
}

export function expandUpcE(code: string): string | null {
  if (!/^\d{8}$/.test(code)) return null;
  const ns = code[0];
  if (ns !== '0' && ns !== '1') return null;
  const d = code.slice(1, 7);
  const check = code[7];
  const last = d[5];
  let body: string;
  if (last === '0' || last === '1' || last === '2') body = `${d[0]}${d[1]}${last}0000${d[2]}${d[3]}${d[4]}`;
  else if (last === '3') body = `${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}`;
  else if (last === '4') body = `${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}`;
  else body = `${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${last}`;
  const upcA = `${ns}${body}${check}`;
  return gtinCheckDigitValid(upcA) ? upcA : null;
}

export function gtinCheckDigitValid(code: string): boolean {
  if (![8, 12, 13, 14].includes(code.length) || !/^\d+$/.test(code)) return false;
  const digits = [...code].map(Number);
  let sum = 0;
  for (let i = 0; i < digits.length - 1; i += 1) {
    const fromRight = digits.length - 2 - i;
    sum += digits[i] * (fromRight % 2 === 0 ? 3 : 1);
  }
  return (10 - (sum % 10)) % 10 === digits[digits.length - 1];
}
