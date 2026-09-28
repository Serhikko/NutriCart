import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';

/** The three tabs, like the phone's bottom bar, scoped to one followed account. */
export function Layout() {
  const { t } = useI18n();
  const { ownerId } = useParams();
  const base = ownerId ? `/a/${ownerId}` : '';
  return (
    <div className="app">
      <Outlet />
      <nav className="tabs" aria-label="Sections">
        <NavLink to={`${base}/day`} className={({ isActive }) => (isActive ? 'active' : '')}>
          {t('nav.day')}
        </NavLink>
        <NavLink to={`${base}/week`} className={({ isActive }) => (isActive ? 'active' : '')}>
          {t('nav.week')}
        </NavLink>
        <NavLink to="/settings" className={({ isActive }) => (isActive ? 'active' : '')}>
          {t('nav.settings')}
        </NavLink>
      </nav>
    </div>
  );
}
