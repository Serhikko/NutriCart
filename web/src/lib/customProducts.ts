import { supabase } from './supabase';
import type { FoodProduct } from '../domain/openFoodFacts';
import { CUSTOM_PRODUCT_COLUMNS, escapeLike, pickSaved, productToRow, rowToProduct, type CustomProductRow } from '../domain/customProduct';

/**
 * The signed-in user's own products, kept under their barcodes in
 * custom_products (migration 0005). Reads never get in the way: if the table
 * is not there yet (the migration has not been run) or anything else fails,
 * the site behaves as if the user had saved nothing, and scanning and search
 * carry on with Open Food Facts alone.
 */
const TABLE = 'custom_products';

/** The user's product under one of these forms of a code, the most likely form first; null on any failure. */
export async function savedProductFor(userId: string | null, candidates: string[]): Promise<FoodProduct | null> {
  if (!userId || candidates.length === 0) return null;
  try {
    const { data, error } = await supabase.from(TABLE).select(CUSTOM_PRODUCT_COLUMNS).eq('owner_id', userId).in('barcode', candidates);
    if (error || !data) return null;
    const row = pickSaved(data as unknown as CustomProductRow[], candidates);
    return row ? rowToProduct(row) : null;
  } catch {
    return null;
  }
}

/** The user's products whose name contains the text, any case; empty on any failure. */
export async function searchCustomProducts(userId: string | null, query: string): Promise<FoodProduct[]> {
  const text = query.trim();
  if (!userId || text === '') return [];
  try {
    const { data, error } = await supabase
      .from(TABLE)
      .select(CUSTOM_PRODUCT_COLUMNS)
      .eq('owner_id', userId)
      .ilike('name', `%${escapeLike(text)}%`)
      .order('updated_at', { ascending: false })
      .limit(25);
    if (error || !data) return [];
    return (data as unknown as CustomProductRow[]).map(rowToProduct);
  } catch {
    return [];
  }
}

/** Saves (or replaces) the user's product under its barcode. Throws when it could not be saved. */
export async function saveCustomProduct(userId: string | null, product: FoodProduct): Promise<void> {
  if (!userId) throw new Error('not signed in');
  const { error } = await supabase.from(TABLE).upsert({ owner_id: userId, ...productToRow(product) }, { onConflict: 'owner_id,barcode' });
  if (error) throw error;
}
