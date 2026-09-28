import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useDay, useOwnerRealtime } from '../lib/queries';
import { useMyTargets } from '../lib/myTargets';
import { useAddWater, useDeleteFood, useLogWeight, useUndoWater } from '../lib/tracker';
import { formatDay, formatTime, todayEpochDay } from '../lib/dates';
import { MEAL_SLOTS, groupByMeal, sumKcal, sumWater } from '../lib/diary';
import { DayLine } from '../components/DayLine';

/** The signed-in user's own diary: the phone's Diary + dashboard line, on the web. */
export function MyDay() {
  const { t, tag } = useI18n();
  const today = todayEpochDay();
  const [epochDay, setEpochDay] = useState(today);
  const me = useMyTargets(epochDay);
  const userId = me.userId ?? '';
  const day = useDay(userId, epochDay);
  useOwnerRealtime(me.userId);
  const addWater = useAddWater(me.userId);
  const undoWater = useUndoWater(me.userId);
  const deleteFood = useDeleteFood(me.userId);
  const logWeight = useLogWeight(me.userId);
  const [weightText, setWeightText] = useState('');
  const targetKcal = me.targets?.kcal ?? null;

  if (me.loading) return <p className="muted">{t('loading')}</p>;
  if (!me.onboarded) {
    return (
      <>
        <h1>{t('me.title')}</h1>
        <p className="muted">{t('me.no_profile')}</p>
        <Link to="/me/onboarding"><button>{t('welcome.track_start')}</button></Link>
      </>
    );
  }

  const groups = day.data ? groupByMeal(day.data.entries) : null;
  const eaten = day.data ? sumKcal(day.data.entries) : 0;
  const summary = targetKcal !== null ? { epoch_day: epochDay, target_kcal: targetKcal, eaten_kcal: Math.round(eaten), active_kcal: null, steps: null, workout_kcal: 0 } : null;

  return (
    <>
      <h1>{t('me.title')}</h1>
      <div className="daynav">
        <button className="ghost" onClick={() => setEpochDay((d) => d - 1)} aria-label="previous day">‹</button>
        <strong>{epochDay === today ? t('day.today') : formatDay(epochDay, tag)}</strong>
        <button className="ghost" onClick={() => setEpochDay((d) => Math.min(today, d + 1))} disabled={epochDay >= today} aria-label="next day">›</button>
      </div>

      <section className="card">
        <DayLine summary={summary} eatenFallback={eaten} />
        {me.targets && (
          <p className="muted" style={{ fontSize: '0.85rem', margin: '4px 0 0' }}>
            {t('me.macros', { p: me.targets.proteinG, f: me.targets.fatG, c: me.targets.carbsG })}
          </p>
        )}
      </section>

      {groups && MEAL_SLOTS.map((slot) => {
        const entries = groups.get(slot) ?? [];
        return (
          <section className="card" key={slot} aria-label={t(`meal.${slot}`)}>
            <div className="meal-title">
              <span>{t(`meal.${slot}`)}</span>
              <span>{Math.round(sumKcal(entries))} {t('unit.kcal')}</span>
            </div>
            {entries.map((e) => (
              <div className="item" key={e.id}>
                <span className="name">{e.name}</span>
                <span className="detail">
                  {e.grams !== null ? `${Math.round(e.grams)} ${t('unit.g')} · ` : ''}{Math.round(e.kcal)} {t('unit.kcal')} · {formatTime(e.logged_at, tag)}
                </span>
                <button className="danger" aria-label={t('me.delete')} onClick={() => deleteFood.mutate({ id: e.id, epochDay, targetKcal })} style={{ padding: '2px 8px' }}>×</button>
              </div>
            ))}
            <Link to={`/me/add/${epochDay}/${slot}`}>
              <button className="ghost" style={{ paddingLeft: 0 }}>+ {t('me.add')}</button>
            </Link>
          </section>
        );
      })}

      <section className="card">
        <div className="row">
          <span>{t('day.water')}</span>
          <strong>{day.data ? sumWater(day.data.water) : 0} {t('unit.ml')}</strong>
        </div>
        <div className="row" style={{ justifyContent: 'flex-start', marginTop: 8 }}>
          <button className="ghost" onClick={() => addWater.mutate({ ml: 250, epochDay })}>{t('me.water_add_250')}</button>
          <button className="ghost" onClick={() => addWater.mutate({ ml: 500, epochDay })}>{t('me.water_add_500')}</button>
          <button className="ghost" onClick={() => undoWater.mutate({ epochDay })} disabled={!day.data || sumWater(day.data.water) === 0}>{t('me.water_undo')}</button>
        </div>
      </section>

      <section className="card">
        <div className="row">
          <span>{t('me.weight')}</span>
          <span className="muted">{me.weightKg !== null ? `${me.weightKg} ${t('unit.kg')}` : '—'}</span>
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <input type="text" inputMode="decimal" value={weightText} placeholder={me.weightKg !== null ? String(me.weightKg) : ''} onChange={(e) => setWeightText(e.target.value)} style={{ maxWidth: 140 }} />
          <button
            className="ghost"
            disabled={logWeight.isPending || !(Number(weightText.replace(',', '.')) >= 30 && Number(weightText.replace(',', '.')) <= 300)}
            onClick={() => logWeight.mutate({ weightKg: Number(weightText.replace(',', '.')), epochDay }, { onSuccess: () => setWeightText('') })}
          >
            {logWeight.isSuccess && weightText === '' ? t('me.weight_saved') : t('me.weight_save')}
          </button>
        </div>
      </section>
    </>
  );
}
