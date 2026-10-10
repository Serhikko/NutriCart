import { Link } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useDay, useFollowed, type FollowedAccount } from '../lib/queries';
import { todayEpochDay } from '../lib/dates';
import { dayLine, sumKcal } from '../lib/diary';
import { setNavDirection } from '../lib/motion';
import { Icon } from './ui/Icon';

// Something logged within this long counts as "Live": the person is eating or logging right now.
const LIVE_WINDOW_MS = 60 * 60 * 1000;

/** The first letter of a name for its avatar; a person glyph when there is none. */
export function Initial({ name }: { name: string }) {
  const letter = name.trim().charAt(0).toLocaleUpperCase();
  return (
    <span className="avatar" aria-hidden="true">
      {letter || <Icon name="person" />}
    </span>
  );
}

/**
 * One followed account with today at a glance: a Live chip when something
 * was logged in the last hour, otherwise today's kcal. The whole row is the
 * link to their day.
 */
function FollowingRow({ account, active, variant }: { account: FollowedAccount; active: boolean; variant: 'sidebar' | 'list' }) {
  const { t, tag } = useI18n();
  const day = useDay(account.ownerId, todayEpochDay());
  const entries = day.data?.entries ?? [];
  const live = entries.some((e) => e.deleted_at === null && Date.now() - new Date(e.logged_at).getTime() < LIVE_WINDOW_MS);
  const eaten = day.data ? dayLine(day.data.summary, sumKcal(entries)).eaten : null;
  return (
    <li>
      <Link
        className="person"
        to={`/a/${account.ownerId}/day`}
        aria-current={active ? 'true' : undefined}
        viewTransition
        onClick={() => setNavDirection('forward')}
      >
        <Initial name={account.ownerName} />
        <span className="pname">{account.ownerName}</span>
        {live ? (
          <span className="live">{t('day.live')}</span>
        ) : eaten !== null ? (
          <span className="pmeta num">
            {new Intl.NumberFormat(tag).format(eaten)} {t('unit.kcal')}
          </span>
        ) : null}
        {variant === 'list' && (
          <span className="open">
            {t('welcome.open')}
            <Icon name="right" size="xs" />
          </span>
        )}
      </Link>
    </li>
  );
}

interface FollowingListProps {
  userId: string | null;
  /** sidebar: compact rows under a "Following" heading; list: an inset card of rows with "Open ›" (Welcome). */
  variant?: 'sidebar' | 'list';
  /** The account whose pages are open, marked as current. */
  activeOwnerId?: string;
}

/** The accounts this user follows, each a link to their day. Renders nothing until there is one. */
export function FollowingList({ userId, variant = 'sidebar', activeOwnerId }: FollowingListProps) {
  const { t } = useI18n();
  const followed = useFollowed(userId);
  const accounts = followed.data ?? [];
  if (accounts.length === 0) return null;
  const rows = accounts.map((a) => <FollowingRow key={a.linkId} account={a} active={a.ownerId === activeOwnerId} variant={variant} />);
  if (variant === 'list') return <ul className="follow-list card">{rows}</ul>;
  return (
    <nav className="side-following" aria-labelledby="side-following">
      <h2 className="side-sec" id="side-following">
        {t('nav.following')}
      </h2>
      <ul className="side-list">{rows}</ul>
    </nav>
  );
}
