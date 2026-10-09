import { useState, type ReactNode } from 'react';
import { useI18n } from '../lib/i18n';
import type { BarcodeCountry } from '../domain/barcodeOrigin';
import type { FoodProduct, ProductPrefill } from '../domain/openFoodFacts';
import { checkDraft, draftFromPrefill, MAX_NAME_LENGTH, type CustomProductDraft, type DraftField } from '../domain/customProduct';

/**
 * The phone's custom-food dialog for a product added under its barcode: name,
 * brand, the four label values per 100 g (per 100 ml for a drink), an
 * optional portion and the optional details. Starts from what Open Food Facts
 * knows (`prefill`) or empty. Save stays disabled until the input is sane.
 *
 * The parent keys this component by its barcode, so reopening it for another
 * product starts from that product's values, not the last one's.
 */
export function CustomProductForm({
  barcode,
  country,
  prefill,
  saving,
  onSave,
  onCancel,
}: {
  barcode: string;
  country: BarcodeCountry | null;
  prefill: ProductPrefill | null;
  saving: boolean;
  onSave: (product: FoodProduct) => void;
  onCancel: () => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<CustomProductDraft>(() => draftFromPrefill(prefill));
  const { ok, product } = checkDraft(draft, barcode);
  const unit = draft.liquid ? t('unit.ml') : t('unit.g');
  const set = (key: keyof CustomProductDraft) => (e: { target: { value: string } }) => setDraft({ ...draft, [key]: e.target.value });

  const field = (key: DraftField, label: string, input?: ReactNode) => (
    <label className="stat" style={{ marginBottom: 10 }} key={key}>
      <span className="muted" style={{ fontSize: '0.85rem' }}>{label}</span>
      {input ?? <input type="text" inputMode="decimal" value={draft[key]} onChange={set(key)} aria-invalid={!ok[key]} />}
      {!ok[key] && draft[key].trim() !== '' && <span className="error" style={{ fontSize: '0.8rem' }}>{t('settings.target_invalid')}</span>}
    </label>
  );

  return (
    <div className="card" role="dialog" aria-label={t('cf.title')}>
      <h2 style={{ marginTop: 0 }}>{t('cf.title')}</h2>
      {prefill && <p className="muted" style={{ marginTop: -4 }}>{t('add.incomplete')}</p>}
      <p className="muted" style={{ fontSize: '0.85rem', marginTop: prefill ? -6 : -4 }}>{t(`add.barcode_line.${country ?? 'other'}`, { code: barcode })}</p>

      {field('name', t('cf.name'), <input type="text" maxLength={MAX_NAME_LENGTH} value={draft.name} onChange={set('name')} aria-invalid={!ok.name} />)}
      <label className="stat" style={{ marginBottom: 10 }}>
        <span className="muted" style={{ fontSize: '0.85rem' }}>{t('cf.brand')}</span>
        <input type="text" value={draft.brand} onChange={set('brand')} />
      </label>
      <label className="row" style={{ cursor: 'pointer', justifyContent: 'flex-start', marginBottom: 10 }}>
        <input type="checkbox" checked={draft.liquid} onChange={(e) => setDraft({ ...draft, liquid: e.target.checked })} />
        <span>{t('cf.drink')}</span>
      </label>
      {field('kcal', t('cf.kcal', { unit }))}
      {field('protein', t('cf.protein', { unit }))}
      {field('fat', t('cf.fat', { unit }))}
      {field('carbs', t('cf.carbs', { unit }))}
      {field('serving', t('cf.serving', { unit }))}

      <span className="muted" style={{ display: 'block', fontWeight: 700, margin: '14px 0 8px' }}>{t('cf.more')}</span>
      {field('fiber', t('cf.fiber', { unit }))}
      {field('sugars', t('cf.sugars', { unit }))}
      {field('salt', t('cf.salt', { unit }))}
      {field('saturatedFat', t('cf.sat_fat', { unit }))}

      <div className="row" style={{ marginTop: 12 }}>
        <button className="ghost" onClick={onCancel}>{t('add.cancel')}</button>
        <button disabled={!product || saving} onClick={() => product && !saving && onSave(product)}>{t('settings.save')}</button>
      </div>
    </div>
  );
}
