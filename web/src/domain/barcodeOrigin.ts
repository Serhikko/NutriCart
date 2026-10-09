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

/**
 * The first three digits of the code's 13-digit form, its GS1 prefix: an
 * EAN-13 and an EAN-8 start with it, a 12-digit UPC-A is an EAN-13 with a
 * leading 0 (so always 0xx, US/Canada numbering), and a GTIN-14 wraps the
 * retail code behind a packaging-level digit. Null for any other length.
 */
export function gs1Prefix(scanned: string): string | null {
  const digits = scanned.replace(/\D/g, '');
  switch (digits.length) {
    case 8:
    case 13:
      return digits.slice(0, 3);
    case 12:
      return `0${digits.slice(0, 2)}`;
    case 14:
      return digits.slice(1, 4);
    default:
      return null;
  }
}

export function countryOf(scanned: string): BarcodeCountry | null {
  // A UPC-A's prefix is 0xx, so it never reads as Ukrainian or Belarusian.
  const prefix = gs1Prefix(scanned);
  return prefix === null ? null : (PREFIXES[prefix] ?? null);
}
