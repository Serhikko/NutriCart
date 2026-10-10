import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import en from '../locales/en.json';
import uk from '../locales/uk.json';

/**
 * Two languages, the same two as the phone, chosen from the browser and
 * changeable in Settings. Keys are flat strings; `{name}` placeholders are
 * filled by t(). Counted phrases go through tp(), which picks the
 * `key_one / _few / _many / _other` variant with Intl.PluralRules (Ukrainian
 * needs all four). No library.
 */
export type Locale = 'en' | 'uk';
type Dict = Record<string, string>;
const dictionaries: Record<Locale, Dict> = { en, uk };

const STORAGE_KEY = 'nutricart.locale';

function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'uk') return stored;
  } catch {
    /* private mode: fall through */
  }
  return typeof navigator !== 'undefined' && navigator.language.toLowerCase().startsWith('uk') ? 'uk' : 'en';
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

const TAGS: Record<Locale, string> = { en: 'en-GB', uk: 'uk-UA' };
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
  const filled = { n: new Intl.NumberFormat(TAGS[locale]).format(n), ...vars };
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
      tag: TAGS[locale],
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
