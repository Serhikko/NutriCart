import { useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useI18n } from '../lib/i18n';
import { useOwnerRealtime, useWeek } from '../lib/queries';
import { formatDay, todayEpochDay } from '../lib/dates';
import { onePerDay } from '../lib/diary';

/** Seven days of calories against target, and the weight trend. */
export function Week() {
  const { ownerId = '' } = useParams();
  const { t, tag } = useI18n();
  const today = todayEpochDay();
  const week = useWeek(ownerId, today);
  useOwnerRealtime(ownerId);

  if (week.isLoading) return <p className="muted">{t('loading')}</p>;
  if (week.isError || !week.data) return <p className="error">{t('error.generic')}</p>;

  const eatenByDay = new Map<number, number>();
  for (const e of week.data.entries) eatenByDay.set(e.epoch_day, (eatenByDay.get(e.epoch_day) ?? 0) + e.kcal);
  const summaryByDay = new Map(week.data.summaries.map((s) => [s.epoch_day, s]));

  const kcalRows = week.data.days.map((d) => ({
    day: formatDay(d, tag).split(' ')[0],
    eaten: Math.round(summaryByDay.get(d)?.eaten_kcal ?? eatenByDay.get(d) ?? 0),
    target: summaryByDay.get(d)?.target_kcal ?? null,
  }));
  const weightRows = onePerDay(week.data.weights).map((w) => ({ day: formatDay(w.epoch_day, tag), kg: w.weight_kg }));
  const hasAny = kcalRows.some((r) => r.eaten > 0) || weightRows.length > 0;

  return (
    <>
      <h1>{t('week.title')}</h1>
      {!hasAny && <p className="muted">{t('week.no_data')}</p>}

      <section className="card">
        <h2 style={{ marginTop: 0 }}>{t('week.kcal')}</h2>
        <div className="chart">
          <ResponsiveContainer>
            <BarChart data={kcalRows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis dataKey="day" tick={{ fontSize: 12 }} stroke="var(--text-2)" />
              <YAxis tick={{ fontSize: 12 }} stroke="var(--text-2)" />
              <Tooltip />
              <Bar dataKey="eaten" name={t('week.eaten')} fill="var(--primary)" radius={[6, 6, 0, 0]} />
              <Bar dataKey="target" name={t('week.target')} fill="var(--border)" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      {weightRows.length > 0 && (
        <section className="card">
          <h2 style={{ marginTop: 0 }}>{t('week.weight')}</h2>
          <div className="chart">
            <ResponsiveContainer>
              <LineChart data={weightRows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="day" tick={{ fontSize: 12 }} stroke="var(--text-2)" />
                <YAxis domain={['dataMin - 1', 'dataMax + 1']} tick={{ fontSize: 12 }} stroke="var(--text-2)" />
                <Tooltip />
                <Line type="monotone" dataKey="kg" name={t('unit.kg')} stroke="var(--primary)" strokeWidth={2} dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}
    </>
  );
}
