import { describe, expect, it } from 'vitest';
import {
  fromZakazCard,
  isZakazProduct,
  lookupNotice,
  pickShopMatch,
  shouldAskShops,
  zakazAbv,
  zakazBrand,
  zakazCode,
  zakazConsistent,
  zakazKcal,
  zakazMacro,
  zakazNumber,
} from '../zakaz';
import { gs1Prefix } from '../barcodeOrigin';

// The same vectors as the phone's ZakazTest: both clients read a shop's card the same way.
describe('zakaz.ua code form', () => {
  it.each([
    ['4823090100292', '04823090100292'],
    ['40111445', '00000040111445'],
    ['012345678905', '00012345678905'],
    ['04823090100292', '04823090100292'],
    ['123', null],
    ['', null],
    ['123456789012345', null],
    ['123456789', null],
    ['4823090 100292', null],
  ])('%s -> %s', (code, expected) => expect(zakazCode(code)).toBe(expected));
});

describe('which codes are worth asking the shops about', () => {
  it.each([
    ['4823090100292', true], // Ukraine
    ['4810000000001', false], // Belarus
    ['5000000000001', false], // UK
    ['5091234567890', false], // UK, the end of the 500-509 range
    ['5101234567890', true], // 510 is not the UK
    ['4006381333931', true], // Germany
    ['40111445', true], // EAN-8, German prefix
    ['50123452', false], // EAN-8, UK prefix
    ['012345678905', true], // UPC-A: US/Canada
    ['123', false],
  ])('%s -> %s', (code, ask) => expect(shouldAskShops(code)).toBe(ask));

  it('a GTIN-14 is judged by the retail code inside', () => {
    expect(shouldAskShops('04823090100292')).toBe(true);
    expect(shouldAskShops('15000000000001')).toBe(false);
  });

  it('GS1 prefix of every code length', () => {
    expect(gs1Prefix('4823090100292')).toBe('482');
    expect(gs1Prefix('50123452')).toBe('501');
    expect(gs1Prefix('012345678905')).toBe('001');
    expect(gs1Prefix('14823090100299')).toBe('482');
    expect(gs1Prefix('123')).toBeNull();
  });
});

describe('numbers in a shop card', () => {
  it.each([
    ['197.00ккал', 197],
    ['9,95г', 9.95],
    ['389,2/1625,6', 389.2],
    [' 0 ', 0],
    ['', null],
    ['—', null],
    ['abc', null],
    [12.5, 12.5],
    ['-3', null],
    [null, null],
    [undefined, null],
    [Number.NaN, null],
    [{ value: 1 }, null],
  ])('%j -> %j', (value, expected) => expect(zakazNumber(value)).toBe(expected));

  it.each([
    ['1625 кДж', 388.4],
    ['1625kJ', 388.4],
    ['389 ккал / 1628 кДж', 389],
    ['540', 540],
    ['950ккал', null],
    ['197.00ккал', 197],
    ['900', 900],
    [-1, null],
  ])('energy %j -> %j kcal', (value, expected) => expect(zakazKcal(value)).toBe(expected));

  it('macros are 0..100 g', () => {
    expect(zakazMacro('120г')).toBeNull();
    expect(zakazMacro('100')).toBe(100);
    expect(zakazMacro('0г')).toBe(0);
    expect(zakazMacro('6,3г')).toBe(6.3);
  });

  it('the four values must agree', () => {
    expect(zakazConsistent(539, 6.3, 30.9, 57.5)).toBe(true);
    expect(zakazConsistent(96, 2.9, 0.4, 3.6)).toBe(false); // kcal per pack, not per 100 g
    expect(zakazConsistent(0, 0, 0, 0)).toBe(true);
    expect(zakazConsistent(400, 40, 0, 70)).toBe(false); // 110 g of macros in 100 g
  });

  it("a drink's stated alcohol counts towards its energy, only ever as an extra way to pass", () => {
    expect(zakazConsistent(80, 0, 0, 0.3)).toBe(false); // a dry wine, read as 4/9/4 alone
    expect(zakazConsistent(80, 0, 0, 0.3, 13)).toBe(true);
    expect(zakazConsistent(225, 0, 0, 0, 40)).toBe(true); // whisky
    expect(zakazConsistent(240, 0, 0, 1.5, 40)).toBe(true); // brandy
    expect(zakazConsistent(539, 6.3, 30.9, 57.5, 40)).toBe(true); // never a reason to fail
    expect(zakazConsistent(42, 0, 0, 10.6, 0)).toBe(true);
    expect(zakazConsistent(900, 0, 0, 0, 13)).toBe(false); // still has to be close
    expect(zakazConsistent(400, 40, 0, 70, 13)).toBe(false); // the 105 g limit stays
  });

  it.each([
    ['Вино біле сухе 13% 0,75л', true, 13],
    ['Пиво світле 4,5% 0,5л', true, 4.5],
    ['Віскі купажований 40% 0,7л', true, 40],
    ['Вино ігристе біле брют 12 % 0,75л', true, 12],
    ['Сік апельсиновий 100% 1л', true, null], // past the 80 % cap
    ['Вода питна 0,5л', true, null],
    ['Шоколад чорний 70% 100г', false, null], // food is never alcohol
    [null, true, null],
  ] as const)('strength in %j (a drink: %s) -> %j', (title, liquid, abv) => expect(zakazAbv(title, liquid)).toBe(abv));

  it('"no brand" is no brand', () => {
    expect(zakazBrand({ trademark: ' Nutella ' })).toBe('Nutella');
    expect(zakazBrand({ trademark: 'без тм' })).toBeNull();
    expect(zakazBrand({ trademark: 'Без ТМ' })).toBeNull();
    expect(zakazBrand({ trademark: 'без торгової марки' })).toBeNull();
    expect(zakazBrand({ trademark: '  ' })).toBeNull();
    expect(zakazBrand(null)).toBeNull();
  });
});

const V1 = {
  title: ' Нутелла  паста горіхова 350г ',
  producer: { trademark: 'Nutella' },
  weight: 350,
  volume: null,
  unit: 'pcs',
  nutrition_facts: { ingredient_energy: '539.00ккал', ingredient_protein: '6,3г', ingredient_fat: '30,9г', ingredient_carbohydrates: '57,5г' },
};
const CODE = '4823090100292';
const noDetails = { servingSizeG: null, fiberPer100g: null, sugarsPer100g: null, saltPer100g: null, saturatedFatPer100g: null };

describe('a shop card as a product or a prefill', () => {
  it('V1: a complete card is a product', () => {
    expect(fromZakazCard(V1, CODE)).toEqual({
      kind: 'product',
      product: {
        id: 'zakaz:4823090100292',
        barcode: CODE,
        name: 'Нутелла паста горіхова 350г',
        brand: 'Nutella',
        kcalPer100g: 539,
        proteinPer100g: 6.3,
        fatPer100g: 30.9,
        carbsPer100g: 57.5,
        liquid: false,
        additives: [],
        ...noDetails,
      },
    });
  });

  it('V2: a card wrapped as { product } is the same card', () => {
    expect(fromZakazCard({ product: V1 }, CODE)).toEqual(fromZakazCard(V1, CODE));
  });

  it('V3: water with a volume is a drink, all zeros', () => {
    const card = { title: 'Вода питна негазована 0,5л', unit: 'pcs', volume: 500, nutrition_facts: { ingredient_energy: '0ккал', ingredient_protein: '0г', ingredient_fat: '0г', ingredient_carbohydrates: '0г' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'product', product: { liquid: true, kcalPer100g: 0, proteinPer100g: 0, fatPer100g: 0, carbsPer100g: 0, brand: null } });
  });

  it('V4: a drink by the size in its title', () => {
    const card = { title: 'Молоко 2,5% 900мл', volume: null, unit: 'pcs', nutrition_facts: { ingredient_energy: '52ккал', ingredient_protein: '2,8', ingredient_fat: '2,5', ingredient_carbohydrates: '4,7' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'product', product: { name: 'Молоко 2,5% 900мл', liquid: true, kcalPer100g: 52, proteinPer100g: 2.8, fatPer100g: 2.5, carbsPer100g: 4.7 } });
  });

  it('V5: a missing value makes a prefill with the rest', () => {
    const { ingredient_carbohydrates: _dropped, ...facts } = V1.nutrition_facts;
    expect(fromZakazCard({ ...V1, nutrition_facts: facts }, CODE)).toEqual({
      kind: 'prefill',
      prefill: { barcode: CODE, name: 'Нутелла паста горіхова 350г', brand: 'Nutella', kcalPer100g: 539, proteinPer100g: 6.3, fatPer100g: 30.9, carbsPer100g: null, liquid: false, ...noDetails },
    });
  });

  it('V6: values that disagree are a prefill with all of them, for the user to check', () => {
    const card = { title: 'Шпинат', unit: 'kg', nutrition_facts: { ingredient_energy: '96ккал', ingredient_protein: '2,9', ingredient_fat: '0,4', ingredient_carbohydrates: '3,6' } };
    expect(fromZakazCard(card, CODE)).toEqual({
      kind: 'prefill',
      prefill: { barcode: CODE, name: 'Шпинат', brand: null, kcalPer100g: 96, proteinPer100g: 2.9, fatPer100g: 0.4, carbsPer100g: 3.6, liquid: false, ...noDetails },
    });
  });

  it('V7: energy in kJ only is converted', () => {
    const card = { ...V1, nutrition_facts: { ...V1.nutrition_facts, ingredient_energy: '2252 кДж' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'product', product: { kcalPer100g: 538.2 } });
  });

  it.each([[{}], [{ title: '  ', nutrition_facts: {} }], [null], ['card'], [[V1]]])('V8: nothing usable is nothing: %j', (card) => {
    expect(fromZakazCard(card, CODE)).toBeNull();
  });

  it('V9: "без тм" is no brand', () => {
    expect(fromZakazCard({ ...V1, producer: { trademark: 'без тм' } }, CODE)).toMatchObject({ kind: 'product', product: { brand: null, name: 'Нутелла паста горіхова 350г' } });
  });

  it('V10: a soft drink', () => {
    const card = { title: 'Напій сильногазований 1,75л', volume: 1750, unit: 'pcs', nutrition_facts: { ingredient_energy: '42ккал', ingredient_protein: '0', ingredient_fat: '0', ingredient_carbohydrates: '10,6' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'product', product: { liquid: true, kcalPer100g: 42, carbsPer100g: 10.6 } });
  });

  it('V11: a wine, whose energy is mostly alcohol, is a product', () => {
    const card = { title: 'Вино біле сухе 13% 0,75л', producer: { trademark: 'Виноробня Тест' }, volume: 750, unit: 'pcs', nutrition_facts: { ingredient_energy: '80ккал', ingredient_protein: '0', ingredient_fat: '0', ingredient_carbohydrates: '0,3' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'product', product: { brand: 'Виноробня Тест', liquid: true, kcalPer100g: 80, proteinPer100g: 0, fatPer100g: 0, carbsPer100g: 0.3 } });
  });

  it('V12: a percentage in the name of a food earns no alcohol allowance', () => {
    const card = { title: 'Шоколад чорний 70% 100г', unit: 'pcs', nutrition_facts: { ingredient_energy: '80ккал', ingredient_protein: '0', ingredient_fat: '0', ingredient_carbohydrates: '0,3' } };
    expect(fromZakazCard(card, CODE)).toMatchObject({ kind: 'prefill', prefill: { liquid: false, kcalPer100g: 80 } });
  });

  it('a name alone, or a value alone, is still a prefill', () => {
    expect(fromZakazCard({ title: 'Сир' }, CODE)).toMatchObject({ kind: 'prefill', prefill: { name: 'Сир', kcalPer100g: null } });
    expect(fromZakazCard({ nutrition_facts: { ingredient_fat: '5' } }, CODE)).toMatchObject({ kind: 'prefill', prefill: { name: null, fatPer100g: 5 } });
  });

  it('goods sold by the kilo are never drinks, whatever the title says', () => {
    expect(fromZakazCard({ ...V1, title: 'Огірки 1л банка', unit: 'kg' }, CODE)).toMatchObject({ product: { liquid: false } });
  });

  it('a product from the shops is recognised by its id', () => {
    const match = fromZakazCard(V1, CODE);
    expect(match?.kind === 'product' && isZakazProduct(match.product)).toBe(true);
    expect(isZakazProduct({ id: 'off:4823090100292' })).toBe(false);
  });
});

describe('several shops listing the code', () => {
  const partial = { title: 'Нутелла', nutrition_facts: {} };
  it('the first usable card in preference order wins over an earlier partial one', () => {
    expect(pickShopMatch([partial, V1, { ...V1, title: 'Інша назва' }], CODE)).toMatchObject({ kind: 'product', product: { name: 'Нутелла паста горіхова 350г' } });
  });
  it('else the first prefill', () => {
    expect(pickShopMatch([{}, partial, { title: 'Друга' }], CODE)).toMatchObject({ kind: 'prefill', prefill: { name: 'Нутелла' } });
  });
  it('cards with nothing usable are a miss', () => {
    expect(pickShopMatch([{}, { title: ' ' }], CODE)).toBeNull();
    expect(pickShopMatch([], CODE)).toBeNull();
  });
});

describe('the message for a code nobody knew', () => {
  it.each([
    [null, true, 'NOT_ASKED', 'OFF_DOWN'],
    ['UKRAINE', true, 'UNAVAILABLE', 'OFF_DOWN'],
    ['UKRAINE', true, 'MISS', 'OFF_DOWN_SHOPS_MISS'],
    [null, true, 'MISS', 'OFF_DOWN_SHOPS_MISS'],
    ['UKRAINE', false, 'MISS', 'NOT_FOUND_UKRAINE_SHOPS'],
    [null, false, 'MISS', 'NOT_FOUND_OTHER_SHOPS'],
    ['BELARUS', false, 'MISS', 'NOT_FOUND_OTHER_SHOPS'],
    ['UKRAINE', false, 'UNAVAILABLE', 'NOT_FOUND_SHOPS_DOWN'],
    [null, false, 'UNAVAILABLE', 'NOT_FOUND_SHOPS_DOWN'],
    ['UKRAINE', false, 'NOT_ASKED', 'NOT_FOUND_UKRAINE'],
    ['BELARUS', false, 'NOT_ASKED', 'NOT_FOUND_BELARUS'],
    [null, false, 'NOT_ASKED', 'NOT_FOUND_OTHER'],
  ] as const)('%s, OFF unavailable %s, shops %s -> %s', (country, offUnavailable, shops, notice) => {
    expect(lookupNotice(country, offUnavailable, shops)).toBe(notice);
  });
});
