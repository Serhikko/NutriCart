import { useId, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { setNavDirection } from '../lib/motion';
import type { MacroTotals } from '../lib/dayView';
import { Bar } from './ui/Bar';
import { Card, CardHead, type Metric } from './ui/Card';
import { Digits } from './ui/Digits';

interface MacroTargets {
  proteinG: number;
  fatG: number;
  carbsG: number;
}

interface MacrosCardProps {
  /** Grams eaten so far, from the diary rows. */
  eaten: MacroTotals;
  /** The day's macro targets; without them the card shows the grams alone. */
  targets: MacroTargets | null;
  className?: string;
}

/**
 * Protein, fat and carbs against their targets: three columns, each a value,
 * a 6 px bar in the macro's colour and "of 115 g" under it. "Targets" goes to
 * the profile, where they are set. On a desktop it sits beside the ring inside
 * the hero card; on a phone it is the card under the hero.
 */
export function MacrosCard({ eaten, targets, className }: MacrosCardProps) {
  const { t, tag } = useI18n();
  const id = useId();
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const g = t('unit.g');
  const rows: { metric: Metric; label: string; value: number; target: number | null }[] = [
    { metric: 'protein', label: t('ob.protein'), value: eaten.protein, target: targets?.proteinG ?? null },
    { metric: 'fat', label: t('ob.fat'), value: eaten.fat, target: targets?.fatG ?? null },
    { metric: 'carbs', label: t('ob.carbs'), value: eaten.carbs, target: targets?.carbsG ?? null },
  ];

  return (
    <Card className={['macros-card', 'a-rise', className ?? ''].filter(Boolean).join(' ')} style={{ '--d': 320 } as CSSProperties} labelledBy={id}>
      <CardHead
        id={id}
        label={t('day.macros')}
        action={
          <Link className="btn-plain" to="/me/onboarding" viewTransition onClick={() => setNavDirection('forward')}>
            {t('day.targets')}
          </Link>
        }
      />
      <div className="macros">
        {rows.map((m, i) => {
          const value = Math.round(m.value);
          const goal = m.target != null && m.target > 0 ? Math.round(m.target) : null;
          const of = goal != null ? t('day.of', { value: `${nf.format(goal)} ${g}` }) : null;
          return (
            <div className={`macro m-${m.metric}`} key={m.metric}>
              <div className="m-l">{m.label}</div>
              <div className="m-v num">
                <Digits value={value} />
                <span className="unit">{g}</span>
              </div>
              {goal != null && (
                <Bar
                  value={value}
                  max={goal}
                  metric={m.metric}
                  label={m.label}
                  delay={420 + i * 80}
                  valueText={`${nf.format(value)} ${g} ${of}`}
                />
              )}
              {of && <div className="m-of num">{of}</div>}
            </div>
          );
        })}
      </div>
    </Card>
  );
}
