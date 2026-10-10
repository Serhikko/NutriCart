import { useI18n } from '../lib/i18n';
import { useOwnerRealtime, useWeek } from '../lib/queries';
import { useMyTargets } from '../lib/myTargets';
import { lastDays, todayEpochDay } from '../lib/dates';
import { buildWeekRows, weekRange, weekStats } from '../lib/weekView';
import { LargeTitle } from '../components/ui/LargeTitle';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { ButtonLink } from '../components/ui/Button';
import { StreakChip, WeekOverview } from '../components/charts/WeekOverview';

/**
 * Seven days of the signed-in user's own numbers, targets from the same math
 * as the phone: the daily average, a marker per day, calories against target
 * and the weight trend, with the streak on the title row.
 */
export function MyWeek() {
  const { t, tag } = useI18n();
  const today = todayEpochDay();
  const me = useMyTargets(today);
  const week = useWeek(me.userId ?? '', today);
  useOwnerRealtime(me.userId);

  const days = lastDays(7, today);
  const loading = me.loading || week.isLoading;
  // A day without a published summary is judged against today's target from the profile.
  const rows = week.data ? buildWeekRows(week.data, today, { fallbackTarget: me.targets?.kcal ?? null }) : null;
  const stats = rows && !loading ? weekStats(rows, today) : null;

  return (
    <>
      <LargeTitle
        eyebrow={weekRange(days[0], days[days.length - 1], tag)}
        title={t('me.week')}
        actions={stats && stats.anyLogged ? <StreakChip days={stats.streak} /> : null}
      />
      <OfflineBanner />
      <WeekOverview
        loading={loading}
        failed={week.isError}
        onRetry={() => void week.refetch()}
        rows={rows}
        weights={week.data?.weights ?? []}
        today={today}
        emptyAction={
          <ButtonLink to="/me/day" viewTransition>
            {t('welcome.track_open')}
          </ButtonLink>
        }
      />
    </>
  );
}
