import { useRef, useState, type CSSProperties } from 'react';
import { useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useDay, useFollowed, useOwnerRealtime } from '../lib/queries';
import { todayEpochDay } from '../lib/dates';
import { MEAL_SLOTS, groupByMeal, sumKcal, sumWater } from '../lib/diary';
import { longDate, shortDate } from '../lib/dayView';
import { DESKTOP_QUERY, useMediaQuery, useRouteFirstOpen } from '../lib/motion';
import { LargeTitle } from '../components/ui/LargeTitle';
import { DayNav } from '../components/ui/DayNav';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { Notice } from '../components/ui/Notice';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { Skeleton } from '../components/ui/Skeleton';
import { DayHero } from '../components/DayHero';
import { ActivityTiles } from '../components/ActivityTiles';
import { MealCard, useFreshEntries } from '../components/MealCard';
import { NudgeBox } from '../components/NudgeBox';
import { WarmDay } from './MyDay';

/**
 * One day of a followed account, read only: the date and a Live chip, the
 * day's ring from what their phone published, water, steps and active
 * energy, their meals, and the nudge card. When a meal arrives live the ring
 * re-sweeps from the old value, the numbers hand off and the new row unfolds
 * (the moment back from Add, without the toast).
 */
export function Day() {
  const { ownerId = '' } = useParams();
  const { t, tag } = useI18n();
  const session = useSession();
  const { userId } = session;
  // Polled like the day itself, so an open page notices within a minute when the owner removed you.
  const followed = useFollowed(userId, 60_000);
  const [openedAt] = useState(() => Date.now());
  const first = useRouteFirstOpen();
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const today = todayEpochDay();
  const [epochDay, setEpochDay] = useState(today);
  const day = useDay(ownerId, epochDay);
  // The day before is loaded once (see WarmDay), so the previous-day button lands at once.
  const [warmed, setWarmed] = useState<number | null>(null);
  useOwnerRealtime(ownerId);

  // Their name comes with the list of people you follow; until it arrives (a reload, a shared
  // link) the title is a placeholder rather than a wrong name.
  const owner = followed.data?.find((a) => a.ownerId === ownerId);
  const naming = followed.data === undefined && !followed.isError && (session.loading || userId != null);
  const title = t('day.title', { name: owner?.ownerName ?? 'NutriCart' });
  // Not (or no longer) in that list: the owner removed you, you unfollowed elsewhere, or the link
  // is someone else's. RLS then hides every row, which would read as an empty day. Only a list
  // fetched since the page opened says so: the cached one may predate a code you just redeemed
  // (its refetch failed, or another tab followed them), and a failed poll keeps the last answer.
  const notFollowing = followed.data !== undefined && followed.dataUpdatedAt >= openedAt && !owner;

  // A day that is still loading keeps the last one on screen (dimmed), so the ring moves from
  // it. A day that failed to load does not: it gets the error and its Retry.
  const lastShown = useRef<typeof day.data>(undefined);
  if (day.data) lastShown.current = day.data;
  const data = day.data ?? (day.isError ? undefined : lastShown.current);
  const stale = !day.data && data != null;

  const live = data ? data.entries.filter((e) => e.deleted_at === null) : [];
  const groups = groupByMeal(live);
  const eatenFallback = sumKcal(live);
  const summary = data?.summary ?? null;
  const [fresh] = useFreshEntries(data && !stale ? live : null, epochDay);
  const isToday = epochDay === today;

  // The Live chip pings once whenever their eaten total moves while you watch (P1).
  const eaten = summary ? summary.eaten_kcal : Math.round(eatenFallback);
  const pings = useRef({ day: epochDay, eaten, n: 0 });
  if (data && !stale) {
    if (pings.current.day !== epochDay) pings.current = { day: epochDay, eaten, n: 0 };
    else if (pings.current.eaten !== eaten) pings.current = { day: epochDay, eaten, n: pings.current.n + 1 };
  }

  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const remaining = summary ? summary.target_kcal - summary.eaten_kcal : null;
  const compact = naming
    ? null
    : remaining === null
      ? title
      : `${title} · ${remaining >= 0 ? t('day.compact_left', { n: nf.format(remaining) }) : t('day.compact_over', { n: nf.format(-remaining) })}`;

  const header = (
    <LargeTitle
      eyebrow={
        <>
          {longDate(epochDay, tag)}
          {isToday && !notFollowing && (
            <span key={pings.current.n} className={pings.current.n > 0 ? 'live ping' : 'live'}>
              {t('day.live')}
            </span>
          )}
        </>
      }
      title={naming ? <Skeleton variant="line" width="5.6em" height=".8em" className="title-skel" label={t('loading')} /> : title}
      compact={compact}
      actions={
        notFollowing ? undefined : (
          <DayNav
            label={isToday ? t('day.today') : shortDate(epochDay, tag)}
            onPrev={() => setEpochDay((d) => d - 1)}
            onNext={() => setEpochDay((d) => Math.min(today, d + 1))}
            nextDisabled={epochDay >= today}
          />
        )
      }
    />
  );

  const nudge = notFollowing ? null : <NudgeBox ownerId={ownerId} userId={userId} />;

  let body;
  if (notFollowing) {
    body = (
      <div className="card day-nothing a-rise">
        <EmptyState icon="lock" title={t('day.not_following')} titleAs="p" />
      </div>
    );
  } else if (!data && day.isError) {
    body = (
      <Notice
        tone="error"
        title={t('error.generic')}
        action={
          <Button variant="fill" size="sm" type="button" icon="refresh" onClick={() => day.refetch()}>
            {t('error.retry')}
          </Button>
        }
      />
    );
  } else if (!data) {
    body = (
      <div className="hero day-skeleton">
        <div className="ring-wrap">
          <Skeleton variant="ring" label={t('loading')} />
        </div>
        <div className="hero-side">
          <div className="kpis">
            <Skeleton variant="line" width="60%" height={14} />
            <Skeleton variant="line" width="40%" height={14} />
          </div>
        </div>
      </div>
    );
  } else {
    body = (
      <>
        <DayHero summary={summary} eatenFallback={eatenFallback} epochDay={epochDay} first={first}>
          <ActivityTiles water={sumWater(data.water)} steps={summary?.steps ?? null} active={summary?.active_kcal ?? null} />
        </DayHero>
        <div className="meals-col">
          <div className="section-head a-fade-up" style={{ '--d': 400 } as CSSProperties}>
            <h2 className="title-2">{t('me.meals')}</h2>
          </div>
          {live.length === 0 ? (
            <div className="card day-nothing a-rise">
              <EmptyState icon="bowl" title={t('day.nothing')} titleAs="p" />
            </div>
          ) : (
            <div className="stack meals">
              {MEAL_SLOTS.map((slot, i) => (
                <MealCard key={slot} slot={slot} index={i} entries={groups.get(slot) ?? []} fresh={fresh} />
              ))}
            </div>
          )}
        </div>
      </>
    );
  }

  return (
    <>
      {header}
      <OfflineBanner />
      {/* Only the day waits while another loads: the nudge card stays usable beside it. */}
      <div className="today-grid partner">
        <div className={stale ? 'today-main stale' : 'today-main'} inert={stale} aria-busy={stale || (!data && !day.isError) || undefined}>
          {body}
        </div>
        <div className="today-side">{desktop && nudge}</div>
      </div>
      {!desktop && nudge}
      {warmed !== epochDay - 1 && !notFollowing && <WarmDay ownerId={ownerId} epochDay={epochDay - 1} onDone={setWarmed} />}
    </>
  );
}
