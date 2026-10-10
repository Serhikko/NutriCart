import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { flushSync } from 'react-dom';
import { useLocation } from 'react-router-dom';
import { todayEpochDay } from './dates';

/**
 * The few pieces of motion that need script: the reduced-motion preference,
 * "is this the first open of this screen today", the previous value of a
 * number, the direction of a page transition, and the spring curves for Web
 * Animations. Everything else moves in CSS (styles/motion.css).
 *
 * The test environment (jsdom) has no matchMedia, Element.animate,
 * startViewTransition or sessionStorage guarantees: every helper here
 * feature-detects and falls back to "no motion", never to an error.
 */

type Listener = () => void;

function mediaQueryList(query: string): MediaQueryList | null {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' ? window.matchMedia(query) : null;
}

function subscribeMedia(query: string, onChange: Listener): () => void {
  const mq = mediaQueryList(query);
  if (!mq) return () => undefined;
  if (typeof mq.addEventListener === 'function') {
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }
  // Safari before 14 only knows the old listener API.
  mq.addListener(onChange);
  return () => mq.removeListener(onChange);
}

/** A media query as live state; `fallback` where matchMedia does not exist (tests). */
export function useMediaQuery(query: string, fallback = false): boolean {
  return useSyncExternalStore(
    (onChange) => subscribeMedia(query, onChange),
    () => mediaQueryList(query)?.matches ?? fallback,
    () => fallback,
  );
}

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)';

/** The preference right now, for code outside React (event handlers, WAAPI calls). */
export function prefersReducedMotion(): boolean {
  return mediaQueryList(REDUCED_MOTION)?.matches ?? false;
}

/** prefers-reduced-motion, live; false when matchMedia is missing (jsdom). */
export function useReducedMotion(): boolean {
  return useMediaQuery(REDUCED_MOTION, false);
}

/** The desktop layout (sidebar, two columns) starts at 900 px. */
export const DESKTOP_QUERY = '(min-width: 900px)';

function storageKeyFor(key: string): string {
  return `nc.open.${key}.${todayEpochDay()}`;
}

function wasOpened(storageKey: string): boolean {
  try {
    return window.sessionStorage.getItem(storageKey) !== null;
  } catch {
    // No storage (private mode, blocked site data): every visit counts as a revisit, so
    // nothing replays its full build on each tab switch.
    return true;
  }
}

function markOpened(storageKey: string): void {
  try {
    window.sessionStorage.setItem(storageKey, '1');
  } catch {
    /* nothing to remember it in */
  }
}

/**
 * True for the first open of `key` in this tab session today, false on every
 * later visit (sessionStorage 'nc.open.<key>.<epoch day>'). It stays true for
 * the whole of that first visit, so content that arrives late still builds.
 * The full entrance (rings sweeping from zero, digits arriving) plays only
 * then; revisits land on the values at once.
 */
export function useFirstOpen(key: string): boolean {
  const storageKey = storageKeyFor(key);
  const [state, setState] = useState(() => ({ storageKey, first: !wasOpened(storageKey) }));
  let current = state;
  if (state.storageKey !== storageKey) {
    // The key changed under a mounted component (the shell between routes): judge the new key now.
    current = { storageKey, first: !wasOpened(storageKey) };
    setState(current);
  }
  useEffect(() => markOpened(storageKey), [storageKey]);
  return current.first;
}

/**
 * The first-open key of a route: 'home', 'day', 'week', 'add', 'settings',
 * 'onboarding', 'partner-day', 'partner-week'. Layout puts .first or
 * .revisit on .app with it; a page that asks useFirstOpen(firstOpenKey(...))
 * (or useRouteFirstOpen()) gets the same answer as the class.
 */
export function firstOpenKey(pathname: string): string {
  if (pathname === '/') return 'home';
  if (pathname === '/settings') return 'settings';
  if (pathname === '/me/onboarding') return 'onboarding';
  if (pathname === '/me/day') return 'day';
  if (pathname === '/me/week') return 'week';
  if (pathname.startsWith('/me/add/')) return 'add';
  const partner = /^\/a\/[^/]+\/(day|week)\/?$/.exec(pathname);
  if (partner) return `partner-${partner[1]}`;
  return pathname;
}

/** useFirstOpen for the current route, the same answer as the .first / .revisit class on .app. */
export function useRouteFirstOpen(): boolean {
  return useFirstOpen(firstOpenKey(useLocation().pathname));
}

/** The value from the previous committed render (undefined on the first). */
export function usePrevious<T>(value: T): T | undefined {
  const ref = useRef<T | undefined>(undefined);
  const previous = ref.current;
  useEffect(() => {
    ref.current = value;
  });
  return previous;
}

export type NavDirection = 'forward' | 'back';

let directionTimer: ReturnType<typeof setTimeout> | undefined;

/**
 * Sets the direction the next page transition slides in from
 * (html[data-dir], read by the view-transition CSS): drill-ins are forward,
 * their back links and the previous-day button are back; null (tab switches)
 * clears it. It clears itself shortly after, so a later transition never
 * inherits a stale direction.
 */
export function setNavDirection(dir: NavDirection | null): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (dir) root.dataset.dir = dir;
  else delete root.dataset.dir;
  if (directionTimer) clearTimeout(directionTimer);
  directionTimer = dir ? setTimeout(() => delete root.dataset.dir, 1500) : undefined;
}

/** Web Animations exist (not in jsdom). */
export const canAnimate = (): boolean => typeof Element !== 'undefined' && 'animate' in Element.prototype;

type ViewTransitionDocument = Document & { startViewTransition?: (update: () => void) => unknown };

/** The View Transitions API exists (not in jsdom, not in older browsers). */
export const canViewTransition = (): boolean =>
  typeof document !== 'undefined' && typeof (document as ViewTransitionDocument).startViewTransition === 'function';

/**
 * Runs a state change inside a view transition, for changes that are not a
 * route navigation (the day navigator's ‹ ›). Route changes use react-router's
 * own `viewTransition` on Link / navigate(). Without the API the change just
 * happens. Under reduced motion the CSS turns the transition into a short
 * crossfade.
 */
export function withViewTransition(update: () => void, dir?: NavDirection | null): void {
  if (dir !== undefined) setNavDirection(dir);
  if (!canViewTransition()) {
    update();
    return;
  }
  (document as ViewTransitionDocument).startViewTransition!(() => {
    flushSync(update);
  });
}

export type SpringName = 'smooth' | 'snappy' | 'sheet' | 'bouncy';

// The cubic-bezier stand-ins tokens.css uses where linear() is not supported.
const SPRING_FALLBACK: Record<SpringName, string> = {
  smooth: 'cubic-bezier(.22, 1, .36, 1)',
  snappy: 'cubic-bezier(.2, 1.08, .36, 1)',
  sheet: 'cubic-bezier(.2, 1.12, .36, 1)',
  bouncy: 'cubic-bezier(.3, 1.5, .5, 1)',
};
const springCache = new Map<SpringName, string>();

/** The --spring-* token as an easing string for el.animate() (the linear() spring where supported). */
export function spring(name: SpringName): string {
  const cached = springCache.get(name);
  if (cached) return cached;
  let value = '';
  if (typeof document !== 'undefined' && typeof getComputedStyle === 'function') {
    value = getComputedStyle(document.documentElement).getPropertyValue(`--spring-${name}`).trim();
  }
  if (!value) return SPRING_FALLBACK[name];
  springCache.set(name, value);
  return value;
}
