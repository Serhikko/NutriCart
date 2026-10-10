import { lazy, Suspense, useCallback, useContext, useEffect, useRef, useState, type ComponentProps, type CSSProperties, type FormEvent, type PointerEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { QueryClientContext } from '@tanstack/react-query';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useMyTargets } from '../lib/myTargets';
import { useLogFood } from '../lib/tracker';
import { useDay } from '../lib/queries';
import { OffError, lookupBarcode, searchProducts } from '../lib/openFoodFacts';
import { saveCustomProduct, savedProductFor, searchCustomProducts } from '../lib/customProducts';
import { shopsSource } from '../lib/zakaz';
import { sumKcal, type MealSlot } from '../lib/diary';
import { longDate } from '../lib/dayView';
import { prefersReducedMotion, setNavDirection, useMediaQuery } from '../lib/motion';
import type { FoodProduct, ProductPrefill } from '../domain/openFoodFacts';
import { forGrams } from '../domain/food';
import { countryOf, type BarcodeCountry } from '../domain/barcodeOrigin';
import { isStorableBarcode } from '../domain/customProduct';
import { lookupNotice, UNANSWERED_NOTICES, type LookupNotice } from '../domain/zakaz';
import { AmountDialog } from '../components/AmountDialog';
import { CustomProductForm } from '../components/CustomProductForm';
import { ResultRow } from '../components/ResultRow';
import { LargeTitle } from '../components/ui/LargeTitle';
import { Icon } from '../components/ui/Icon';
import { Button } from '../components/ui/Button';
import { Notice } from '../components/ui/Notice';
import { EmptyState } from '../components/ui/EmptyState';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { Skeleton } from '../components/ui/Skeleton';
import { Sheet, DOCK_QUERY } from '../components/ui/Sheet';
import { useToast } from '../components/ui/Toast';

// The barcode library is ~300 kB, so it only loads when someone taps Scan.
const BarcodeScanner = lazy(() => import('../components/BarcodeScanner').then((m) => ({ default: m.BarcodeScanner })));

/** Entrance delay (ms) for the .a-* classes, as a per-element custom property. */
const delay = (ms: number) => ({ '--d': ms }) as CSSProperties;
/** The toast arrives once the day's ring has started its re-sweep (A5). */
const TOAST_AFTER_MS = 520;
const SKELETON_ROWS = 5;

type MyTargets = ReturnType<typeof useMyTargets>;

/**
 * The amount sheet with what the day already holds, for the preview ring and
 * "N kcal left after this". The day comes from the same query as My day (so
 * it is usually cached), and the target follows My day's rule: a phone
 * account's published target wins, since only the phone knows the activity.
 */
function AmountForDay({ me, epochDay, ...props }: ComponentProps<typeof AmountDialog> & { me: MyTargets; epochDay: number }) {
  const day = useDay(me.userId ?? '', epochDay);
  const published = day.data?.summary ?? null;
  const target = me.primaryClient === 'phone' && published ? published.target_kcal : (me.targets?.kcal ?? me.summary?.targetKcal ?? null);
  const eaten = day.data ? sumKcal(day.data.entries) : null;
  // While "Add" saves, the day refetches with the new entry already in it. The sheet keeps the
  // total it showed when Add was pressed, so as it leaves it does not count this food twice
  // ("130 kcal left after this" turning into "32 kcal over after this").
  const [held, setHeld] = useState(eaten);
  if (!props.busy && held !== eaten) setHeld(eaten);
  return <AmountDialog {...props} eatenKcal={props.busy ? held : eaten} targetKcal={target} />;
}

/**
 * Search Open Food Facts or scan a barcode, then choose an amount: the phone's
 * FoodSearchScreen. A scan asks Open Food Facts, then, for codes they may
 * have, the Ukrainian shops (zakaz.ua). A product nobody has, or one known
 * without all of its nutrition, can be added once under its barcode
 * (custom_products); the next scan of that code finds it, and name search
 * lists it first. When a source did not answer, the message says so (and
 * to try again later) rather than calling the product unknown; like the
 * phone, the only action it offers is to add the product.
 *
 * The page stays put under everything that opens from it: the amount, the
 * new-product form and the scanner are sheets (on a phone the page recedes
 * behind them), and from 1240 px the amount docks beside the results as an
 * inspector, so several foods can be logged without closing anything. After
 * Add the diary comes back with the new entry (justAdded) and a toast.
 */
export function AddFood() {
  const { epochDay: dayParam = '', slot = 'LUNCH' } = useParams();
  const epochDay = Number(dayParam);
  const meal = slot as MealSlot;
  const { t, tp, tag } = useI18n();
  const navigate = useNavigate();
  const toast = useToast();
  const me = useMyTargets(epochDay);
  const logFood = useLogFood(me.userId);
  // Rendered on its own (tests) there is no query cache to read the day from: the sheet then shows the portion alone.
  const hasQueryClient = useContext(QueryClientContext) !== undefined;
  const docked = useMediaQuery(DOCK_QUERY, false);
  // Read at scan time, not captured: the token is refreshed hourly, and onCode must stay stable for the camera.
  const { session } = useSession();
  const accessToken = useRef<string | null>(null);
  useEffect(() => {
    accessToken.current = session?.access_token ?? null;
  }, [session]);

  const [query, setQuery] = useState('');
  const searchRef = useRef<HTMLInputElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const scanRef = useRef<HTMLButtonElement>(null);
  const barcodeRef = useRef<HTMLInputElement>(null);
  /** How the new-product form last closed: Save hands focus on to the amount, Cancel back to the barcode row. */
  const formOutcome = useRef<'saved' | 'closed'>('closed');
  const [results, setResults] = useState<FoodProduct[] | null>(null);
  /** Bumped by every name search, so each new set of results plays its cascade (S1). */
  const [resultSet, setResultSet] = useState(0);
  const [status, setStatus] = useState<'idle' | 'searching' | 'short' | 'offline' | 'not_found'>('idle');
  /** What the current 'searching' is: a name search shows skeleton rows, a barcode a quiet line. */
  const [lookingUp, setLookingUp] = useState<'name' | 'barcode'>('name');
  const [detail, setDetail] = useState<string | null>(null);
  const fail = (e: unknown) => {
    setDetail(e instanceof OffError ? e.message : e instanceof Error ? e.message : String(e));
    setStatus('offline');
  };
  const [scanning, setScanning] = useState(false);
  const [barcode, setBarcode] = useState('');
  const [selected, setSelected] = useState<FoodProduct | null>(null);
  /** The last scan found nothing: its digits, their GS1 origin and what the sources said, for the message. */
  const [notFound, setNotFound] = useState<{ barcode: string; country: BarcodeCountry | null; notice: LookupNotice } | null>(null);
  /** The add-this-product form, empty or prefilled from what Open Food Facts or a shop knows. */
  const [form, setForm] = useState<{ barcode: string; prefill: ProductPrefill | null; from: 'off' | 'shops' | null } | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);
  /** The entry is on its way to the diary; `failed` when it did not get there. */
  const [logging, setLogging] = useState<'idle' | 'busy' | 'failed'>('idle');
  const userId = me.userId;
  const barcodeDigits = barcode.replace(/\D/g, '').length;

  const search = async (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    if (q.length < 2) return setStatus('short');
    setLookingUp('name');
    setStatus('searching');
    setDetail(null);
    setNotFound(null);
    // On a touch screen the keyboard steps aside so the results can be seen; with a mouse the caret stays.
    if (typeof window.matchMedia === 'function' && window.matchMedia('(hover: none)').matches) searchRef.current?.blur();
    // The user's own products first; that lookup never fails, it is just empty.
    const [own, off] = await Promise.allSettled([searchCustomProducts(userId, q), searchProducts(q)]);
    const mine = own.status === 'fulfilled' ? own.value : [];
    setResultSet((n) => n + 1);
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
      setLookingUp('barcode');
      setStatus('searching');
      setDetail(null);
      setNotFound(null);
      try {
        const result = await lookupBarcode(code, {
          saved: (candidates) => savedProductFor(userId, candidates),
          shops: shopsSource(accessToken.current),
        });
        if (result.kind === 'found') {
          setSelected(result.product);
          setSaveFailed(false);
          setLogging('idle');
          setStatus('idle');
        } else if (result.kind === 'incomplete') {
          setForm({ barcode: result.prefill.barcode, prefill: result.prefill, from: result.from });
          setStatus('idle');
        } else {
          setNotFound({ barcode: result.barcode, country: result.country, notice: lookupNotice(result.country, result.offUnavailable, result.shops) });
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
    formOutcome.current = 'saved';
    setForm(null);
    setNotFound(null);
    setStatus('idle');
    setSelected(product);
  };

  const closeForm = () => {
    formOutcome.current = 'closed';
    setForm(null);
    setNotFound(null);
    setStatus('idle');
  };

  const choose = (product: FoodProduct) => {
    setSelected(product);
    setSaveFailed(false);
    setLogging('idle');
  };

  const closeAmount = () => {
    setSelected(null);
    setSaveFailed(false);
    setLogging('idle');
  };

  /**
   * Where focus goes when a sheet has closed and the control that opened it is gone ("Add this
   * product" leaves with the not-found card, Save with the form). After Save the amount comes next:
   * the docked inspector, which could not take focus while the form still held the page. Otherwise
   * the barcode row, where that flow began; on a touch screen its neighbour, the Scan button, since a
   * focused field would raise the keyboard.
   */
  const focusFallback = useCallback((from: 'form' | 'amount'): HTMLElement | null => {
    if (from === 'form' && formOutcome.current === 'saved') {
      const inspector = gridRef.current?.querySelector<HTMLElement>('dialog.is-docked[open]:not(.is-leaving)');
      if (inspector) return inspector;
    }
    const touch = typeof window.matchMedia === 'function' && window.matchMedia('(hover: none)').matches;
    return touch ? scanRef.current : barcodeRef.current;
  }, []);

  const confirm = (grams: number, servings: number | null) => {
    if (!selected || logging === 'busy') return;
    const product = selected;
    const kcal = forGrams(product.kcalPer100g, product.proteinPer100g, product.fatPer100g, product.carbsPer100g, grams).kcal;
    setLogging('busy');
    logFood.mutate(
      { product, grams, servings, meal, epochDay, summary: me.summary },
      {
        onSuccess: () => {
          const text = t('me.added', { meal: t(`meal.${meal}`), kcal: new Intl.NumberFormat(tag, { maximumFractionDigits: 0 }).format(kcal) });
          // The toast lives in the shell, so it outlives this page and lands on the diary.
          setTimeout(() => toast({ text }), prefersReducedMotion() ? 0 : TOAST_AFTER_MS);
          // Back to the diary, which plays its "back from Add" moment with this entry.
          setNavDirection('back');
          navigate('/me/day', { state: { justAdded: { epochDay, slot: meal, name: product.name, kcal } }, viewTransition: true });
        },
        onError: () => setLogging('failed'),
      },
    );
  };

  const mealName = t(`meal.${meal}`);
  const searching = status === 'searching';
  const showResults = !(searching && lookingUp === 'name') && results !== null && results.length > 0;
  const amountNotice =
    logging === 'failed' ? (
      <Notice tone="error" title={t('error.generic')} />
    ) : saveFailed ? (
      <Notice tone="warning" title={t('add.save_failed')} />
    ) : null;
  const amountProps = {
    onConfirm: confirm,
    onCancel: closeAmount,
    notice: amountNotice,
    busy: logging === 'busy',
    // The phone's sheet hands its ring to the day's hero ring (A1); the docked inspector stays where it is.
    shareRing: logging === 'busy' && !docked,
  };

  return (
    <>
      <LargeTitle
        back={{ to: '/me/day', label: t('me.title') }}
        eyebrow={Number.isFinite(epochDay) && dayParam !== '' ? `${mealName} · ${longDate(epochDay, tag)}` : mealName}
        title={t('add.title', { meal: mealName })}
      />
      <OfflineBanner />

      <div className="add-grid" ref={gridRef}>
        <div className="add-main">
          <form className="search-row a-fade-up" style={delay(70)} role="search" onSubmit={search}>
            <div className="search" onPointerDown={focusOwnInput}>
              <Icon name="search" size="sm" />
              <input
                ref={searchRef}
                type="text"
                value={query}
                placeholder={t('add.search')}
                onChange={(e) => setQuery(e.target.value)}
                aria-label={t('add.search')}
                enterKeyHint="search"
                autoComplete="off"
                spellCheck={false}
              />
              <button type="submit" className="go" disabled={searching}>
                {t('add.go')}
              </button>
            </div>
            <button ref={scanRef} type="button" className="scan" aria-label={t('add.scan')} aria-haspopup="dialog" onClick={() => setScanning(true)}>
              <Icon name="scan" />
            </button>
          </form>
          {status === 'short' && (
            <p className="inline-warn" role="alert">
              <Icon name="warning" size="xs" />
              <span>{t('add.min_chars')}</span>
            </p>
          )}

          <form
            className="code-row a-fade-up"
            style={delay(90)}
            onSubmit={(e) => {
              e.preventDefault();
              if (barcodeDigits >= 8) void onCode(barcode);
            }}
          >
            <div className="field code-field">
              <input
                ref={barcodeRef}
                type="text"
                inputMode="numeric"
                value={barcode}
                placeholder={t('add.barcode_manual')}
                onChange={(e) => setBarcode(e.target.value)}
                aria-label={t('add.barcode_manual')}
                autoComplete="off"
                enterKeyHint="go"
              />
            </div>
            <Button type="submit" variant="fill" disabled={searching || barcodeDigits < 8}>
              {t('add.barcode_go')}
            </Button>
          </form>

          <div className="add-status">
            {searching && lookingUp === 'barcode' && (
              <div className="looking-up" role="status">
                <Skeleton variant="line" width="46%" />
                <span className="sr">{t('loading')}</span>
              </div>
            )}
            {status === 'offline' && <Notice tone="error" icon="offline" title={t('add.offline')} detail={detail} className="a-rise" />}
            {/* Hidden while the form or a modal amount sheet covers it; beside the docked inspector it shows, or a
                lookup made while a product is open there would seem to do nothing. */}
            {status === 'not_found' && notFound && isStorableBarcode(notFound.barcode) && !form && !(selected && !docked) && (
              <section className="card not-found a-rise" aria-labelledby="not-found-text">
                <span className="nf-icon" aria-hidden="true">
                  <Icon name="warning" />
                </span>
                <div className="nf-body">
                  <p className="nf-text" id="not-found-text">
                    {t(`add.notice.${notFound.notice}`)}
                  </p>
                  <p className="nf-code">{t(`add.barcode_line.${notFound.country ?? 'other'}`, { code: notFound.barcode })}</p>
                  <Button
                    type="button"
                    variant="ink"
                    icon="plus"
                    aria-haspopup="dialog"
                    onClick={() => setForm({ barcode: notFound.barcode, prefill: null, from: null })}
                  >
                    {t('add.add_product')}
                  </Button>
                </div>
              </section>
            )}
            {/* A code no barcode table can hold (a typing slip, say): nothing to add it under, but a source that did not answer is still said. */}
            {status === 'not_found' && notFound && !isStorableBarcode(notFound.barcode) && (
              <Notice
                tone={UNANSWERED_NOTICES.includes(notFound.notice) ? 'warning' : 'error'}
                title={t(UNANSWERED_NOTICES.includes(notFound.notice) ? `add.notice.${notFound.notice}` : 'add.scan_not_found')}
                className="a-rise"
              />
            )}
          </div>

          {searching && lookingUp === 'name' && (
            <div className="results-block" aria-busy="true">
              <div className="results-meta">
                <Skeleton variant="line" width={72} />
              </div>
              <div className="card results">
                {Array.from({ length: SKELETON_ROWS }, (_, i) => (
                  <Skeleton key={i} variant="row" label={i === 0 ? t('loading') : undefined} />
                ))}
              </div>
            </div>
          )}

          {showResults && (
            <div className="results-block">
              <p className="results-meta">
                <span>{tp('add.results', results!.length)}</span>
                <span>{t('add.per100_hint')}</span>
              </p>
              <ul className="card results" key={resultSet}>
                {results!.map((p, i) => (
                  <li key={p.id}>
                    <ResultRow product={p} index={i} selected={selected?.id === p.id} onSelect={choose} />
                  </li>
                ))}
              </ul>
            </div>
          )}

          {results !== null && results.length === 0 && status === 'idle' && (
            <EmptyState icon="search" title={t('add.none')} titleAs="p" className="add-empty" />
          )}
          {results === null && (status === 'idle' || status === 'short') && (
            <EmptyState icon="search" title={t('add.empty_hint')} titleAs="p" className="add-empty" />
          )}

          {/* The count, read out once a search lands. */}
          <p className="sr" aria-live="polite">
            {showResults ? tp('add.results', results!.length) : ''}
          </p>
        </div>

        {/* The inspector's column waits for a choice instead of standing empty. */}
        {docked && selected === null && (
          <div className="card inspector-idle">
            <EmptyState icon="bowl" title={t('add.inspector_hint')} titleAs="p" />
          </div>
        )}

        <Sheet open={selected !== null} onClose={closeAmount} label={selected?.name ?? ''} dock>
          {selected && (
            <>
              {hasQueryClient ? (
                <AmountForDay me={me} epochDay={epochDay} product={selected} {...amountProps} />
              ) : (
                <AmountDialog product={selected} {...amountProps} />
              )}
              <FocusReturn target={() => focusFallback('amount')} />
            </>
          )}
        </Sheet>
      </div>

      <Sheet open={form !== null} onClose={closeForm} label={t('cf.title')}>
        {form && (
          <>
            <CustomProductForm
              key={form.barcode}
              barcode={form.barcode}
              country={countryOf(form.barcode)}
              prefill={form.prefill}
              prefillFrom={form.from}
              saving={saving}
              onSave={(product) => void saveProduct(product)}
              onCancel={closeForm}
            />
            <FocusReturn target={() => focusFallback('form')} />
          </>
        )}
      </Sheet>

      <Sheet open={scanning} onClose={() => setScanning(false)} label={t('add.scan')} className="scan-sheet">
        {scanning && (
          <Suspense
            fallback={
              <div className="scanner">
                <Skeleton variant="block" height={240} label={t('loading')} />
              </div>
            }
          >
            <BarcodeScanner onCode={onCode} onClose={() => setScanning(false)} />
          </Suspense>
        )}
      </Sheet>
    </>
  );
}

/**
 * Placed in a sheet's content, so it unmounts with the dialog once the exit has played. A sheet
 * gives focus back to the control that opened it; when that control is gone, focus would drop to
 * <body> and a screen reader would start again from the top of the page, so it goes to `target`.
 * This runs as a passive cleanup: after the sheet has closed its dialog and the page is no longer
 * inert. It never takes focus from something that already holds it (the opener, or the next sheet).
 */
function FocusReturn({ target }: { target: () => HTMLElement | null }) {
  const latest = useRef(target);
  latest.current = target;
  useEffect(
    () => () => {
      const active = document.activeElement;
      if (active && active !== document.body) return;
      const element = latest.current();
      if (element?.isConnected && !element.closest('[inert]')) element.focus({ preventScroll: true });
    },
    [],
  );
  return null;
}

/** A press anywhere on the search capsule (not on its button) puts the caret in the field. */
function focusOwnInput(event: PointerEvent<HTMLDivElement>) {
  const target = event.target as HTMLElement;
  if (target.closest('button, input')) return;
  event.preventDefault();
  event.currentTarget.querySelector('input')?.focus();
}
