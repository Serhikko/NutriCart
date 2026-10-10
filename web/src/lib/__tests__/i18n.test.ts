import { describe, expect, it } from 'vitest';
import { translate, translatePlural } from '../i18n';
import en from '../../locales/en.json';
import uk from '../../locales/uk.json';

describe('translations', () => {
  it('fills placeholders', () => {
    expect(translate('en', 'day.line_target', { eaten: 1230, target: 2100, remaining: 870 })).toBe('1230 / 2100 kcal, 870 left');
    expect(translate('uk', 'day.title', { name: 'Serhii' })).toBe('День: Serhii');
  });

  it('falls back to English, then to the key', () => {
    expect(translate('uk', 'no.such.key')).toBe('no.such.key');
  });

  it('both locales carry the same keys', () => {
    expect(Object.keys(uk).sort()).toEqual(Object.keys(en).sort());
  });
});

describe('plurals', () => {
  it('picks the Ukrainian form for the number', () => {
    expect(translatePlural('uk', 'add.results', 1)).toBe('1 результат');
    expect(translatePlural('uk', 'add.results', 3)).toBe('3 результати');
    expect(translatePlural('uk', 'add.results', 5)).toBe('5 результатів');
    expect(translatePlural('uk', 'add.results', 21)).toBe('21 результат');
  });

  it('uses one and other in English, and fills the other placeholders', () => {
    expect(translatePlural('en', 'meal.items', 1, { time: '08:12' })).toBe('1 item · from 08:12');
    expect(translatePlural('en', 'meal.items', 2, { time: '08:12' })).toBe('2 items · from 08:12');
  });

  it('formats the count for the locale unless it is given', () => {
    expect(translatePlural('en', 'add.results', 1234)).toBe('1,234 results');
    expect(translatePlural('en', 'add.results', 2, { n: 'two' })).toBe('two results');
  });

  it('falls back to _other, then to the bare key', () => {
    expect(translatePlural('en', 'add.results', 0)).toBe('0 results');
    expect(translatePlural('uk', 'no.such.key', 2)).toBe('no.such.key');
  });
});
