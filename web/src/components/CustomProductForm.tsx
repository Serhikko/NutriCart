import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { useI18n } from '../lib/i18n';
import type { BarcodeCountry } from '../domain/barcodeOrigin';
import { hasCoreValues, type FoodProduct, type ProductPrefill } from '../domain/openFoodFacts';
import { checkDraft, draftFromPrefill, MAX_NAME_LENGTH, type CustomProductDraft, type DraftField } from '../domain/customProduct';
import { Icon } from './ui/Icon';
import { Button } from './ui/Button';

/**
 * The line above a prefilled form, naming who knows the product. With all
 * four core values there, nothing is missing, so it asks for a check
 * instead: a shop's values that don't add up, or OFF's estimates.
 */
function prefillLine(prefill: ProductPrefill, from: 'off' | 'shops' | null): string {
  if (from === 'shops') return hasCoreValues(prefill) ? 'add.check_shop' : 'add.incomplete_shop';
  return hasCoreValues(prefill) && prefill.estimated ? 'add.check_estimated' : 'add.incomplete';
}

const OPTIONAL: readonly DraftField[] = ['fiber', 'sugars', 'salt', 'saturatedFat'];

/**
 * The phone's custom-food dialog for a product added under its barcode: name,
 * brand, the four label values per 100 g (per 100 ml for a drink), an
 * optional portion and the optional details. Starts from what Open Food Facts
 * or a Ukrainian shop knows (`prefill`, from `prefillFrom`) or empty. Save
 * stays disabled until the input is sane.
 *
 * Laid out as an iOS form inside a Sheet (which is the dialog, named by the
 * title): who knows the product and its barcode on top, the fields as wells
 * with their labels above them, the four values per 100 in a 2x2 grid, the
 * optional details behind a disclosure, and Cancel and Save in a footer that
 * stays in reach while the form scrolls. A value outside the phone's ranges
 * gets a red ring and says so under the field.
 *
 * The parent keys this component by its barcode, so reopening it for another
 * product starts from that product's values, not the last one's.
 */
export function CustomProductForm({
  barcode,
  country,
  prefill,
  prefillFrom = 'off',
  saving,
  onSave,
  onCancel,
}: {
  barcode: string;
  country: BarcodeCountry | null;
  prefill: ProductPrefill | null;
  /** Who knows the product in part, for the line above the form. */
  prefillFrom?: 'off' | 'shops' | null;
  saving: boolean;
  onSave: (product: FoodProduct) => void;
  onCancel: () => void;
}) {
  const { t } = useI18n();
  const uid = useId();
  const [draft, setDraft] = useState<CustomProductDraft>(() => draftFromPrefill(prefill));
  // The details start open when a source already filled one in, so nothing it knows is hidden.
  const [moreOpen, setMoreOpen] = useState(() => OPTIONAL.some((key) => draft[key].trim() !== ''));
  const { ok, product } = checkDraft(draft, barcode);
  const unit = draft.liquid ? t('unit.ml') : t('unit.g');
  const set = (key: keyof CustomProductDraft) => (e: { target: { value: string } }) => setDraft({ ...draft, [key]: e.target.value });
  // Only a value that was typed and is out of range is flagged; an empty required field just keeps Save off.
  const flagged = (key: DraftField) => !ok[key] && draft[key].trim() !== '';

  const field = (key: DraftField, label: string, options: { input?: ReactNode; required?: boolean; className?: string } = {}) => {
    const id = `${uid}-${key}`;
    const errorId = `${id}-error`;
    const bad = flagged(key);
    return (
      <div className={['cf-field', options.className ?? ''].filter(Boolean).join(' ')} key={key}>
        <label className="field-label" htmlFor={id}>
          {label}
        </label>
        <div className="field">
          {options.input ?? (
            <input
              id={id}
              type="text"
              inputMode="decimal"
              autoComplete="off"
              value={draft[key]}
              onChange={set(key)}
              aria-required={options.required || undefined}
              aria-invalid={bad || undefined}
              aria-describedby={bad ? errorId : undefined}
            />
          )}
        </div>
        {bad && (
          <p className="cf-error" id={errorId}>
            {t('settings.target_invalid')}
          </p>
        )}
      </div>
    );
  };

  const moreFlagged = OPTIONAL.some(flagged);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (product && !saving) onSave(product);
  };

  return (
    <form className="cf" onSubmit={submit} noValidate>
      <div className="cf-head">
        <span className="glyph" aria-hidden="true">
          <Icon name="leaf" />
        </span>
        <div className="t">
          <h2>{t('cf.title')}</h2>
          <p className="cf-code">
            <span>{t(`add.barcode_line.${country ?? 'other'}`, { code: barcode })}</span>
          </p>
        </div>
      </div>
      {prefill && <p className="cf-lead">{t(prefillLine(prefill, prefillFrom))}</p>}

      <div className="cf-section">
        {field('name', t('cf.name'), {
          input: (
            <input
              id={`${uid}-name`}
              type="text"
              autoComplete="off"
              maxLength={MAX_NAME_LENGTH}
              value={draft.name}
              onChange={set('name')}
              aria-required
              aria-invalid={flagged('name') || undefined}
            />
          ),
        })}
        <div className="cf-field">
          <label className="field-label" htmlFor={`${uid}-brand`}>
            {t('cf.brand')}
          </label>
          <div className="field">
            <input id={`${uid}-brand`} type="text" autoComplete="off" value={draft.brand} onChange={set('brand')} />
          </div>
        </div>
        <label className="cf-switch">
          <span>{t('cf.drink')}</span>
          <input
            type="checkbox"
            role="switch"
            className="switch"
            checked={draft.liquid}
            onChange={(e) => setDraft({ ...draft, liquid: e.target.checked })}
          />
        </label>
      </div>

      <div className="cf-grid">
        {field('kcal', t('cf.kcal', { unit }), { required: true })}
        {field('protein', t('cf.protein', { unit }), { required: true })}
        {field('fat', t('cf.fat', { unit }), { required: true })}
        {field('carbs', t('cf.carbs', { unit }), { required: true })}
      </div>
      {field('serving', t('cf.serving', { unit }), { className: 'cf-serving' })}

      <details className="cf-more" open={moreOpen} onToggle={(e) => setMoreOpen(e.currentTarget.open)}>
        <summary>
          <span>{t('cf.more')}</span>
          {moreFlagged && !moreOpen && <Icon name="warning" size="xs" className="cf-flag" />}
          <Icon name="down" size="sm" className="cf-chev" />
        </summary>
        <div className="cf-grid">
          {field('fiber', t('cf.fiber', { unit }))}
          {field('sugars', t('cf.sugars', { unit }))}
          {field('salt', t('cf.salt', { unit }))}
          {field('saturatedFat', t('cf.sat_fat', { unit }))}
        </div>
      </details>

      <div className="cf-foot">
        <Button type="button" variant="plain" onClick={onCancel}>
          {t('add.cancel')}
        </Button>
        <Button type="submit" variant="ink" disabled={!product || saving}>
          {t('settings.save')}
        </Button>
      </div>
    </form>
  );
}
