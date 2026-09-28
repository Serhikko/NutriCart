import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useProfileDetails } from '../lib/tracker';

/**
 * The bottom tabs. On a followed account (/a/<owner>) they point at that
 * account's Day and Week; otherwise at the user's own, once onboarded.
 */
export function Layout() {
  const { t } = useI18n();
  const { ownerId } = useParams();
  const { userId } = useSession();
  const details = useProfileDetails(userId);
  const base = ownerId ? `/a/${ownerId}` : details.data ? '/me' : null;
  const cls = ({ isActive }: { isActive: boolean }) => (isActive ? 'active' : '');
  return (
    <div className="app">
      <Outlet />
      <nav className="tabs" aria-label="Sections">
        <NavLink to="/" end className={cls}>{t('nav.home')}</NavLink>
        {base && <NavLink to={`${base}/day`} className={cls}>{t('nav.day')}</NavLink>}
        {base && <NavLink to={`${base}/week`} className={cls}>{t('nav.week')}</NavLink>}
        <NavLink to="/settings" className={cls}>{t('nav.settings')}</NavLink>
      </nav>
    </div>
  );
}
