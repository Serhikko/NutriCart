import { lazy, Suspense, useCallback, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useMyTargets } from '../lib/myTargets';
import { useLogFood } from '../lib/tracker';
import { OffError, lookupBarcode, searchProducts } from '../lib/openFoodFacts';
import { saveCustomProduct, savedProductFor, searchCustomProducts } from '../lib/customProducts';
import type { FoodProduct, ProductPrefill } from '../domain/openFoodFacts';
import { countryOf, type BarcodeCountry } from '../domain/barcodeOrigin';
import { isStorableBarcode } from '../domain/customProduct';
import type { MealSlot } from '../lib/diary';
import { AmountDialog } from '../components/AmountDialog';
import { CustomProductForm } from '../components/CustomProductForm';

// The barcode library is ~300 kB, so it only loads when someone taps Scan.
const BarcodeScanner = lazy(() => import('../components/BarcodeScanner').then((m) => ({ default: m.BarcodeScanner })));

/**
 * Search Open Food Facts or scan a barcode, then choose an amount: the phone's
 * FoodSearchScreen. A scanned product Open Food Facts lacks, or knows without
 * all of its nutrition, can be added once under its barcode (custom_products);
 * the next scan of that code finds it, and name search lists it first.
 */
export function AddFood() {
  const { epochDay: dayParam = '', slot = 'LUNCH' } = useParams();
  const epochDay = Number(dayParam);
  const meal = slot as MealSlot;
  const { t } = useI18n();
  const navigate = useNavigate();
  const me = useMyTargets(epochDay);
  const logFood = useLogFood(me.userId);

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<FoodProduct[] | null>(null);
  const [status, setStatus] = useState<'idle' | 'searching' | 'short' | 'offline' | 'not_found'>('idle');
  const [detail, setDetail] = useState<string | null>(null);
  const fail = (e: unknown) => {
    setDetail(e instanceof OffError ? e.message : e instanceof Error ? e.message : String(e));
    setStatus('offline');
  };
  const [scanning, setScanning] = useState(false);
  const [barcode, setBarcode] = useState('');
  const [selected, setSelected] = useState<FoodProduct | null>(null);
  /** The last scan found nothing: its digits and their GS1 origin, for the "add it" message. */
  const [notFound, setNotFound] = useState<{ barcode: string; country: BarcodeCountry | null } | null>(null);
  /** The add-this-product form, empty or prefilled from what Open Food Facts knows. */
  const [form, setForm] = useState<{ barcode: string; prefill: ProductPrefill | null } | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);
  const userId = me.userId;

  const search = async (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    if (q.length < 2) return setStatus('short');
    setStatus('searching');
    setDetail(null);
    setNotFound(null);
    // The user's own products first; that lookup never fails, it is just empty.
    const [own, off] = await Promise.allSettled([searchCustomProducts(userId, q), searchProducts(q)]);
    const mine = own.status === 'fulfilled' ? own.value : [];
    if (off.status === 'fulfilled') {
      setResults([...mine, ...off.value]);
      setStatus('idle');
    } else {
      setResults(mine);
      fail(off.reason);
    }
  };

  const onCode = useCallback(
    async (code: string) => {
      setScanning(false);
      setStatus('searching');
      setDetail(null);
      setNotFound(null);
      try {
        const result = await lookupBarcode(code, { saved: (candidates) => savedProductFor(userId, candidates) });
        if (result.kind === 'found') {
          setSelected(result.product);
          setStatus('idle');
        } else if (result.kind === 'incomplete') {
          setForm({ barcode: result.prefill.barcode, prefill: result.prefill });
          setStatus('idle');
        } else {
          setNotFound({ barcode: result.barcode, country: result.country });
          setStatus('not_found');
        }
      } catch (e) {
        fail(e);
      }
    },
    [userId],
  );

  /**
   * Remember the product under its barcode, then ask for the amount. Logging never waits on remembering.
   * Saving leaves the add flow like closeForm does: the not-found card is cleared, so cancelling the
   * amount dialog lands on a clean search screen instead of offering to add the product again (a failed
   * save is already reported above the amount dialog).
   */
  const saveProduct = async (product: FoodProduct) => {
    setSaving(true);
    let saved = true;
    try {
      await saveCustomProduct(userId, product);
    } catch {
      saved = false;
    }
    setSaving(false);
    setSaveFailed(!saved);
    setForm(null);
    setNotFound(null);
    setStatus('idle');
    setSelected(product);
  };

  const closeForm = () => {
    setForm(null);
    setNotFound(null);
    setStatus('idle');
  };

  const confirm = (grams: number, servings: number | null) => {
    if (!selected) return;
    logFood.mutate(
      { product: selected, grams, servings, meal, epochDay, summary: me.summary },
      { onSuccess: () => navigate('/me/day') },
    );
  };

  return (
    <>
      <h1>{t('add.title', { meal: t(`meal.${meal}`) })}</h1>

      {selected ? (
        <>
          {saveFailed && <p className="error" style={{ fontSize: '0.9rem' }}>{t('add.save_failed')}</p>}
          <AmountDialog
            product={selected}
            onConfirm={confirm}
            onCancel={() => {
              setSelected(null);
              setSaveFailed(false);
            }}
          />
        </>
      ) : form ? (
        <CustomProductForm
          key={form.barcode}
          barcode={form.barcode}
          country={countryOf(form.barcode)}
          prefill={form.prefill}
          saving={saving}
          onSave={(product) => void saveProduct(product)}
          onCancel={closeForm}
        />
      ) : (
        <>
          <form className="row" onSubmit={search}>
            <input type="text" value={query} placeholder={t('add.search')} onChange={(e) => setQuery(e.target.value)} aria-label={t('add.search')} />
            <button type="submit" disabled={status === 'searching'}>{t('add.go')}</button>
          </form>
          <div className="row" style={{ marginTop: 8 }}>
            <button className="ghost" onClick={() => setScanning((s) => !s)} style={{ paddingLeft: 0 }}>{t('add.scan')}</button>
          </div>
          {scanning && (
            <Suspense fallback={<p className="muted">…</p>}>
              <BarcodeScanner onCode={onCode} onClose={() => setScanning(false)} />
            </Suspense>
          )}
          <form
            className="row"
            style={{ marginTop: 8 }}
            onSubmit={(e) => {
              e.preventDefault();
              if (barcode.replace(/\D/g, '').length >= 8) void onCode(barcode);
            }}
          >
            <input type="text" inputMode="numeric" value={barcode} placeholder={t('add.barcode_manual')} onChange={(e) => setBarcode(e.target.value)} aria-label={t('add.barcode_manual')} />
            <button type="submit" className="ghost" disabled={status === 'searching' || barcode.replace(/\D/g, '').length < 8}>{t('add.barcode_go')}</button>
          </form>

          {status === 'short' && <p className="error">{t('add.min_chars')}</p>}
          {status === 'searching' && <p className="muted">{t('loading')}</p>}
          {status === 'offline' && (
            <p className="error">
              {t('add.offline')}
              {detail && <span className="muted" style={{ display: 'block', fontSize: '0.8rem' }}>{detail}</span>}
            </p>
          )}
          {status === 'not_found' && notFound && isStorableBarcode(notFound.barcode) && (
            <div className="card">
              <p style={{ marginTop: 0 }}>{t(`add.not_found.${notFound.country ?? 'other'}`)}</p>
              <p className="muted" style={{ fontSize: '0.85rem' }}>{t(`add.barcode_line.${notFound.country ?? 'other'}`, { code: notFound.barcode })}</p>
              <button onClick={() => setForm({ barcode: notFound.barcode, prefill: null })}>{t('add.add_product')}</button>
            </div>
          )}
          {/* A code no barcode table can hold (a typing slip, say): nothing to add it under. */}
          {status === 'not_found' && notFound && !isStorableBarcode(notFound.barcode) && <p className="error">{t('add.scan_not_found')}</p>}
          {results && results.length === 0 && status === 'idle' && <p className="muted">{t('add.none')}</p>}

          {results?.map((p) => (
            <button key={p.id} className="card row" style={{ width: '100%', textAlign: 'left', background: 'var(--surface)', color: 'var(--text)', borderRadius: 'var(--radius)' }} onClick={() => setSelected(p)}>
              <span className="stat">
                <strong>{p.name}</strong>
                {p.brand && <span className="muted" style={{ fontSize: '0.85rem' }}>{p.brand}</span>}
              </span>
              <span className="muted" style={{ whiteSpace: 'nowrap' }}>{Math.round(p.kcalPer100g)} {t('unit.kcal')}/100 {p.liquid ? t('unit.ml') : t('unit.g')}</span>
            </button>
          ))}
        </>
      )}
    </>
  );
}
