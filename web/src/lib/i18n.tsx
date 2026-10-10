import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import en from '../locales/en.json';
import uk from '../locales/uk.json';
import { BORROWED_BE_TAG } from './dateFormat';
import be from '../locales/be.json';
import ru from '../locales/ru.json';

/**
 * Four languages: English, Ukrainian, Belarusian and Russian, chosen from the
 * browser's language list and changeable in Settings. Keys are flat strings;
 * `{name}` placeholders are filled by t(). Counted phrases go through tp(),
 * which picks the `key_one / _few / _many / _other` variant with
 * Intl.PluralRules (the three Slavic languages need all four). No library.
 */
export type Locale = 'en' | 'uk' | 'be' | 'ru';
type Dict = Record<string, string>;
const dictionaries: Record<Locale, Dict> = { en, uk, be, ru };

/** Every site language, in the order the language picker lists them. */
export const LOCALES: readonly Locale[] = ['en', 'uk', 'be', 'ru'];

const STORAGE_KEY = 'nutricart.locale';

/** Each language's BCP-47 tag: its plural rules, and its dates and numbers where the browser has them. */
const TAGS: Record<Locale, string> = { en: 'en-GB', uk: 'uk-UA', be: 'be-BY', ru: 'ru-RU' };

/**
 * The tag a language formats with where the browser has no data of its own.
 * Chromium's ICU has Belarusian plural rules but no Belarusian dates or
 * numbers: asked for be-BY it writes its root locale's ("M10 4–10",
 * "12,345.5"). Russian as written in Belarus has Belarusian's separators
 * ("12 345,5"), and lib/dateFormat.ts recognises this tag and writes the
 * Belarusian month and weekday names ("субота, 10 кастрычніка"), so no
 * Russian word shows on a Belarusian page. Safari and Firefox have both.
 */
const FORMAT_FALLBACK: Partial<Record<Locale, string>> = { be: BORROWED_BE_TAG };

function hasDates(tag: string): boolean {
  try {
    return Intl.DateTimeFormat.supportedLocalesOf([tag]).length > 0;
  } catch {
    return false;
  }
}

/** The tag Intl writes a site language's dates and numbers with: its own, or its fallback's where the browser lacks it. */
function formatTag(locale: Locale): string {
  const fallback = FORMAT_FALLBACK[locale];
  return fallback && !hasDates(TAGS[locale]) ? fallback : TAGS[locale];
}

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (LOCALES as readonly string[]).includes(value);
}

/** The site language a language tag names by its primary subtag ('be-BY' -> 'be', 'RU' -> 'ru'), or null. */
export function localeOf(lang: string): Locale | null {
  const primary = lang.trim().split(/[-_]/)[0].toLowerCase();
  return isLocale(primary) ? primary : null;
}

/**
 * The BCP-47 tag Intl formats dates and numbers with, for a site language or
 * for any language tag such as `document.documentElement.lang` ('uk' ->
 * 'uk-UA', 'ru-RU' -> 'ru-RU', 'be' -> 'be-BY', or 'ru-RU' in a browser
 * without Belarusian); English ('en-GB') for a language the site does not have.
 */
export function intlTag(localeOrLang: string): string {
  return formatTag(localeOf(localeOrLang) ?? 'en');
}

/** The languages that put a comma after the weekday in a date, as Intl writes them whole. */
const WEEKDAY_COMMA: ReadonlySet<Locale> = new Set(['uk', 'be', 'ru']);

/**
 * What goes between a weekday and its day and month for a language tag, when
 * the two are formatted apart: ', ' in Ukrainian, Belarusian and Russian
 * ("субота, 10 жовтня", "субота, 10 кастрычніка", "суббота, 10 октября"), a
 * space in English ("Saturday 10 October").
 */
export function weekdaySeparator(tag: string): string {
  const locale = localeOf(tag);
  return locale && WEEKDAY_COMMA.has(locale) ? ', ' : ' ';
}

/**
 * The first of the browser's languages, in its order of preference, that the
 * site has (matched by primary subtag, so 'be-BY' is Belarusian); English when
 * none is.
 */
export function localeFromLanguages(languages: readonly string[]): Locale {
  for (const lang of languages) {
    const locale = localeOf(lang);
    if (locale) return locale;
  }
  return 'en';
}

/** The language chosen in Settings, if any; otherwise the browser's languages. */
export function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (isLocale(stored)) return stored;
  } catch {
    /* private mode: fall through */
  }
  if (typeof navigator === 'undefined') return 'en';
  const languages = navigator.languages?.length ? navigator.languages : [navigator.language ?? ''];
  return localeFromLanguages(languages);
}

interface I18n {
  locale: Locale;
  /** BCP-47 tag for Intl formatting. */
  tag: string;
  t: (key: string, vars?: Record<string, string | number>) => string;
  /** A counted phrase: `{n}` is `n` formatted for the locale unless vars.n is given. */
  tp: (key: string, n: number, vars?: Record<string, string | number>) => string;
  setLocale: (locale: Locale) => void;
}

const I18nContext = createContext<I18n | null>(null);

function fill(template: string, vars?: Record<string, string | number>): string {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) => String(vars[name] ?? ''));
}

export function translate(locale: Locale, key: string, vars?: Record<string, string | number>): string {
  return fill(dictionaries[locale][key] ?? dictionaries.en[key] ?? key, vars);
}

const pluralRules = new Map<Locale, Intl.PluralRules>();

function pluralCategory(locale: Locale, n: number): string {
  let rules = pluralRules.get(locale);
  if (!rules) {
    rules = new Intl.PluralRules(TAGS[locale]);
    pluralRules.set(locale, rules);
  }
  return rules.select(n);
}

/**
 * The plural form of `key` for `n`: `key_<category>` in the locale, then
 * `key_other`, then the same two in English, then the bare key.
 */
export function translatePlural(locale: Locale, key: string, n: number, vars?: Record<string, string | number>): string {
  const category = pluralCategory(locale, n);
  const filled = { n: new Intl.NumberFormat(formatTag(locale)).format(n), ...vars };
  for (const dict of [dictionaries[locale], dictionaries.en]) {
    for (const candidate of [`${key}_${category}`, `${key}_other`]) {
      if (dict[candidate] !== undefined) return fill(dict[candidate], filled);
    }
  }
  return translate(locale, key, filled);
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectLocale);
  // Screen readers, hyphenation and the date field follow the page language.
  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);
  const value = useMemo<I18n>(
    () => ({
      locale,
      tag: formatTag(locale),
      t: (key, vars) => translate(locale, key, vars),
      tp: (key, n, vars) => translatePlural(locale, key, n, vars),
      setLocale: (next) => {
        setLocaleState(next);
        try {
          localStorage.setItem(STORAGE_KEY, next);
        } catch {
          /* ignore */
        }
      },
    }),
    [locale],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n outside I18nProvider');
  return ctx;
}
