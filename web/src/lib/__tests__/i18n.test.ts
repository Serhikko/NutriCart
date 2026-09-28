import { describe, expect, it } from 'vitest';
import { translate } from '../i18n';
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
