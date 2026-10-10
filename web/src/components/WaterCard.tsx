import { useId, useState, type CSSProperties } from 'react';
import { useI18n } from '../lib/i18n';
import { usePrevious } from '../lib/motion';
import { formatTime } from '../lib/dates';
import { GLASSES, glassFill } from '../lib/dayView';
import { Button } from './ui/Button';
import { Card, CardHead } from './ui/Card';
import { Digits } from './ui/Digits';

interface WaterCardProps {
  /** Millilitres drunk that day. */
  ml: number;
  /** When the last glass was logged. */
  lastAt?: string | null;
  /** Your own day: +250, +500 and Undo. */
  editable?: boolean;
  onAdd?: (ml: number) => void;
  onUndo?: () => void;
  className?: string;
}

/**
 * The day's water: the total in large numerals, eight 250 ml glasses that
 * fill (a glass can be part full), and +250 ml, +500 ml and Undo. On the
 * first open the glasses fill one after another (T9); a new glass fills on
 * its own spring, and Undo drains the last one quickly (T10).
 */
export function WaterCard({ ml, lastAt, editable = false, onAdd, onUndo, className }: WaterCardProps) {
  const { t, tag } = useI18n();
  const id = useId();
  const previous = usePrevious(ml);
  const draining = previous !== undefined && ml < previous;
  // Only the glasses already full when the card appeared fill one after another on the first
  // open. A glass filled later (a tap, a live insert, another day) fills at once on its own
  // spring: given the entrance then, it would wait out the first-open delay, empty.
  const [initialMl] = useState(ml);

  return (
    <Card metric="water" className={['water-card', 'a-rise', className ?? ''].filter(Boolean).join(' ')} style={{ '--d': 520 } as CSSProperties} labelledBy={id}>
      <CardHead id={id} icon="drop" label={t('day.water')} meta={lastAt ? t('water.last', { time: formatTime(lastAt, tag) }) : null} />
      <div className="water-row">
        <div className="big-v num">
          <Digits value={ml} />
          <span className="unit">{t('unit.ml')}</span>
        </div>
        <div className={draining ? 'glasses draining' : 'glasses'} aria-hidden="true">
          {Array.from({ length: GLASSES }, (_, i) => {
            const fill = glassFill(ml, i);
            return (
              <svg className="glass-i" viewBox="0 0 19 28" key={i} focusable="false">
                <path className="body" d="M1.5 2.5h16l-1.7 22a2 2 0 0 1-2 1.9H5.2a2 2 0 0 1-2-1.9z" />
                <g className={glassFill(initialMl, i) > 0 ? 'liq-g a-liquid' : 'liq-g'} style={{ '--f': Number(fill.toFixed(3)), '--d': 560 + i * 55 } as CSSProperties}>
                  <path className="liq" d="M3.1 9.5c2 1.2 4.2 1.2 6.4 0s4.4-1.2 6.4 0l-1.2 15a1.6 1.6 0 0 1-1.6 1.5H5.9a1.6 1.6 0 0 1-1.6-1.5z" />
                </g>
              </svg>
            );
          })}
        </div>
      </div>
      {editable && (
        <div className="btn-row">
          <Button variant="water" type="button" onClick={() => onAdd?.(250)}>
            {t('me.water_add_250')}
          </Button>
          <Button variant="water" type="button" onClick={() => onAdd?.(500)}>
            {t('me.water_add_500')}
          </Button>
          <Button variant="plain" type="button" className="undo" disabled={ml === 0} onClick={() => onUndo?.()}>
            {t('me.water_undo')}
          </Button>
        </div>
      )}
    </Card>
  );
}
