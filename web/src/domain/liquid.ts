/**
 * Is this product a drink? Open Food Facts stores every nutriment "per 100 g"
 * even for liquids, where the label really means per 100 ml, and 1 ml of a
 * drink weighs about 1 g. The numbers therefore stay as they are; only the
 * unit shown to the user changes. Same rule as the phone's LiquidDetector.
 *
 * Ukrainian and Belarusian packs write the size in Cyrillic ("500 мл",
 * "0,5 л", "1 літр", "1 литр"). The boundaries use the Unicode letter class,
 * so a number glued to a preceding letter ("ж1л") and a unit that is only
 * the start of a word ("1 large", "1 лист", "3 ложки") do not count. The
 * text is lower-cased first, as on the phone.
 */
const VOLUME = /(^|[^\p{L}])\d+([.,]\d+)?\s*(ml|cl|dl|l|мл|л|літр\p{L}*|литр\p{L}*)(?!\p{L})/u;

export function isLiquid(nutritionDataPer: unknown, quantity: unknown, servingSize: unknown): boolean {
  if (typeof nutritionDataPer === 'string' && nutritionDataPer.trim().toLowerCase() === '100ml') return true;
  return [quantity, servingSize].some((v) => typeof v === 'string' && VOLUME.test(v.toLowerCase()));
}
