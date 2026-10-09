import { describe, expect, it } from 'vitest';
import { countryOf } from '../barcodeOrigin';

// The same vectors as the phone's BarcodeOriginTest.
describe('barcode origin (GS1 prefix)', () => {
  it.each([
    ['4820024700016', 'UKRAINE'],
    ['4810268000013', 'BELARUS'],
    ['48212345', 'UKRAINE'], // EAN-8
    ['14820024700013', 'UKRAINE'], // GTIN-14: the first digit is dropped
    ['482002470001', null], // 12 digits is UPC-A, US/Canada numbering
    ['0482002470001', null],
    ['5000112637922', null],
    ['4800000000000', null],
    ['4830000000000', null],
    ['abc 482 0024 700016', 'UKRAINE'], // digits only
    ['', null],
  ])('%s -> %s', (code, country) => expect(countryOf(code)).toBe(country));

  it('does not need a valid check digit', () => expect(countryOf('4820024700017')).toBe('UKRAINE'));
  it('8-digit Belarusian and other lengths', () => {
    expect(countryOf('48112345')).toBe('BELARUS');
    expect(countryOf('4821234')).toBeNull();
    expect(countryOf('482002470001612')).toBeNull();
  });
});
