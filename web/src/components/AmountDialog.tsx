import { useState, type CSSProperties, type FormEvent, type ReactNode } from 'react';
import { useI18n } from '../lib/i18n';
import type { FoodProduct } from '../domain/openFoodFacts';
import { forGrams } from '../domain/food';
import { isZakazProduct } from '../domain/zakaz';
import { Icon } from './ui/Icon';
import { Ring } from './ui/Ring';
import { Digits } from './ui/Digits';
import { Button } from './ui/Button';
import { AmountStepper } from './ui/AmountStepper';
import { ChipGroup, type ChipOption } from './ui/ChipGroup';
import { productGlyph } from './ResultRow';

/** The most one entry can weigh, as on the phone. */
const MAX_GRAMS = 5000;
// − and + move grams by 10 and portions by half a portion.
const GRAM_STEP = 10;
const PORTION_STEP = 0.5;

type Mode = 'mass' | 'portions';
type Vars = Record<`--${string}`, string | number>;
const vars = (v: Vars) => v as CSSProperties;

const parseAmount = (text: string) => Number(text.trim().replace(',', '.'));
/** Two amounts within half a gram are the same chip. */
const sameGrams = (a: number, b: number) => Math.abs(a - b) < 0.5;

interface AmountDialogProps {
  product: FoodProduct;
  onConfirm: (grams: number, servings: number | null) => void;
  /** The close button (named "Cancel"): the only way out inside the sheet. */
  onCancel: () => void;
  /**
   * What the day already holds and its target, in kcal: with both, a preview
   * ring shows today plus this food, and the line under the number says what
   * is left after it. Leave them out to show the portion on its own.
   */
  eatenKcal?: number | null;
  targetKcal?: number | null;
  /** A message at the top of the sheet (the product could not be remembered, the log failed). */
  notice?: ReactNode;
  /** The entry is on its way: Add waits. */
  busy?: boolean;
  /** The preview ring carries the hero-ring view-transition name, so it lands on the day's ring (A1). */
  shareRing?: boolean;
  /** Start from this many grams instead of a portion (or 100 g). */
  initialGrams?: number;
}

function startingAmount(product: FoodProduct, initialGrams?: number): string {
  if (initialGrams != null && initialGrams > 0) return String(Math.round(initialGrams * 10) / 10);
  // One portion when the label states one: what people most often log.
  return String(product.servingSizeG ? Math.round(product.servingSizeG) : 100);
}

/**
 * The amount sheet: how much of a product, and what that does to the day.
 * The content of a Sheet (which is the dialog, named by the product): header
 * with the close button, a preview ring with the raspberry "this food" arc,
 * the kcal in large Ember digits that hand off as the amount changes, "N kcal
 * left after this", the stepper (grams or ml, or portions when the label
 * states a portion size), quick amounts, the macro composition, the per-100
 * line, and Add.
 *
 * In the docked inspector a new product swaps in without remounting, so the
 * numbers hand off from the last product's; the amount starts over.
 */
export function AmountDialog({
  product,
  onConfirm,
  onCancel,
  eatenKcal = null,
  targetKcal = null,
  notice,
  busy = false,
  shareRing = false,
  initialGrams,
}: AmountDialogProps) {
  const { t, tp, tag } = useI18n();
  const serving = product.servingSizeG && product.servingSizeG > 0 ? product.servingSizeG : null;
  const massUnit = product.liquid ? t('unit.ml') : t('unit.g');

  const [state, setState] = useState(() => ({ id: product.id, mode: 'mass' as Mode, text: startingAmount(product, initialGrams) }));
  let current = state;
  if (state.id !== product.id) {
    current = { id: product.id, mode: 'mass', text: startingAmount(product, initialGrams) };
    setState(current);
  }
  const { mode, text } = current;
  const setText = (next: string) => setState((s) => ({ ...s, text: next }));

  const amount = parseAmount(text);
  const valid = Number.isFinite(amount) && amount > 0;
  const portions = mode === 'portions' && serving !== null;
  const grams = !valid ? null : portions ? amount * serving! : amount;
  const ok = grams !== null && grams <= MAX_GRAMS;
  const n = forGrams(product.kcalPer100g, product.proteinPer100g, product.fatPer100g, product.carbsPer100g, ok ? grams! : 0);
  const kcal = Math.round(n.kcal);

  const whole = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const tenth = new Intl.NumberFormat(tag, { maximumFractionDigits: 1 });
  const plain = new Intl.NumberFormat(tag, { maximumFractionDigits: 2, useGrouping: false });
  const percent = new Intl.NumberFormat(tag, { style: 'percent', maximumFractionDigits: 0 });

  // "portion" / "portions" after the number, counted ("1,5 порції", "5 порцій").
  const portionUnit = tp('add.portion_unit', valid ? amount : 1);
  const unit = portions ? portionUnit : massUnit;

  const switchUnit = (next: string) => {
    if (!serving) return;
    if (next === portionUnit && mode === 'mass') {
      // 207 g is one portion, 100 g half of one: the nearest half portion, at least one half.
      const count = grams !== null ? Math.max(PORTION_STEP, Math.round(grams / serving / PORTION_STEP) * PORTION_STEP) : 1;
      setState((s) => ({ ...s, mode: 'portions', text: plain.format(count) }));
    } else if (next === massUnit && mode === 'portions') {
      setState((s) => ({ ...s, mode: 'mass', text: plain.format(grams !== null ? Math.round(grams) : Math.round(serving)) }));
    }
  };

  // Quick amounts: 100 g and one or two portions when the label states one, otherwise sizes that suit the product.
  const presets: { key: string; grams: number; label: string; portions?: number }[] = [];
  const massChip = (g: number) => ({ key: `g${g}`, grams: g, label: `${whole.format(g)} ${massUnit}` });
  if (serving) {
    if (!sameGrams(serving, 100) && !sameGrams(serving * 2, 100)) presets.push(massChip(100));
    for (const count of [1, 2]) {
      presets.push({
        key: `p${count}`,
        // Whole grams, as the field shows them once the chip is chosen.
        grams: Math.round(serving * count),
        portions: count,
        label: tp('add.portion_chip', count, { g: whole.format(serving * count), unit: massUnit }),
      });
    }
  } else {
    for (const g of product.liquid ? [100, 250, 330, 500] : [50, 100, 200]) presets.push(massChip(g));
  }
  // The chip that stands for the amount now: by count while portions are on, by whole grams otherwise.
  const chosen = !valid
    ? null
    : (presets.find((p) => (portions ? p.portions === amount : grams !== null && sameGrams(p.grams, Math.round(grams))))?.key ?? null);
  const chips: ChipOption<string>[] = presets.map((p) => ({ value: p.key, label: p.label }));
  const pickPreset = (key: string) => {
    const preset = presets.find((p) => p.key === key);
    if (!preset) return;
    // A portion chip keeps portions when they are on; everything else is an amount in grams (or ml).
    if (mode === 'portions' && preset.portions != null) setState((s) => ({ ...s, text: plain.format(preset.portions!) }));
    else setState((s) => ({ ...s, mode: 'mass', text: plain.format(Math.round(preset.grams)) }));
  };

  // Today plus this food, when the day and its target are known.
  const day = targetKcal != null && targetKcal > 0 && eatenKcal != null ? { eaten: eatenKcal, target: targetKcal } : null;
  const after = day ? day.target - day.eaten - (ok ? n.kcal : 0) : null;

  // The macros of this amount, and their share of its energy for the composition bar.
  const macros = [
    { key: 'protein', label: t('ob.protein'), grams: n.proteinG, energy: n.proteinG * 4, per100: product.proteinPer100g },
    { key: 'fat', label: t('ob.fat'), grams: n.fatG, energy: n.fatG * 9, per100: product.fatPer100g },
    { key: 'carbs', label: t('ob.carbs'), grams: n.carbsG, energy: n.carbsG * 4, per100: product.carbsPer100g },
  ];
  const energy = macros.reduce((sum, m) => sum + m.energy, 0);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (ok && !busy) onConfirm(grams!, portions ? amount : null);
  };

  const brandLine = [product.brand, t('add.per100_brand', { kcal: whole.format(product.kcalPer100g), unit: massUnit })].filter(Boolean).join(' · ');

  return (
    <form className="amount-panel" onSubmit={submit} noValidate>
      {notice}
      <div className="sh-head">
        <span className="glyph" aria-hidden="true">
          <Icon name={productGlyph(product)} />
        </span>
        <div className="t">
          <h2>{product.name}</h2>
          <p>{brandLine}</p>
        </div>
        <button type="button" className="close" aria-label={t('add.cancel')} onClick={onCancel}>
          <Icon name="x" />
        </button>
      </div>

      <div className={day ? 'sh-hero' : 'sh-hero no-ring'}>
        {day && (
          <div className="ring-wrap sh-ring" aria-hidden="true">
            <Ring
              value={day.eaten}
              target={day.target}
              add={ok ? n.kcal : 0}
              size={88}
              stroke={11}
              muted
              className={shareRing ? 'preview shared' : 'preview'}
            />
            <span className="pct num">{percent.format((day.eaten + (ok ? n.kcal : 0)) / day.target)}</span>
          </div>
        )}
        <div className="sh-main">
          <div className="sh-kcal num">
            {ok ? <Digits value={kcal} gradient enter delay={40} step={60} /> : <span className="sh-none">—</span>}
            <span className="unit">{t('unit.kcal')}</span>
          </div>
          {after !== null && ok && <AfterLine kcal={after} format={whole.format} />}
        </div>
      </div>

      <AmountStepper
        value={text}
        onChange={setText}
        unit={unit}
        units={serving ? [massUnit, portionUnit] : undefined}
        onUnitChange={serving ? switchUnit : undefined}
        step={portions ? PORTION_STEP : GRAM_STEP}
        max={portions ? MAX_GRAMS / serving! : MAX_GRAMS}
        label={portions ? t('add.portions') : product.liquid ? t('add.ml') : t('add.grams')}
        decLabel={t('add.less')}
        incLabel={t('add.more')}
      />

      <ChipGroup className="sh-chips" label={t('add.amount')} options={chips} value={chosen} onChange={pickPreset} />

      <div className="comp">
        <div className={energy > 0 ? 'comp-bar' : 'comp-bar is-empty'} aria-hidden="true">
          {macros.map((m, i) => (
            <i
              key={m.key}
              className={m.energy > 0 ? `m-${m.key}` : `m-${m.key} is-zero`}
              style={vars({ '--f': energy > 0 ? Number((m.energy / energy).toFixed(3)) : 0, '--d': 220 + i * 70 })}
            />
          ))}
        </div>
        <div className="comp-legend">
          {macros.map((m) => (
            <div key={m.key} className={`macro m-${m.key}`}>
              <div className="m-l">{m.label}</div>
              <div className="m-v num">
                {ok ? tenth.format(m.grams) : '—'}
                <span className="unit">{t('unit.g')}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <p className="per100">
        <span>{t('add.per100', { unit: massUnit })}</span>
        {/* No-break spaces hold each nutrient to its value ("fat 0.2 g"), so a narrow line breaks only between nutrients. */}
        {macros.map((m) => ` · ${m.label.toLocaleLowerCase(tag)}\u00a0${tenth.format(m.per100)}\u00a0${t('unit.g')}`).join('')}
      </p>
      {/* Values a shop typed in, not Open Food Facts' community: say where they come from, as the phone does. */}
      {isZakazProduct(product) && <p className="source-note">{t('add.source_zakaz')}</p>}

      <Button type="submit" variant="ink" size="lg" icon="plus" className="sh-confirm" disabled={!ok || busy}>
        {t('add.confirm')}
      </Button>
    </form>
  );
}

/** "709 kcal left after this" (or "… over after this") with the number in bold, in either language's word order. */
function AfterLine({ kcal, format }: { kcal: number; format: (n: number) => string }) {
  const { t } = useI18n();
  const over = Math.round(kcal) < 0;
  const mark = '\u0000';
  const [before, rest = ''] = t(over ? 'add.over_after' : 'add.left_after', { kcal: mark }).split(mark);
  return (
    <p className={over ? 'sh-after is-over' : 'sh-after'}>
      {before}
      <b className="num">{format(Math.abs(Math.round(kcal)))}</b>
      {rest}
    </p>
  );
}
