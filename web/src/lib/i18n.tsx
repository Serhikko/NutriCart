import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import en from '../locales/en.json';
import uk from '../locales/uk.json';

/**
 * Two languages, the same two as the phone, chosen from the browser and
 * changeable in Settings. Keys are flat strings; `{name}` placeholders are
 * filled by t(). No library: the whole thing is forty lines.
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
  setLocale: (locale: Locale) => void;
}

const I18nContext = createContext<I18n | null>(null);

export function translate(locale: Locale, key: string, vars?: Record<string, string | number>): string {
  const template = dictionaries[locale][key] ?? dictionaries.en[key] ?? key;
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) => String(vars[name] ?? ''));
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectLocale);
  const value = useMemo<I18n>(
    () => ({
      locale,
      tag: locale === 'uk' ? 'uk-UA' : 'en-GB',
      t: (key, vars) => translate(locale, key, vars),
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
