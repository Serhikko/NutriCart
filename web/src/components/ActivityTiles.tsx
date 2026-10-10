import type { CSSProperties } from 'react';
import { useI18n } from '../lib/i18n';
import { formatTime } from '../lib/dates';
import { CardHead, type Metric } from './ui/Card';
import { Digits } from './ui/Digits';
import type { IconName } from './ui/Icon';

interface ActivityTilesProps {
  steps: number | null;
  /** Active energy in kcal. */
  active: number | null;
  /** When the phone published these (its "From your phone · 16:58" footnote). */
  updatedAt?: string | null;
  /**
   * A partner's day: three compact tiles, water first. Leave it out on your own
   * day, where water has its own card.
   */
  water?: number | null;
  className?: string;
}

interface Tile {
  key: string;
  metric: Metric;
  icon: IconName;
  label: string;
  value: number | null;
  unit?: string;
}

/**
 * What the phone measured (steps, active energy) as Health-style tiles: the
 * label and glyph in the metric's ink, the value in large rounded numerals
 * pushed to the bottom (so values line up even when a label wraps), and "—"
 * for anything missing. On a partner's day the water joins them as a third,
 * compact tile.
 */
export function ActivityTiles({ steps, active, updatedAt, water, className }: ActivityTilesProps) {
  const { t, tag } = useI18n();
  const partner = water !== undefined;
  const tiles: Tile[] = [
    ...(partner ? [{ key: 'water', metric: 'water' as const, icon: 'drop' as const, label: t('day.water'), value: water ?? null, unit: t('unit.ml') }] : []),
    { key: 'steps', metric: 'steps', icon: 'steps', label: t('day.steps'), value: steps },
    { key: 'active', metric: 'kcal', icon: 'flame', label: t(partner ? 'day.active' : 'day.active_energy'), value: active, unit: t('unit.kcal') },
  ];
  const foot = !partner && updatedAt ? t('day.from_phone', { time: formatTime(updatedAt, tag) }) : null;

  return (
    <div className={['tiles', partner ? 'three' : '', className ?? ''].filter(Boolean).join(' ')}>
      {tiles.map((tile, i) => (
        <article className={`card tile m-${tile.metric} a-rise`} style={{ '--d': 380 + i * 40 } as CSSProperties} key={tile.key}>
          <CardHead icon={tile.icon} label={tile.label} as="span" />
          <div className="t-v num">
            {tile.value == null ? (
              '—'
            ) : (
              <>
                <Digits value={Math.round(tile.value)} />
                {tile.unit && <span className="unit">{tile.unit}</span>}
              </>
            )}
          </div>
          {foot && <div className="t-f">{foot}</div>}
        </article>
      ))}
    </div>
  );
}
