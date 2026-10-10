import { useCallback, useEffect, useLayoutEffect, useRef, useState, type MouseEvent, type RefObject } from 'react';
import { Outlet, ScrollRestoration, useLocation, useNavigate, useParams, useViewTransitionState } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useProfileDetails } from '../lib/tracker';
import { todayEpochDay } from '../lib/dates';
import { guessMealSlot } from '../domain/habits';
import { DESKTOP_QUERY, firstOpenKey, prefersReducedMotion, setNavDirection, useFirstOpen, useMediaQuery } from '../lib/motion';
import { CompactTitleContext } from './ui/LargeTitle';
import { ToastProvider } from './ui/Toast';
import { TabBar, isModifiedClick, type FabAction, type NavItem } from './TabBar';
import { Sidebar } from './Sidebar';

/** Where the + button goes: today, the meal guessed from the hour (breakfast 05-10, lunch 11-15, dinner 16-21, else a snack). */
function addFoodPath(): string {
  return `/me/add/${todayEpochDay()}/${guessMealSlot(new Date().getHours())}`;
}

/** The partner's nudge card on their Day page (NudgeBox), however it is marked up. */
function findNudgeCard(title: string): HTMLElement | null {
  const byId = document.getElementById('nudge');
  if (byId) return byId;
  for (const el of document.querySelectorAll<HTMLElement>('main section[aria-label], main h2')) {
    const name = el.getAttribute('aria-label') ?? el.textContent ?? '';
    if (name.trim() === title) return el.closest('section') ?? el;
  }
  return null;
}

function revealNudgeCard(card: HTMLElement) {
  // Optional: not every environment scrolls elements (jsdom has no scrollIntoView); the focus still moves.
  card.scrollIntoView?.({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'center' });
  card.querySelector<HTMLElement>('input, textarea')?.focus({ preventScroll: true });
}

/**
 * Remembers where the page was scrolled when a sheet is presented
 * (html.presenting, set by the Sheet), so the receding stage keeps showing
 * that part of the page (--sy), and puts the scroll back when the sheet goes.
 * The last offset is taken from the scroll events before the class appeared:
 * by the time it appears the browser may already have clamped the scroll.
 * When the sheet goes because the page changed ("Add" goes back to the day),
 * the new page keeps its own scroll position: nothing is restored.
 */
function usePresentingStage(stage: RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    if (typeof MutationObserver === 'undefined') return;
    const root = document.documentElement;
    let lastY = window.scrollY;
    let openY: number | null = null;
    let openUrl = '';
    const onScroll = () => {
      if (openY === null) lastY = window.scrollY;
    };
    const onRestored = () => stage.current?.classList.remove('restoring');
    const observer = new MutationObserver(() => {
      const presenting = root.classList.contains('presenting');
      if (presenting && openY === null) {
        openY = lastY;
        openUrl = window.location.href;
        root.style.setProperty('--sy', `${openY}px`);
        stage.current?.classList.remove('restoring');
      } else if (!presenting && openY !== null) {
        const y = openY;
        openY = null;
        if (window.location.href !== openUrl) {
          lastY = window.scrollY;
          return;
        }
        window.scrollTo(0, y);
        lastY = y;
        const el = stage.current;
        if (el && !prefersReducedMotion() && !window.matchMedia?.(DESKTOP_QUERY).matches) {
          el.classList.add('restoring');
          el.addEventListener('animationend', onRestored, { once: true });
        }
      }
    });
    observer.observe(root, { attributes: true, attributeFilter: ['class'] });
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', onScroll);
    };
  }, [stage]);
}

/**
 * The compact top bar fades in as the large title scrolls away. Browsers with
 * scroll-driven animations do it in CSS alone; elsewhere a sentinel 64 px down
 * the page toggles html.scrolled.
 */
function useScrolledFallback(sentinel: RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    if (typeof CSS !== 'undefined' && CSS.supports?.('animation-timeline: scroll()')) return;
    const el = sentinel.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const root = document.documentElement;
    const observer = new IntersectionObserver(([entry]) => {
      root.classList.toggle('scrolled', !entry.isIntersecting && entry.boundingClientRect.top < 0);
    });
    observer.observe(el);
    return () => {
      observer.disconnect();
      root.classList.remove('scrolled');
    };
  }, [sentinel]);
}

/**
 * The shell around every page. Phone and tablet: the page on the stage, a
 * glass compact top bar on scroll, the scroll-edge fade, and the floating
 * capsule tab bar with its + button. Desktop (>= 900 px): a fixed sidebar
 * instead. Only one of the two navigations is rendered at a time.
 *
 * The tabs are Home, Day, Week, Settings. On a followed account
 * (/a/<owner>) Day and Week are that account's; otherwise the user's own,
 * once onboarded. The + button (own pages, once onboarded) adds food to
 * today's guessed meal; on a partner's pages it is a bell that brings up
 * the nudge card.
 */
export function Layout() {
  const { t } = useI18n();
  const { ownerId } = useParams();
  const { pathname, hash } = useLocation();
  const navigate = useNavigate();
  const { userId } = useSession();
  const details = useProfileDetails(userId);
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const first = useFirstOpen(firstOpenKey(pathname));
  // True from the tap until the router's view transition has finished (never without the API).
  // A first open's cascade IS its entrance, so html[data-vt-first] drops the new page's own fade:
  // the page is there at once under the old one's 120 ms fade-out, its cascade already running.
  // (Holding the cascade until the transition ended left an empty page for a quarter second.)
  // Set in a layout effect, it is in place before the browser captures the new page.
  const transitioning = useViewTransitionState(pathname);
  const vtFirst = first && transitioning;
  useLayoutEffect(() => {
    if (!vtFirst) return;
    const root = document.documentElement;
    root.dataset.vtFirst = '';
    return () => {
      delete root.dataset.vtFirst;
    };
  }, [vtFirst]);
  const intro = useFirstOpen('shell');
  const [compact, setCompact] = useState<string | null>(null);
  const stage = useRef<HTMLDivElement>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  usePresentingStage(stage);
  useScrolledFallback(sentinel);

  const onboarded = Boolean(details.data);
  const base = ownerId ? `/a/${ownerId}` : onboarded ? '/me' : null;
  const items: NavItem[] = [
    {
      key: 'home',
      to: '/',
      label: t('nav.home'),
      icon: 'home',
      active: pathname === '/' || (pathname === '/me/onboarding' && !onboarded),
    },
  ];
  if (base) {
    items.push(
      {
        key: 'day',
        to: `${base}/day`,
        label: t('nav.day'),
        icon: 'day',
        active: pathname === `${base}/day` || (base === '/me' && pathname.startsWith('/me/add/')),
      },
      { key: 'week', to: `${base}/week`, label: t('nav.week'), icon: 'week', active: pathname === `${base}/week` },
    );
  }
  items.push({
    key: 'settings',
    to: '/settings',
    label: t('nav.settings'),
    icon: 'settings',
    active: pathname === '/settings' || (pathname === '/me/onboarding' && onboarded),
  });

  // The bell on a partner's pages: on their Day it brings the nudge card up; from their Week it goes there first.
  const nudgeTitle = t('nudge.title');
  const openNudge = useCallback(() => {
    const card = findNudgeCard(nudgeTitle);
    if (card) revealNudgeCard(card);
    else if (ownerId) {
      setNavDirection(null);
      navigate(`/a/${ownerId}/day#nudge`, { viewTransition: true });
    }
  }, [nudgeTitle, ownerId, navigate]);

  // Arriving with #nudge: wait (briefly) for the day to load, then bring the card up.
  useEffect(() => {
    if (hash !== '#nudge') return;
    let tries = 0;
    let timer: ReturnType<typeof setTimeout>;
    const attempt = () => {
      const card = findNudgeCard(nudgeTitle);
      if (card) revealNudgeCard(card);
      else if (++tries < 30) timer = setTimeout(attempt, 100);
    };
    timer = setTimeout(attempt, 50);
    return () => clearTimeout(timer);
  }, [hash, pathname, nudgeTitle]);

  let fab: FabAction | null = null;
  if (ownerId) fab = { kind: 'bell', label: nudgeTitle, onClick: openNudge };
  else if (onboarded) {
    fab = {
      kind: 'add',
      to: addFoodPath(),
      label: t('me.add'),
      // The meal is guessed at the moment of the tap, not when the bar last rendered.
      onClick: (event: MouseEvent<HTMLAnchorElement>) => {
        if (isModifiedClick(event)) return;
        event.preventDefault();
        setNavDirection('forward');
        navigate(addFoodPath(), { viewTransition: true });
      },
    };
  }

  // The part after " · " is secondary ("My day · 870 left").
  const split = compact ? compact.indexOf(' · ') : -1;
  const pageTitle = compact ? (split > 0 ? compact.slice(0, split) : compact) : null;

  // The tab title names the page, so history and screen readers can tell pages apart.
  useEffect(() => {
    const app = t('app.name');
    document.title = pageTitle && pageTitle !== app ? `${pageTitle} – ${app}` : app;
  }, [pageTitle, t]);

  return (
    <ToastProvider>
      <CompactTitleContext.Provider value={setCompact}>
        <div className={first ? 'app first' : 'app revisit'}>
          <svg className="defs" aria-hidden="true" focusable="false">
            <defs>
              <linearGradient id="g-ember-icon" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" className="s-e1" />
                <stop offset=".55" className="s-e2" />
                <stop offset="1" className="s-e3" />
              </linearGradient>
              <linearGradient id="g-ember-v" x1="0" y1="1" x2="0" y2="0">
                <stop offset="0" className="s-e2" />
                <stop offset="1" className="s-e1" />
              </linearGradient>
              <linearGradient id="g-water" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" className="s-w1" />
                <stop offset="1" className="s-w2" />
              </linearGradient>
              <linearGradient id="g-weight" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" className="s-w2" />
                <stop offset="1" className="s-wt" />
              </linearGradient>
              <linearGradient id="g-weight-area" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" className="s-wt-a" />
                <stop offset="1" className="s-wt-z" />
              </linearGradient>
              <pattern id="p-today" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="7" height="7" className="f-e2-soft" />
                <rect width="3.5" height="7" className="f-e2" />
              </pattern>
              <pattern id="p-over" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="5" height="5" className="f-surface" />
                <rect width="2.2" height="5" className="f-over" />
              </pattern>
            </defs>
          </svg>

          {desktop && <Sidebar items={items} activeOwnerId={ownerId} />}

          <div className="stage" ref={stage}>
            <div className="top-sentinel" ref={sentinel} aria-hidden="true" />
            <main className="page">
              <Outlet />
            </main>
          </div>

          {!desktop && compact && (
            <div className="topbar" aria-hidden="true">
              <b>
                {split > 0 ? (
                  <>
                    {compact.slice(0, split)}
                    <span>{compact.slice(split)}</span>
                  </>
                ) : (
                  compact
                )}
              </b>
            </div>
          )}
          {!desktop && <div className="tab-fade" aria-hidden="true" />}
          {!desktop && <TabBar items={items} fab={fab} intro={intro} />}
        </div>
        <ScrollRestoration />
      </CompactTitleContext.Provider>
    </ToastProvider>
  );
}
