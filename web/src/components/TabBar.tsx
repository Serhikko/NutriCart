import { useLayoutEffect, useRef, type MouseEvent, type RefObject } from 'react';
import { Link } from 'react-router-dom';
import { Icon, type IconName } from './ui/Icon';
import { canAnimate, setNavDirection, useReducedMotion } from '../lib/motion';
import { useI18n } from '../lib/i18n';

/** One section of the site, as the tab bar and the sidebar show it. */
export interface NavItem {
  key: string;
  to: string;
  label: string;
  icon: IconName;
  active: boolean;
}

/** The round button beside the tabs: + (add food, the meal guessed from the hour) or, on a partner's pages, a bell. */
export type FabAction =
  | { kind: 'add'; to: string; label: string; onClick: (event: MouseEvent<HTMLAnchorElement>) => void }
  | { kind: 'bell'; label: string; onClick: () => void };

/**
 * Keeps a pill under the active item of `container` (the element with
 * aria-current="page"), and moves it with a squash-and-stretch when the
 * active item changes (G6 horizontally, G7 vertically in the sidebar).
 * Positions go in as custom properties (--pos, --size); the move is a Web
 * Animation, skipped under reduced motion and where WAAPI is missing.
 * Until it has measured, the container has no data-measured and the CSS
 * paints the active item itself (also the case in tests, which have no layout).
 */
export function useSlidingPill(
  container: RefObject<HTMLElement | null>,
  pill: RefObject<HTMLElement | null>,
  activeKey: string | null,
  axis: 'x' | 'y',
  /** Changes when the items change (Day and Week appear once a profile exists): re-place without a slide. */
  layoutKey = '',
) {
  const reduced = useReducedMotion();
  const last = useRef<{ key: string | null; pos: number; size: number } | null>(null);

  useLayoutEffect(() => {
    const box = container.current;
    const el = pill.current;
    if (!box || !el) return;

    const place = (animate: boolean) => {
      const active = box.querySelector<HTMLElement>('[aria-current="page"]');
      if (!active) {
        delete box.dataset.measured;
        last.current = null;
        return;
      }
      const pos = axis === 'x' ? active.offsetLeft : active.offsetTop;
      const size = axis === 'x' ? active.offsetWidth : active.offsetHeight;
      if (!size) return;
      el.style.setProperty('--pos', `${pos}px`);
      el.style.setProperty('--size', `${size}px`);
      box.dataset.measured = '';
      const from = last.current;
      last.current = { key: activeKey, pos, size };
      if (!animate || !from || from.key === activeKey || reduced || !canAnimate()) return;
      if (from.pos === pos && from.size === size) return;
      // Stretch toward the target at 40% of the run, slightly squashed across, then settle.
      const forward = pos > from.pos;
      const travel = Math.abs(pos - from.pos);
      const midPos = forward ? from.pos + travel * 0.45 : pos + travel * 0.2;
      const midSize = travel * 0.35 + Math.max(from.size, size);
      const move = axis === 'x' ? 'translateX' : 'translateY';
      const squash = axis === 'x' ? ' scaleY(.92)' : ' scaleX(.97)';
      const dim = axis === 'x' ? 'width' : 'height';
      el.animate(
        [
          { transform: `${move}(${from.pos}px)`, [dim]: `${from.size}px` },
          { transform: `${move}(${midPos}px)${squash}`, [dim]: `${midSize}px`, offset: 0.4 },
          { transform: `${move}(${pos}px)`, [dim]: `${size}px` },
        ],
        { duration: 640, easing: 'cubic-bezier(.3, 1.35, .5, 1)' },
      );
    };

    place(true);
    // Fonts arriving, a rotated phone or a longer translation move the items: follow without animating.
    const replace = () => place(false);
    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(replace);
      observer.observe(box);
    } else {
      window.addEventListener('resize', replace);
    }
    document.fonts?.ready.then(replace, () => undefined);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', replace);
    };
  }, [container, pill, activeKey, axis, reduced, layoutKey]);
}

/** A click the browser should handle itself (open in a new tab or window). */
export const isModifiedClick = (event: MouseEvent) => event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey;

/**
 * Tapping the tab of the page you are on scrolls back to the top instead of
 * reloading it. (Day is also lit on the add-food page; there it goes back to the day.)
 */
export function onNavClick(item: NavItem, reduced: boolean) {
  return (event: MouseEvent<HTMLAnchorElement>) => {
    if (isModifiedClick(event)) return;
    if (item.active && window.location.pathname === item.to) {
      event.preventDefault();
      window.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
      return;
    }
    // Lit but elsewhere (the add-food page under Day): going up a level slides back.
    setNavDirection(item.active ? 'back' : null);
  };
}

/**
 * The floating capsule tab bar (phone and tablet) with its separate round +
 * button. Glass is on the capsule and the button themselves, never on a
 * wrapper, and both are excluded from the page cross-fade (view-transition
 * names in CSS), so the chrome never blinks between pages.
 */
export function TabBar({ items, fab, intro }: { items: NavItem[]; fab: FabAction | null; intro: boolean }) {
  const { t } = useI18n();
  const reduced = useReducedMotion();
  const tabs = useRef<HTMLDivElement>(null);
  const pill = useRef<HTMLSpanElement>(null);
  const activeKey = items.find((i) => i.active)?.key ?? null;
  useSlidingPill(tabs, pill, activeKey, 'x', items.map((i) => i.key).join(','));

  return (
    <nav className={intro ? 'tabbar intro' : 'tabbar'} aria-label={t('nav.sections')}>
      <div className="tabs" ref={tabs}>
        <span className="tab-pill" ref={pill} aria-hidden="true" />
        {items.map((item) => (
          <Link
            key={item.key}
            to={item.to}
            className="tab"
            aria-current={item.active ? 'page' : undefined}
            viewTransition
            onClick={onNavClick(item, reduced)}
          >
            <Icon name={item.icon} />
            <span>{item.label}</span>
          </Link>
        ))}
      </div>
      {fab?.kind === 'add' && (
        <Link className="fab" to={fab.to} aria-label={fab.label} viewTransition onClick={fab.onClick}>
          <Icon name="plus" />
        </Link>
      )}
      {fab?.kind === 'bell' && (
        <button type="button" className="fab bell" aria-label={fab.label} onClick={fab.onClick}>
          <Icon name="bell" />
        </button>
      )}
    </nav>
  );
}
