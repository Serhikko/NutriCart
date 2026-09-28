import { lazy, Suspense, useCallback, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useMyTargets } from '../lib/myTargets';
import { useLogFood } from '../lib/tracker';
import { productByBarcode, searchProducts } from '../lib/openFoodFacts';
import type { FoodProduct } from '../domain/openFoodFacts';
import type { MealSlot } from '../lib/diary';
import { AmountDialog } from '../components/AmountDialog';

// The barcode library is ~300 kB, so it only loads when someone taps Scan.
const BarcodeScanner = lazy(() => import('../components/BarcodeScanner').then((m) => ({ default: m.BarcodeScanner })));

/** Search Open Food Facts or scan a barcode, then choose an amount: the phone's FoodSearchScreen. */
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
  const [scanning, setScanning] = useState(false);
  const [selected, setSelected] = useState<FoodProduct | null>(null);

  const search = async (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    if (q.length < 2) return setStatus('short');
    setStatus('searching');
    try {
      setResults(await searchProducts(q));
      setStatus('idle');
    } catch {
      setStatus('offline');
    }
  };

  const onCode = useCallback(async (code: string) => {
    setScanning(false);
    setStatus('searching');
    try {
      const product = await productByBarcode(code);
      if (product) {
        setSelected(product);
        setStatus('idle');
      } else {
        setStatus('not_found');
      }
    } catch {
      setStatus('offline');
    }
  }, []);

  const confirm = (grams: number, servings: number | null) => {
    if (!selected) return;
    logFood.mutate(
      { product: selected, grams, servings, meal, epochDay, targetKcal: me.targets?.kcal ?? null },
      { onSuccess: () => navigate('/me/day') },
    );
  };

  return (
    <>
      <h1>{t('add.title', { meal: t(`meal.${meal}`) })}</h1>

      {selected ? (
        <AmountDialog product={selected} onConfirm={confirm} onCancel={() => setSelected(null)} />
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

          {status === 'short' && <p className="error">{t('add.min_chars')}</p>}
          {status === 'searching' && <p className="muted">{t('loading')}</p>}
          {status === 'offline' && <p className="error">{t('add.offline')}</p>}
          {status === 'not_found' && <p className="error">{t('add.scan_not_found')}</p>}
          {results && results.length === 0 && status === 'idle' && <p className="muted">{t('add.none')}</p>}

          {results?.map((p) => (
            <button key={p.id} className="card row" style={{ width: '100%', textAlign: 'left', background: 'var(--surface)', color: 'var(--text)', borderRadius: 'var(--radius)' }} onClick={() => setSelected(p)}>
              <span className="stat">
                <strong>{p.name}</strong>
                {p.brand && <span className="muted" style={{ fontSize: '0.85rem' }}>{p.brand}</span>}
              </span>
              <span className="muted" style={{ whiteSpace: 'nowrap' }}>{Math.round(p.kcalPer100g)} {t('unit.kcal')}/100 {t('unit.g')}</span>
            </button>
          ))}
        </>
      )}
    </>
  );
}
