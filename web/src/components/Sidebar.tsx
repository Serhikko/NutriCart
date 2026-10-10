import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useMyProfile } from '../lib/queries';
import { useReducedMotion } from '../lib/motion';
import { Icon } from './ui/Icon';
import { FollowingList, Initial } from './FollowingList';
import { onNavClick, useSlidingPill, type NavItem } from './TabBar';

/** Who is signed in, at the foot of the sidebar; a link to Settings. */
function AccountFoot() {
  const { t } = useI18n();
  const { userId, email } = useSession();
  const profile = useMyProfile(userId);
  const name = profile.data?.trim() ?? '';
  const title = name || email || t('settings.account');
  const sub = email && name ? email : email ? '' : t('nav.account_anon');
  return (
    <Link className="side-foot" to="/settings" viewTransition>
      <Initial name={name || email || ''} />
      <span className="who">
        <b>{title}</b>
        {sub && <span>{sub}</span>}
      </span>
    </Link>
  );
}

/**
 * The desktop sidebar (>= 900 px): brand, the sections with a sliding pill,
 * the accounts this user follows, and the account at the foot. Solid, never
 * glass, and excluded from the page cross-fade.
 */
export function Sidebar({ items, activeOwnerId }: { items: NavItem[]; activeOwnerId?: string }) {
  const { t } = useI18n();
  const { userId } = useSession();
  const reduced = useReducedMotion();
  const nav = useRef<HTMLElement>(null);
  const pill = useRef<HTMLSpanElement>(null);
  const activeKey = items.find((i) => i.active)?.key ?? null;
  useSlidingPill(nav, pill, activeKey, 'y', items.map((i) => i.key).join(','));

  return (
    <div className="sidebar">
      <div className="brand">
        <img src="/icon.svg" alt="" width={30} height={30} />
        {t('app.name')}
      </div>
      <nav className="side-nav" ref={nav} aria-label={t('nav.sections')}>
        <span className="side-pill" ref={pill} aria-hidden="true" />
        {items.map((item) => (
          <Link
            key={item.key}
            to={item.to}
            className="side-link"
            aria-current={item.active ? 'page' : undefined}
            viewTransition
            onClick={onNavClick(item, reduced)}
          >
            <Icon name={item.icon} />
            <span>{item.label}</span>
          </Link>
        ))}
      </nav>
      <FollowingList userId={userId} activeOwnerId={activeOwnerId} />
      <AccountFoot />
    </div>
  );
}
