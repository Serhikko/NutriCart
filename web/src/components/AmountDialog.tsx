import { useState } from 'react';
import { useI18n } from '../lib/i18n';
import type { FoodProduct } from '../domain/openFoodFacts';
import { forGrams, servingsToGrams } from '../domain/food';

/** The phone's AmountDialog: grams, or portions when the label states a portion size. */
export function AmountDialog({ product, onConfirm, onCancel }: { product: FoodProduct; onConfirm: (grams: number, servings: number | null) => void; onCancel: () => void }) {
  const { t } = useI18n();
  const [usePortions, setUsePortions] = useState(false);
  const [text, setText] = useState('100');
  const amount = Number(text.replace(',', '.'));
  const valid = Number.isFinite(amount) && amount > 0;
  const grams = !valid ? null : usePortions && product.servingSizeG ? servingsToGrams(amount, product.servingSizeG) : amount;
  const ok = grams !== null && grams <= 5000;
  const kcal = ok ? forGrams(product.kcalPer100g, product.proteinPer100g, product.fatPer100g, product.carbsPer100g, grams!).kcal : null;

  const row = (label: string, v: number | null) => (
    <div className="item" key={label}>
      <span className="name muted">{label}</span>
      <span>{v === null ? '—' : `${Math.round(v * 10) / 10} ${t('unit.g')}`}</span>
    </div>
  );

  return (
    <div className="card" role="dialog" aria-label={product.name}>
      <h2 style={{ marginTop: 0 }}>{product.name}</h2>
      {product.brand && <p className="muted" style={{ marginTop: -6 }}>{product.brand}</p>}
      {product.servingSizeG && (
        <div className="row" style={{ justifyContent: 'flex-start', marginBottom: 8 }}>
          <button className={usePortions ? 'ghost' : ''} onClick={() => { setUsePortions(false); setText('100'); }}>{t('add.grams')}</button>
          <button className={usePortions ? '' : 'ghost'} onClick={() => { setUsePortions(true); setText('1'); }}>{t('add.portions')}</button>
          {usePortions && <span className="muted" style={{ fontSize: '0.85rem' }}>{t('add.portion_size', { g: Math.round(product.servingSizeG) })}</span>}
        </div>
      )}
      <input type="text" inputMode="decimal" value={text} onChange={(e) => setText(e.target.value)} aria-label={usePortions ? t('add.portions') : t('add.grams')} />
      {kcal !== null && (
        <p style={{ fontWeight: 700, color: 'var(--primary)', fontSize: '1.2rem' }}>
          {Math.round(kcal)} {t('unit.kcal')}
        </p>
      )}
      <p className="muted" style={{ fontSize: '0.85rem', marginBottom: 4 }}>{t('add.per100')}</p>
      {row(t('ob.protein'), product.proteinPer100g)}
      {row(t('ob.fat'), product.fatPer100g)}
      {row(t('ob.carbs'), product.carbsPer100g)}
      <div className="row" style={{ marginTop: 12 }}>
        <button className="ghost" onClick={onCancel}>{t('add.cancel')}</button>
        <button disabled={!ok} onClick={() => ok && onConfirm(grams!, usePortions ? amount : null)}>{t('add.confirm')}</button>
      </div>
    </div>
  );
}
