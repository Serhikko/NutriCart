import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  LOCALES,
  detectLocale,
  intlTag,
  isLocale,
  localeFromLanguages,
  localeOf,
  translate,
  translatePlural,
  weekdaySeparator,
  type Locale,
} from '../i18n';
import { longDate } from '../dayView';
import { BORROWED_BE_TAG } from '../dateFormat';
import { toEpochDay } from '../dates';
import { dayLabel } from '../weekView';
import en from '../../locales/en.json';
import uk from '../../locales/uk.json';
import be from '../../locales/be.json';
import ru from '../../locales/ru.json';

const DICTS: Record<Locale, Record<string, string>> = { en, uk, be, ru };
const OTHERS = LOCALES.filter((l) => l !== 'en');
const PLURAL = /_(zero|one|two|few|many|other)$/;
/** A counted phrase's form: `key_<category>` beside a `key_other` ("welcome.too_many" is no plural). */
const isPluralForm = (key: string, dict: Record<string, string>) => PLURAL.test(key) && `${key.replace(PLURAL, '')}_other` in dict;
const placeholders = (s: string) => [...new Set([...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]))].sort();
const TODAY = 20735; // Friday 9 October 2026

describe('translations', () => {
  it('fills placeholders', () => {
    expect(translate('en', 'day.line_target', { eaten: 1230, target: 2100, remaining: 870 })).toBe('1230 / 2100 kcal, 870 left');
    expect(translate('uk', 'day.title', { name: 'Serhii' })).toBe('День: Serhii');
  });

  it('falls back to English, then to the key', () => {
    for (const locale of LOCALES) expect(translate(locale, 'no.such.key')).toBe('no.such.key');
  });

  it('has a dictionary for every locale, and only those', () => {
    expect(Object.keys(DICTS).sort()).toEqual([...LOCALES].sort());
    expect(LOCALES).toEqual(['en', 'uk', 'be', 'ru']);
  });

  it.each(OTHERS)('%s carries the same keys as English', (locale) => {
    expect(Object.keys(DICTS[locale]).sort()).toEqual(Object.keys(en).sort());
  });

  it.each(OTHERS)('%s fills the same placeholders as English, key by key', (locale) => {
    const dict = DICTS[locale];
    const differ = Object.keys(en)
      .filter((key) => key in dict && placeholders(dict[key]).join() !== placeholders(en[key as keyof typeof en]).join())
      .map((key) => `${key}: {${placeholders(dict[key]).join('}{')}} vs {${placeholders(en[key as keyof typeof en]).join('}{')}}`);
    expect(differ).toEqual([]);
  });

  it.each(OTHERS)('%s has every plural form Ukrainian has, and every category its own rules pick', (locale) => {
    const dict = DICTS[locale];
    const ukPlurals = Object.keys(uk).filter((key) => isPluralForm(key, uk));
    expect(ukPlurals.length).toBeGreaterThan(0);
    expect(ukPlurals.filter((key) => !(key in dict))).toEqual([]);
    const bases = new Set(ukPlurals.map((key) => key.replace(PLURAL, '')));
    const categories = new Intl.PluralRules(intlTag(locale)).resolvedOptions().pluralCategories;
    const missing = [...bases].flatMap((base) => categories.map((c) => `${base}_${c}`)).filter((key) => !(key in dict));
    expect(missing).toEqual([]);
  });
});

describe('plurals', () => {
  it('picks the Ukrainian form for the number', () => {
    expect(translatePlural('uk', 'add.results', 1)).toBe('1 результат');
    expect(translatePlural('uk', 'add.results', 3)).toBe('3 результати');
    expect(translatePlural('uk', 'add.results', 5)).toBe('5 результатів');
    expect(translatePlural('uk', 'add.results', 21)).toBe('21 результат');
  });

  it.each(['be', 'ru'] as const)('picks the %s form by its own plural rules, and formats the count for it', (locale) => {
    const tag = intlTag(locale);
    const rules = new Intl.PluralRules(tag);
    const nf = new Intl.NumberFormat(tag);
    for (const n of [1, 2, 5, 11, 21, 22, 25, 1234]) {
      const template = DICTS[locale][`add.results_${rules.select(n)}`];
      expect(translatePlural(locale, 'add.results', n)).toBe(template.replace('{n}', nf.format(n)));
    }
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
    for (const locale of OTHERS) expect(translatePlural(locale, 'no.such.key', 2)).toBe('no.such.key');
  });
});

describe('language tags', () => {
  it('maps every locale to its Intl tag', () => {
    expect(LOCALES.map(intlTag)).toEqual(['en-GB', 'uk-UA', 'be-BY', 'ru-RU']);
  });

  it('maps a document lang or any tag by its primary subtag, English for the rest', () => {
    expect(intlTag('uk-UA')).toBe('uk-UA');
    expect(intlTag('ru-RU')).toBe('ru-RU');
    expect(intlTag('ru-UA')).toBe('ru-RU');
    expect(intlTag('BE')).toBe('be-BY');
    expect(intlTag('be_BY')).toBe('be-BY');
    expect(intlTag('en-US')).toBe('en-GB');
    expect(intlTag('de-DE')).toBe('en-GB');
    expect(intlTag('')).toBe('en-GB');
  });

  it('formats Belarusian with the borrowed ru-BY tag where the browser has no Belarusian (Chromium): Belarusian names, Russian separators', () => {
    const real = Intl.DateTimeFormat.supportedLocalesOf;
    const spy = vi
      .spyOn(Intl.DateTimeFormat, 'supportedLocalesOf')
      .mockImplementation((locales, options) => real([locales ?? []].flat().filter((l) => !String(l).startsWith('be')), options));
    try {
      expect(intlTag('be')).toBe(BORROWED_BE_TAG);
      expect(intlTag('be-BY')).toBe(BORROWED_BE_TAG);
      expect(LOCALES.filter((l) => l !== 'be').map(intlTag)).toEqual(['en-GB', 'uk-UA', 'ru-RU']);
      // Dates keep Belarusian month names (lib/dateFormat.ts), never Russian ones.
      expect(longDate(toEpochDay(new Date(2026, 9, 10)), intlTag('be'))).toBe('Субота, 10 кастрычніка');
      // The count is written with Belarusian's (and Russian's) separators; the form is chosen by Belarusian rules.
      const nf = new Intl.NumberFormat(BORROWED_BE_TAG);
      const one = new Intl.PluralRules('be-BY').select(12341);
      expect(translatePlural('be', 'add.results', 12341)).toBe(be[`add.results_${one}` as keyof typeof be].replace('{n}', nf.format(12341)));
    } finally {
      spy.mockRestore();
    }
    expect(intlTag('be')).toBe('be-BY');
  });

  it('knows its own locales and nothing else', () => {
    for (const locale of LOCALES) expect(isLocale(locale)).toBe(true);
    for (const other of ['de', 'uk-UA', 'EN', '', null, undefined, 1]) expect(isLocale(other)).toBe(false);
    expect(localeOf('ru-RU')).toBe('ru');
    expect(localeOf('pl-PL')).toBeNull();
  });
});

describe('detection', () => {
  const languages = (list: string[], language = list[0] ?? '') => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(list);
    vi.spyOn(navigator, 'language', 'get').mockReturnValue(language);
  };
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('takes the first of the browser languages the site has, by primary subtag', () => {
    expect(localeFromLanguages(['be-BY', 'ru'])).toBe('be');
    expect(localeFromLanguages(['ru-RU'])).toBe('ru');
    expect(localeFromLanguages(['de-DE'])).toBe('en');
    expect(localeFromLanguages(['de-DE', 'uk-UA', 'ru-RU'])).toBe('uk');
    expect(localeFromLanguages(['pl', 'en-US', 'ru'])).toBe('en');
    expect(localeFromLanguages([])).toBe('en');
  });

  it('reads navigator.languages in order', () => {
    languages(['be-BY', 'ru']);
    expect(detectLocale()).toBe('be');
    languages(['ru-RU']);
    expect(detectLocale()).toBe('ru');
    languages(['de-DE']);
    expect(detectLocale()).toBe('en');
    languages(['de-DE', 'uk']);
    expect(detectLocale()).toBe('uk');
  });

  it('falls back to navigator.language when the list is empty', () => {
    languages([], 'ru-RU');
    expect(detectLocale()).toBe('ru');
  });

  it('lets the language chosen in Settings win over the browser', () => {
    languages(['be-BY', 'ru']);
    localStorage.setItem('nutricart.locale', 'uk');
    expect(detectLocale()).toBe('uk');
    for (const locale of LOCALES) {
      localStorage.setItem('nutricart.locale', locale);
      expect(detectLocale()).toBe(locale);
    }
  });

  it('ignores a stored value that is not a site language', () => {
    languages(['ru-RU']);
    localStorage.setItem('nutricart.locale', 'de');
    expect(detectLocale()).toBe('ru');
  });
});

describe('dates', () => {
  it('puts the comma after the weekday where Intl does when it writes the date whole', () => {
    const date = new Date(2026, 9, 10);
    for (const locale of LOCALES) {
      const tag = intlTag(locale);
      const parts = new Intl.DateTimeFormat(tag, { weekday: 'long', day: 'numeric', month: 'long' }).formatToParts(date);
      const after = parts[parts.findIndex((p) => p.type === 'weekday') + 1];
      expect(after.type).toBe('literal');
      expect(weekdaySeparator(tag).trim()).toBe(after.value.trim());
    }
    expect(weekdaySeparator('en-GB')).toBe(' ');
    for (const tag of ['uk-UA', 'be-BY', 'ru-RU']) expect(weekdaySeparator(tag)).toBe(', ');
  });

  it('writes the page eyebrow and the chart header with a nominative weekday and that comma', () => {
    expect(longDate(TODAY, 'en-GB')).toBe('Friday 9 October');
    expect(longDate(TODAY, 'uk-UA')).toBe('Пʼятниця, 9 жовтня');
    expect(longDate(TODAY, 'ru-RU')).toBe('Пятница, 9 октября');
    expect(longDate(TODAY, 'be-BY')).toBe('Пятніца, 9 кастрычніка');
    expect(dayLabel(TODAY, 'ru-RU')).toBe('Пятница, 9 окт.');
    expect(dayLabel(TODAY - 5, 'be-BY')).toMatch(/^Нядзеля, 4 кас\.?$/);
  });
});
