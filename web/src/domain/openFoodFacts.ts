import { per100gFromServing, resolveKcalPer100g } from './food';

/**
 * Port of the phone's ProductDto mapping and LenientDoubleSerializer: an Open
 * Food Facts product, community-filled and often incomplete, turned into a
 * usable product or dropped. Same rules as the phone, same test cases.
 */

export interface FoodProduct {
  /** "off:<barcode>" — the same id the phone uses in its cache. */
  id: string;
  barcode: string;
  name: string;
  brand: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  fatPer100g: number;
  carbsPer100g: number;
  servingSizeG: number | null;
  fiberPer100g: number | null;
  sugarsPer100g: number | null;
  saltPer100g: number | null;
  saturatedFatPer100g: number | null;
  additives: string[];
}

/** `12.5`, `"12.5"`, `"12,5"` -> number; `""`, `"<0.5"`, `"n/a"`, null -> null. */
export function lenientNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string') return null;
  const parsed = Number(value.trim().replace(',', '.'));
  return value.trim() !== '' && Number.isFinite(parsed) ? parsed : null;
}

type Raw = Record<string, unknown>;
const num = (n: Raw | undefined, key: string) => (n ? lenientNumber(n[key]) : null);

/** The fields the phone asks for; the same `fields` parameter keeps answers small. */
export const OFF_FIELDS = 'code,product_name,product_name_en,brands,nutriments,serving_quantity,additives_tags';

export function toProduct(raw: Raw): FoodProduct | null {
  const barcode = typeof raw.code === 'string' ? raw.code.trim() : String(raw.code ?? '').trim();
  if (!barcode) return null;
  const name =
    (typeof raw.product_name === 'string' && raw.product_name.trim()) ||
    (typeof raw.product_name_en === 'string' && raw.product_name_en.trim()) ||
    '';
  if (!name) return null;
  const n = (raw.nutriments && typeof raw.nutriments === 'object' ? (raw.nutriments as Raw) : null) ?? null;
  if (!n) return null;

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
  if (kcal === null || protein === null || fat === null || carbs === null) return null;

  const brands = typeof raw.brands === 'string' ? raw.brands.split(',')[0]?.trim() : '';
  const additives = Array.isArray(raw.additives_tags)
    ? (raw.additives_tags as unknown[])
        .filter((t): t is string => typeof t === 'string')
        .map((t) => t.slice(t.indexOf(':') + 1).toUpperCase())
        .filter((t) => /^E\d+[A-Z]*$/.test(t))
    : [];

  return {
    id: `off:${barcode}`,
    barcode,
    name,
    brand: brands || null,
    kcalPer100g: kcal,
    proteinPer100g: protein,
    fatPer100g: fat,
    carbsPer100g: carbs,
    servingSizeG,
    fiberPer100g: core(num(n, 'fiber_100g'), num(n, 'fiber_serving')),
    sugarsPer100g: core(num(n, 'sugars_100g'), num(n, 'sugars_serving')),
    saltPer100g: core(num(n, 'salt_100g'), num(n, 'salt_serving')),
    saturatedFatPer100g: core(num(n, 'saturated-fat_100g'), num(n, 'saturated-fat_serving')),
    additives,
  };
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
