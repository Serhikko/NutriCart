import type { FoodProduct, ProductPrefill } from './openFoodFacts';

/**
 * A product the user added under its barcode, because Open Food Facts did not
 * have it or had it without all of its nutrition. Stored in the
 * custom_products table (migration 0005), one row per user and barcode; the
 * phone keeps the same thing as a local product with the same id shape.
 */

/** The same id the phone gives a product added from the barcode flow. */
export const customProductId = (barcode: string) => `local:barcode:${barcode}`;

/** What the table accepts as a barcode: digits only, EAN-8 to GTIN-14. */
export const isStorableBarcode = (code: string) => /^\d{8,14}$/.test(code);

/** Longest name the table accepts. */
export const MAX_NAME_LENGTH = 500;

export interface CustomProductRow {
  barcode: string;
  name: string;
  brand: string | null;
  kcal_per_100g: number;
  protein_per_100g: number;
  fat_per_100g: number;
  carbs_per_100g: number;
  serving_size_g: number | null;
  liquid: boolean;
  fiber_per_100g: number | null;
  sugars_per_100g: number | null;
  salt_per_100g: number | null;
  saturated_fat_per_100g: number | null;
}

/** The columns the site reads, in one place. */
export const CUSTOM_PRODUCT_COLUMNS =
  'barcode, name, brand, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g, serving_size_g, liquid, fiber_per_100g, sugars_per_100g, salt_per_100g, saturated_fat_per_100g';

export function rowToProduct(row: CustomProductRow): FoodProduct {
  return {
    id: customProductId(row.barcode),
    barcode: row.barcode,
    name: row.name,
    brand: row.brand,
    kcalPer100g: row.kcal_per_100g,
    proteinPer100g: row.protein_per_100g,
    fatPer100g: row.fat_per_100g,
    carbsPer100g: row.carbs_per_100g,
    servingSizeG: row.serving_size_g,
    liquid: row.liquid,
    fiberPer100g: row.fiber_per_100g,
    sugarsPer100g: row.sugars_per_100g,
    saltPer100g: row.salt_per_100g,
    saturatedFatPer100g: row.saturated_fat_per_100g,
    additives: [],
  };
}

export function productToRow(p: FoodProduct): CustomProductRow {
  return {
    barcode: p.barcode,
    name: p.name,
    brand: p.brand,
    kcal_per_100g: p.kcalPer100g,
    protein_per_100g: p.proteinPer100g,
    fat_per_100g: p.fatPer100g,
    carbs_per_100g: p.carbsPer100g,
    serving_size_g: p.servingSizeG,
    liquid: p.liquid,
    fiber_per_100g: p.fiberPer100g,
    sugars_per_100g: p.sugarsPer100g,
    salt_per_100g: p.saltPer100g,
    saturated_fat_per_100g: p.saturatedFatPer100g,
  };
}

/** The saved row for the most likely form of the code: candidates are in normalizer order. */
export function pickSaved(rows: readonly CustomProductRow[], candidates: readonly string[]): CustomProductRow | null {
  for (const code of candidates) {
    const row = rows.find((r) => r.barcode === code);
    if (row) return row;
  }
  return null;
}

/** A search text as a literal inside an ILIKE pattern: `%`, `_` and `\` lose their meaning. */
export function escapeLike(text: string): string {
  return text.replace(/[\\%_]/g, (c) => `\\${c}`);
}

// ---------------------------------------------------------------------------
// The form: the phone's CustomFoodDialog rules
// ---------------------------------------------------------------------------

/** What the user types, as typed. */
export interface CustomProductDraft {
  name: string;
  brand: string;
  kcal: string;
  protein: string;
  fat: string;
  carbs: string;
  serving: string;
  liquid: boolean;
  fiber: string;
  sugars: string;
  salt: string;
  saturatedFat: string;
}

export type DraftField = Exclude<keyof CustomProductDraft, 'brand' | 'liquid'>;

/** `"12,5"` and `"12.5"` -> 12.5; blank or anything else -> null. */
export function parseDecimal(text: string): number | null {
  const s = text.trim().replace(/,/g, '.');
  if (!/^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(s)) return null;
  const v = Number(s);
  return Number.isFinite(v) ? v : null;
}

const show = (v: number | null, decimals: number) => {
  if (v === null || !Number.isFinite(v)) return '';
  const f = 10 ** decimals;
  return String(Math.round(v * f) / f);
};

/** The form's starting values: what Open Food Facts knows, or nothing. */
export function draftFromPrefill(prefill: ProductPrefill | null): CustomProductDraft {
  return {
    name: prefill?.name ?? '',
    brand: prefill?.brand ?? '',
    // Like the phone's edit dialog: whole kcal and grams of portion, the rest as stated.
    kcal: show(prefill?.kcalPer100g ?? null, 0),
    protein: show(prefill?.proteinPer100g ?? null, 2),
    fat: show(prefill?.fatPer100g ?? null, 2),
    carbs: show(prefill?.carbsPer100g ?? null, 2),
    serving: show(prefill?.servingSizeG ?? null, 0),
    liquid: prefill?.liquid ?? false,
    fiber: show(prefill?.fiberPer100g ?? null, 2),
    sugars: show(prefill?.sugarsPer100g ?? null, 2),
    salt: show(prefill?.saltPer100g ?? null, 2),
    saturatedFat: show(prefill?.saturatedFatPer100g ?? null, 2),
  };
}

const inRange = (v: number | null, min: number, max: number) => (v !== null && v >= min && v <= max ? v : null);

export interface DraftCheck {
  /** Per field: is what is typed acceptable (a blank optional field is). */
  ok: Record<DraftField, boolean>;
  /** The product to save, when every field is acceptable. */
  product: FoodProduct | null;
}

/**
 * The phone's ranges: kcal 0..900, protein, fat and carbs 0..100, the four
 * optional details 0..100 or blank, a portion 1..5000 or blank, a name.
 */
export function checkDraft(draft: CustomProductDraft, barcode: string): DraftCheck {
  const name = draft.name.trim();
  const brand = draft.brand.trim();
  const kcal = inRange(parseDecimal(draft.kcal), 0, 900);
  const protein = inRange(parseDecimal(draft.protein), 0, 100);
  const fat = inRange(parseDecimal(draft.fat), 0, 100);
  const carbs = inRange(parseDecimal(draft.carbs), 0, 100);
  const optional = (text: string) => (text.trim() === '' ? null : inRange(parseDecimal(text), 0, 100));
  const optionalOk = (text: string) => text.trim() === '' || optional(text) !== null;
  const serving = draft.serving.trim() === '' ? null : inRange(parseDecimal(draft.serving), 1, 5000);

  const ok: Record<DraftField, boolean> = {
    name: name !== '' && name.length <= MAX_NAME_LENGTH,
    kcal: kcal !== null,
    protein: protein !== null,
    fat: fat !== null,
    carbs: carbs !== null,
    serving: draft.serving.trim() === '' || serving !== null,
    fiber: optionalOk(draft.fiber),
    sugars: optionalOk(draft.sugars),
    salt: optionalOk(draft.salt),
    saturatedFat: optionalOk(draft.saturatedFat),
  };
  if (!Object.values(ok).every(Boolean)) return { ok, product: null };

  return {
    ok,
    product: {
      id: customProductId(barcode),
      barcode,
      name,
      brand: brand === '' ? null : brand,
      kcalPer100g: kcal!,
      proteinPer100g: protein!,
      fatPer100g: fat!,
      carbsPer100g: carbs!,
      servingSizeG: serving,
      liquid: draft.liquid,
      fiberPer100g: optional(draft.fiber),
      sugarsPer100g: optional(draft.sugars),
      saltPer100g: optional(draft.salt),
      saturatedFatPer100g: optional(draft.saturatedFat),
      additives: [],
    },
  };
}
