import { useId, useMemo, type CSSProperties, type ReactNode } from 'react';
import { useI18n } from '../../lib/i18n';
import { useRouteFirstOpen } from '../../lib/motion';
import type { WeightEntry } from '../../lib/diary';
import { longWeekday, markerState, shortWeekday, weekStats, weightSeries, type WeekDay } from '../../lib/weekView';
import { Button } from '../ui/Button';
import { DayMarker } from '../ui/DayMarker';
import { Digits } from '../ui/Digits';
import { EmptyState } from '../ui/EmptyState';
import { Icon } from '../ui/Icon';
import { Notice } from '../ui/Notice';
import { Skeleton } from '../ui/Skeleton';
import { CaloriesChart } from './CaloriesChart';
import { WeightChart } from './WeightChart';

/**
 * The body of a Week page, shared by My week and a partner's week (DESIGN.md
 * §6.5, §6.8): the daily-average hero with its strip of day markers, then the
 * calories chart and the weight chart. From 1024 px the hero and weight sit
 * in a narrow left column and the calories chart fills the right one.
 *
 * Also every state the page can be in: loading (skeletons at the final
 * geometry), failed (a notice with Try again), and nothing logged at all (an
 * empty state with seven ghost days and one way forward).
 */

const delay = (ms: number) => ({ '--d': ms }) as CSSProperties;

interface WeekOverviewProps {
  loading: boolean;
  failed: boolean;
  onRetry: () => void;
  /** One row per day, oldest first (null until the week has loaded). */
  rows: WeekDay[] | null;
  weights: WeightEntry[];
  today: number;
  /** The one way forward from the empty state (a link to the day). */
  emptyAction: ReactNode;
}

export function WeekOverview({ loading, failed, onRetry, rows, weights, today, emptyAction }: WeekOverviewProps) {
  const { t } = useI18n();
  const first = useRouteFirstOpen();

  // No rows yet and no failure means the query has not answered (or not started): still loading.
  if (loading || (!failed && !rows)) return <WeekSkeleton />;
  if (failed || !rows) {
    return (
      <Notice
        tone="error"
        title={t('error.generic')}
        action={
          <Button variant="fill" size="sm" icon="refresh" type="button" onClick={onRetry}>
            {t('error.retry')}
          </Button>
        }
      />
    );
  }

  const stats = weekStats(rows, today);
  const series = weightSeries(weights, today);
  if (!stats.anyLogged && series.points.length === 0) {
    return (
      <EmptyState
        className="week-empty a-rise"
        art={
          <span className="ghost-week">
            {rows.map((r) => (
              <i key={r.epochDay} />
            ))}
          </span>
        }
        title={t('week.no_data')}
        action={emptyAction}
      />
    );
  }

  return (
    <div className="week-grid">
      <WeekHero rows={rows} stats={stats} first={first} />
      <CaloriesChart rows={rows} goal={stats.goal} className="w-cal a-rise" style={delay(300)} />
      {series.points.length > 0 && <WeightChart series={series} ping={first} className="w-wt a-rise" style={delay(380)} />}
    </div>
  );
}

/** The streak chip on the title row ("12-day streak"), with the Ember flame. */
export function StreakChip({ days }: { days: number }) {
  const { t } = useI18n();
  return (
    <span className="streak">
      <Icon name="flame" size="xs" />
      {t('me.streak', { days })}
    </span>
  );
}

function WeekHero({ rows, stats, first }: { rows: WeekDay[]; stats: ReturnType<typeof weekStats>; first: boolean }) {
  const { t, tp, tag } = useI18n();
  const nf = useMemo(() => new Intl.NumberFormat(tag, { maximumFractionDigits: 0 }), [tag]);
  const headId = `wk-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const unit = t('unit.kcal');

  return (
    <section className="week-hero w-hero" aria-labelledby={headId}>
      <div className="stage-light a-bloom" style={delay(160)} aria-hidden="true" />
      <h2 className="hero-l a-fade-up" id={headId} style={delay(120)}>
        {t('week.average')}
      </h2>
      <p className="week-num">
        {stats.average != null ? (
          <Digits value={stats.average} gradient enter={first} delay={170} step={60} />
        ) : (
          <span className="grad-dash">—</span>
        )}
        {/* Arrives with the digits, so a first open never shows a lone unit. */}
        <span className="unit a-fade-up" style={delay(170)}>{unit}</span>
      </p>
      <div className="days">
        {rows.map((r, i) => {
          const state = markerState(r);
          const day = r.isToday ? t('day.today') : longWeekday(r.epochDay, tag);
          const kcal = nf.format(r.eaten);
          let name: string;
          if (state === 'TODAY') name = t('week.marker_today', { day, kcal });
          else if (state === 'GOOD') name = t('week.marker_good', { day, kcal });
          else if (state === 'OVER') name = t('week.marker_over', { day, kcal });
          // Logged, but there is no goal to judge it against (a partner day without a summary).
          else if (r.eaten > 0) name = `${day}: ${kcal} ${unit}`;
          else name = t('week.marker_empty', { day });
          return (
            <DayMarker
              key={r.epochDay}
              state={state}
              progress={r.target ? r.eaten / r.target : 0}
              label={r.isToday ? t('day.today') : shortWeekday(r.epochDay, tag)}
              name={name}
              delay={260 + i * 50}
              sweep={first}
            />
          );
        })}
      </div>
      {stats.goal != null && (
        <p className="week-cap a-fade-up" style={delay(620)}>
          {/* Counted by the days with a verdict: "0 of 1 day" on the first day, "з 1 дня" in Ukrainian.
              `logged` keeps the uncounted template working where a locale has no plural forms. */}
          <b>{tp('week.days_on_plan', stats.logged, { good: stats.good, logged: stats.logged })}</b>
          {/* The dot stays with the phrase before it, so a second line never starts with it. */}
          {'\u00a0· '}
          {t('week.goal', { target: nf.format(stats.goal) })}
        </p>
      )}
    </section>
  );
}

/** The page at its final geometry while the week loads: the hero's lines and seven discs, then the two cards. */
function WeekSkeleton() {
  const { t } = useI18n();
  return (
    <div className="week-grid week-loading">
      <div className="week-hero w-hero">
        <Skeleton variant="line" width={120} height={15} label={t('loading')} />
        <Skeleton variant="line" width={210} height={64} className="sk-num" />
        <div className="days">
          {Array.from({ length: 7 }, (_, i) => (
            <span className="dm" key={i}>
              <Skeleton variant="block" width={36} height={36} className="sk-disc" />
              <Skeleton variant="line" width={26} height={10} />
            </span>
          ))}
        </div>
      </div>
      <Skeleton variant="block" className="w-cal sk-cal" />
      <Skeleton variant="block" className="w-wt sk-wt" />
    </div>
  );
}
