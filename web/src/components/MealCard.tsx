import { useI18n } from '../lib/i18n';
import { formatTime } from '../lib/dates';
import { sumKcal, type FoodLogEntry, type MealSlot } from '../lib/diary';

/** One meal section, like a MealSection on the phone's diary screen. */
export function MealCard({ slot, entries }: { slot: MealSlot; entries: FoodLogEntry[] }) {
  const { t, tag } = useI18n();
  if (entries.length === 0) return null;
  return (
    <section className="card" aria-label={t(`meal.${slot}`)}>
      <div className="meal-title">
        <span>{t(`meal.${slot}`)}</span>
        <span>
          {Math.round(sumKcal(entries))} {t('unit.kcal')}
        </span>
      </div>
      {entries.map((e) => (
        <div className="item" key={e.id}>
          <span className="name">{e.name}</span>
          <span className="detail">
            {e.grams !== null ? `${Math.round(e.grams)} ${t('unit.g')} · ` : ''}
            {Math.round(e.kcal)} {t('unit.kcal')} · {formatTime(e.logged_at, tag)}
          </span>
        </div>
      ))}
    </section>
  );
}
