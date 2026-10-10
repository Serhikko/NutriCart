import { useId, useState, type CSSProperties, type FormEvent } from 'react';
import { useI18n } from '../lib/i18n';
import { dayMonth, parseKg, validKg } from '../lib/dayView';
import { Button } from './ui/Button';
import { Card, CardHead } from './ui/Card';
import { Digits } from './ui/Digits';
import { Icon } from './ui/Icon';

interface WeightCardProps {
  /** The day the card logs a weight for. */
  epochDay: number;
  isToday: boolean;
  /** The weight shown: the latest one on today, that day's own on an earlier day (null: none logged). */
  kg: number | null;
  /** The latest known weight, offered as the field's placeholder. */
  latestKg: number | null;
  /** The change over the week up to this day, in kg (negative is down). */
  change: number | null;
  /** Saves a weight for `epochDay`; resolves once it is stored. */
  onSave: (kg: number) => Promise<unknown>;
  /** A save is under way. */
  saving?: boolean;
  className?: string;
}

/**
 * The weight card: the value in large numerals with the week's trend, and a
 * field to log a weight for the day shown. Today it is "Weight today"; on an
 * earlier day it says which day it logs for ("Weight on 8 October"), so a
 * weight is never filed under today by mistake.
 */
export function WeightCard({ epochDay, isToday, kg, latestKg, change, onSave, saving = false, className }: WeightCardProps) {
  const { t, tag } = useI18n();
  const id = useId();
  const [text, setText] = useState('');
  const [saved, setSaved] = useState(false);
  // A different day starts with an empty field.
  const [day, setDay] = useState(epochDay);
  if (day !== epochDay) {
    setDay(epochDay);
    setText('');
    setSaved(false);
  }

  const nf1 = new Intl.NumberFormat(tag, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const signed = new Intl.NumberFormat(tag, { signDisplay: 'exceptZero', minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const label = isToday ? t('me.weight') : t('me.weight_on', { date: dayMonth(epochDay, tag) });
  const value = parseKg(text);
  const canSave = !saving && validKg(value);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!canSave) return;
    onSave(value).then(
      () => {
        setText('');
        setSaved(true);
      },
      () => setSaved(false),
    );
  };

  return (
    <Card metric="weight" className={['weight-card', 'a-rise', className ?? ''].filter(Boolean).join(' ')} style={{ '--d': 580 } as CSSProperties} labelledBy={id}>
      <CardHead id={id} icon="scale" label={label} />
      <div className="weight-now">
        <div className="big-v num">
          {kg == null ? (
            '—'
          ) : (
            <>
              <Digits value={kg} format={(n) => nf1.format(n)} />
              <span className="unit">{t('unit.kg')}</span>
            </>
          )}
        </div>
        {kg != null && change != null && change !== 0 && (
          <span className={change < 0 ? 'delta' : 'delta up'}>
            {/* The arrow and the colour say up or down; a screen reader hears the sign instead. */}
            <span className="delta-v" aria-hidden="true">
              <Icon name={change < 0 ? 'arrowDown' : 'arrowUp'} size="xs" />
              {t('me.weight_delta', { kg: nf1.format(Math.abs(change)) })}
            </span>
            <span className="sr">{t('me.weight_delta', { kg: signed.format(change) })}</span>
          </span>
        )}
      </div>
      <form className="field-row weight-form" onSubmit={submit}>
        <label className="field">
          <input
            type="text"
            inputMode="decimal"
            value={text}
            aria-label={label}
            placeholder={latestKg !== null ? nf1.format(latestKg) : ''}
            onChange={(e) => {
              setText(e.target.value);
              setSaved(false);
            }}
          />
          <span className="sfx">{t('unit.kg')}</span>
        </label>
        <Button variant="fill" type="submit" disabled={!canSave}>
          {saved && text === '' ? t('me.weight_saved') : t('me.weight_save')}
        </Button>
      </form>
    </Card>
  );
}
