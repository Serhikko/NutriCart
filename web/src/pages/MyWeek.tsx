import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useI18n } from '../lib/i18n';
import { useOwnerRealtime, useWeek } from '../lib/queries';
import { useMyTargets } from '../lib/myTargets';
import { formatDay, todayEpochDay } from '../lib/dates';
import { onePerDay } from '../lib/diary';
import { dayState, streak } from '../domain/habits';

/** Seven days of the signed-in user's own numbers, targets from the same math as the phone. */
export function MyWeek() {
  const { t, tag } = useI18n();
  const today = todayEpochDay();
  const me = useMyTargets(today);
  const week = useWeek(me.userId ?? '', today);
  useOwnerRealtime(me.userId);

  if (me.loading || week.isLoading) return <p className="muted">{t('loading')}</p>;
  if (week.isError || !week.data) return <p className="error">{t('error.generic')}</p>;

  const eatenByDay = new Map<number, number>();
  for (const e of week.data.entries) eatenByDay.set(e.epoch_day, (eatenByDay.get(e.epoch_day) ?? 0) + e.kcal);
  const summaryByDay = new Map(week.data.summaries.map((s) => [s.epoch_day, s]));
  const fallbackTarget = me.targets?.kcal ?? null;

  const rows = week.data.days.map((d) => {
    const eaten = Math.round(eatenByDay.get(d) ?? summaryByDay.get(d)?.eaten_kcal ?? 0);
    const target = summaryByDay.get(d)?.target_kcal ?? fallbackTarget;
    return { epochDay: d, day: formatDay(d, tag).split(' ')[0], eaten, target, state: dayState(eaten, target ?? 0) };
  });
  const logged = rows.filter((r) => r.state !== 'EMPTY');
  const good = logged.filter((r) => r.state === 'GOOD').length;
  const loggedDays = new Set(rows.filter((r) => r.eaten > 0).map((r) => r.epochDay));
  const weightRows = onePerDay(week.data.weights).map((w) => ({ day: formatDay(w.epoch_day, tag), kg: w.weight_kg }));

  return (
    <>
      <h1>{t('me.week')}</h1>
      <section className="card">
        <div className="row">
          <span>{t('week.adherence', { good, logged: logged.length })}</span>
          <span className="badge">{t('me.streak', { days: streak(loggedDays, today) })}</span>
        </div>
        <div className="grid" style={{ gridTemplateColumns: 'repeat(7, 1fr)', marginTop: 10 }}>
          {rows.map((r) => (
            <div key={r.epochDay} className="stat" style={{ alignItems: 'center' }}>
              <span className="muted" style={{ fontSize: '0.75rem' }}>{r.day}</span>
              <span aria-label={r.state} style={{ width: 12, height: 12, borderRadius: 6, background: r.state === 'GOOD' ? 'var(--primary)' : r.state === 'OVER' ? 'var(--error)' : 'var(--border)' }} />
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2 style={{ marginTop: 0 }}>{t('week.kcal')}</h2>
        <div className="chart">
          <ResponsiveContainer>
            <BarChart data={rows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
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
