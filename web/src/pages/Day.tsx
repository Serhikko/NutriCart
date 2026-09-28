import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useDay, useFollowed, useOwnerRealtime } from '../lib/queries';
import { formatDay, todayEpochDay } from '../lib/dates';
import { MEAL_SLOTS, groupByMeal, sumKcal, sumWater } from '../lib/diary';
import { MealCard } from '../components/MealCard';
import { DayLine } from '../components/DayLine';
import { NudgeBox } from '../components/NudgeBox';

/** One day of the followed account: meals, water, the day line, activity, and the nudge box. */
export function Day() {
  const { ownerId = '' } = useParams();
  const { t, tag } = useI18n();
  const { userId } = useSession();
  const followed = useFollowed(userId);
  const today = todayEpochDay();
  const [epochDay, setEpochDay] = useState(today);
  const day = useDay(ownerId, epochDay);
  useOwnerRealtime(ownerId);

  const ownerName = followed.data?.find((a) => a.ownerId === ownerId)?.ownerName ?? 'NutriCart';
  const groups = day.data ? groupByMeal(day.data.entries) : null;
  const anyMeal = day.data ? day.data.entries.some((e) => e.deleted_at === null) : false;

  return (
    <>
      <h1>{t('day.title', { name: ownerName })}</h1>
      <div className="daynav">
        <button className="ghost" onClick={() => setEpochDay((d) => d - 1)} aria-label="previous day">
          ‹
        </button>
        <strong>{epochDay === today ? t('day.today') : formatDay(epochDay, tag)}</strong>
        <button className="ghost" onClick={() => setEpochDay((d) => Math.min(today, d + 1))} disabled={epochDay >= today} aria-label="next day">
          ›
        </button>
      </div>

      {day.isLoading && <p className="muted">{t('loading')}</p>}
      {day.isError && <p className="error">{t('error.generic')}</p>}

      {day.data && (
        <>
          <section className="card">
            <div className="row">
              <DayLine summary={day.data.summary} eatenFallback={sumKcal(day.data.entries)} />
              {epochDay === today && <span className="badge">{t('day.live')}</span>}
            </div>
            <div className="grid" style={{ marginTop: 12 }}>
              <div className="stat">
                <span className="muted">{t('day.water')}</span>
                <strong>
                  {sumWater(day.data.water)} {t('unit.ml')}
                </strong>
              </div>
              <div className="stat">
                <span className="muted">{t('day.steps')}</span>
                <strong>{day.data.summary?.steps ?? '—'}</strong>
              </div>
              <div className="stat">
                <span className="muted">{t('day.active')}</span>
                <strong>
                  {day.data.summary?.active_kcal != null ? `${Math.round(day.data.summary.active_kcal)} ${t('unit.kcal')}` : '—'}
                </strong>
              </div>
            </div>
          </section>

          {!anyMeal && <p className="muted">{t('day.nothing')}</p>}
          {groups && MEAL_SLOTS.map((slot) => <MealCard key={slot} slot={slot} entries={groups.get(slot) ?? []} />)}
        </>
      )}

      <NudgeBox ownerId={ownerId} userId={userId} />
    </>
  );
}
