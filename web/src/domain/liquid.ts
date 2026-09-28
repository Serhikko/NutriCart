/**
 * Is this product a drink? Open Food Facts stores every nutriment "per 100 g"
 * even for liquids, where the label really means per 100 ml, and 1 ml of a
 * drink weighs about 1 g. The numbers therefore stay as they are; only the
 * unit shown to the user changes. Same rule as the phone's LiquidDetector.
 */
const VOLUME = /(^|[^a-z])\d+([.,]\d+)?\s*(ml|cl|dl|l)(?![a-z])/i;

export function isLiquid(nutritionDataPer: unknown, quantity: unknown, servingSize: unknown): boolean {
  if (typeof nutritionDataPer === 'string' && nutritionDataPer.trim().toLowerCase() === '100ml') return true;
  return [quantity, servingSize].some((v) => typeof v === 'string' && VOLUME.test(v));
}
