import { useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useFollowed, useOwnerRealtime, useWeek } from '../lib/queries';
import { lastDays, todayEpochDay } from '../lib/dates';
import { buildWeekRows, weekRange, weekStats } from '../lib/weekView';
import { LargeTitle } from '../components/ui/LargeTitle';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { ButtonLink } from '../components/ui/Button';
import { StreakChip, WeekOverview } from '../components/charts/WeekOverview';

/**
 * A followed account's last seven days, read-only: their daily average and
 * day markers, calories against their published targets, and their weight
 * trend. Their name is the eyebrow; the numbers update live.
 */
export function Week() {
  const { ownerId = '' } = useParams();
  const { t, tag } = useI18n();
  const { userId } = useSession();
  const followed = useFollowed(userId);
  const today = todayEpochDay();
  const week = useWeek(ownerId, today);
  useOwnerRealtime(ownerId);

  const ownerName = followed.data?.find((a) => a.ownerId === ownerId)?.ownerName;
  const days = lastDays(7, today);
  const range = weekRange(days[0], days[days.length - 1], tag);
  // Their phone publishes the summaries, so those win over the raw diary rows.
  const rows = week.data ? buildWeekRows(week.data, today, { preferSummary: true }) : null;
  const stats = rows && !week.isLoading ? weekStats(rows, today) : null;

  return (
    <>
      <LargeTitle
        eyebrow={ownerName ? `${ownerName} · ${range}` : range}
        title={t('week.title')}
        actions={stats && stats.streak > 0 ? <StreakChip days={stats.streak} /> : null}
      />
      <OfflineBanner />
      <WeekOverview
        loading={week.isLoading}
        failed={week.isError}
        onRetry={() => void week.refetch()}
        rows={rows}
        weights={week.data?.weights ?? []}
        today={today}
        emptyAction={
          <ButtonLink to={`/a/${ownerId}/day`} viewTransition>
            {t('day.title', { name: ownerName ?? 'NutriCart' })}
          </ButtonLink>
        }
      />
    </>
  );
}
