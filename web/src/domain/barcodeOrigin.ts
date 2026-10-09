/**
 * Which national GS1 office issued a barcode: 482 is GS1 Ukraine, 481 is GS1
 * Belarus. Port of the phone's BarcodeOrigin, with the same test vectors.
 *
 * A GS1 prefix says where the NUMBER was registered, not where the food was
 * made (a Ukrainian brand may pack abroad, an importer may register locally),
 * so the screens say "Ukrainian barcode" or "this Ukrainian product", never
 * "made in". The check digit is not verified: this is about origin only.
 */
export type BarcodeCountry = 'UKRAINE' | 'BELARUS';

const PREFIXES: Record<string, BarcodeCountry> = { '482': 'UKRAINE', '481': 'BELARUS' };

export function countryOf(scanned: string): BarcodeCountry | null {
  const digits = scanned.replace(/\D/g, '');
  // GTIN-14 wraps the retail code behind a packaging-level digit.
  const code = digits.length === 14 ? digits.slice(1) : digits;
  // EAN-13 and EAN-8 only; a 12-digit UPC-A is US/Canada numbering.
  if (code.length !== 13 && code.length !== 8) return null;
  return PREFIXES[code.slice(0, 3)] ?? null;
}
