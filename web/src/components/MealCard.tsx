import { useEffect, useId, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { setNavDirection } from '../lib/motion';
import { formatTime } from '../lib/dates';
import { sumKcal, type FoodLogEntry, type MealSlot } from '../lib/diary';
import { Digits } from './ui/Digits';
import { Icon, type IconName } from './ui/Icon';

const MEAL_ICON: Record<MealSlot, IconName> = { BREAKFAST: 'sunrise', LUNCH: 'sun', DINNER: 'moon', SNACK: 'cookie' };

/**
 * The diary rows that arrived while the day was on screen (a live insert from
 * the phone, the row just added, an undone delete), so they can unfold and
 * glow once. The rows of a day's first load are not fresh. `mark` makes a row
 * fresh by hand (an undo, or the row Add food reported back).
 */
export function useFreshEntries(entries: readonly { id: string }[] | null | undefined, day: number) {
  const seen = useRef<{ day: number; ids: Set<string> } | null>(null);
  const fresh = useRef(new Set<string>());
  if (entries) {
    if (!seen.current || seen.current.day !== day) {
      seen.current = { day, ids: new Set(entries.map((e) => e.id)) };
    } else {
      for (const e of entries) {
        if (seen.current.ids.has(e.id)) continue;
        seen.current.ids.add(e.id);
        fresh.current.add(e.id);
      }
    }
  }
  const mark = useRef((id: string) => {
    fresh.current.add(id);
  }).current;
  return [fresh.current as ReadonlySet<string>, mark] as const;
}

/** How far a row slides to show Delete, and how far a swipe must go to leave it open. */
const SWIPE_OPEN = 88;
const SWIPE_COMMIT = 44;

interface MealCardProps {
  slot: MealSlot;
  /** The meal's live entries, in logging order. */
  entries: FoodLogEntry[];
  /** Your own diary: rows open the entry's actions, Edit shows the delete discs, an "Add food" row ends the card. */
  editable?: boolean;
  /** Edit mode (the section's Edit / Done): a red minus disc slides in before every row. */
  editing?: boolean;
  /** The day the "Add food" row adds to. */
  epochDay?: number;
  /** A disc tap or a swipe's Delete. */
  onDelete?: (entry: FoodLogEntry) => void;
  /** A tap on the row: the entry's action sheet (Edit amount, Delete). */
  onOpen?: (entry: FoodLogEntry) => void;
  /** Rows that just arrived (back from Add, a live insert, an undo): they unfold and glow once. */
  fresh?: ReadonlySet<string>;
  /** Rows being deleted: they fold away. */
  leaving?: ReadonlySet<string>;
  /** The meal's total before this mount (back from Add): it hands off to the new total. */
  totalFrom?: number;
  /** Position in the list, for the entrance stagger. */
  index?: number;
}

/**
 * One meal: a squircle tile, the meal's name with "2 items · from 08:12" and
 * its total, then the entries as inset rows (name on up to two lines, amount
 * and time under it, kcal on the right). The same card serves your own diary
 * (rows are buttons, Edit mode, swipe to delete, an "Add food" row) and a
 * partner's (read only; an empty meal is left out).
 */
export function MealCard({
  slot,
  entries,
  editable = false,
  editing = false,
  epochDay,
  onDelete,
  onOpen,
  fresh,
  leaving,
  totalFrom,
  index = 0,
}: MealCardProps) {
  const { t, tp, tag } = useI18n();
  const id = useId();
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  // A swipe opens one row at a time.
  const [swiped, setSwiped] = useState<string | null>(null);
  if (!editable && entries.length === 0) return null;

  const counted = entries.filter((e) => !leaving?.has(e.id));
  const total = Math.round(sumKcal(counted));
  const sub = counted.length > 0 ? tp('meal.items', counted.length, { time: formatTime(counted[0].logged_at, tag) }) : t('meal.empty');

  return (
    <section className={editing ? 'card meal a-rise editing' : 'card meal a-rise'} style={{ '--d': 440 + index * 60 } as CSSProperties} aria-labelledby={id}>
      <header className="meal-head">
        <span className={`mtile ${slot}`} aria-hidden="true">
          <Icon name={MEAL_ICON[slot]} size="sm" />
        </span>
        <div className="meal-t">
          <h3 id={id}>{t(`meal.${slot}`)}</h3>
          <span className="sub">{sub}</span>
        </div>
        <div className="meal-kcal num">
          <Digits value={total} from={totalFrom} delay={360} />
          <span className="unit">{t('unit.kcal')}</span>
        </div>
      </header>
      {entries.length > 0 && (
        <ul className="entries">
          {entries.map((e) => (
            <EntryRow
              key={e.id}
              entry={e}
              editable={editable}
              editing={editing}
              fresh={fresh?.has(e.id) ?? false}
              leaving={leaving?.has(e.id) ?? false}
              swiped={swiped === e.id}
              onSwipe={(open) => setSwiped(open ? e.id : null)}
              onDelete={onDelete}
              onOpen={onOpen}
              meta={`${e.grams !== null ? `${nf.format(Math.round(e.grams))} ${t('unit.g')} · ` : ''}${formatTime(e.logged_at, tag)}`}
              kcal={nf.format(Math.round(e.kcal))}
            />
          ))}
        </ul>
      )}
      {editable && epochDay != null && (
        <Link className="add-row" to={`/me/add/${epochDay}/${slot}`} viewTransition onClick={() => setNavDirection('forward')}>
          <span className="pd" aria-hidden="true">
            <span>
              <Icon name="plus" size="xs" />
            </span>
          </span>
          {t('me.add')}
        </Link>
      )}
    </section>
  );
}

interface EntryRowProps {
  entry: FoodLogEntry;
  editable: boolean;
  editing: boolean;
  fresh: boolean;
  leaving: boolean;
  swiped: boolean;
  onSwipe: (open: boolean) => void;
  onDelete?: (entry: FoodLogEntry) => void;
  onOpen?: (entry: FoodLogEntry) => void;
  meta: string;
  kcal: string;
}

interface Drag {
  id: number;
  x0: number;
  y0: number;
  base: number;
  active: boolean;
  /** Where the row is now: the release reads it here, not from a render that may not have happened yet. */
  at: number;
}

/**
 * One diary row. Its wrapper is a one-row grid, so it can unfold (a new row)
 * or fold away (a delete) by animating grid-template-rows, the only layout
 * animation on the page. On touch, a swipe to the left uncovers Delete.
 */
function EntryRow({ entry, editable, editing, fresh, leaving, swiped, onSwipe, onDelete, onOpen, meta, kcal }: EntryRowProps) {
  const { t } = useI18n();
  const rowRef = useRef<HTMLDivElement>(null);
  const drag = useRef<Drag | null>(null);
  const [offset, setOffset] = useState<number | null>(null);
  // A swipe ends in a click on the row; that click must not open the action sheet.
  const swallowClick = useRef(false);

  // An open row closes when anything else on the page is touched.
  useEffect(() => {
    if (!swiped) return;
    const close = (event: PointerEvent) => {
      if (!rowRef.current?.contains(event.target as Node)) onSwipe(false);
    };
    document.addEventListener('pointerdown', close, true);
    return () => document.removeEventListener('pointerdown', close, true);
  }, [swiped, onSwipe]);

  const canSwipe = editable && !editing && onDelete != null;

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    // A swipe that ended without a click must not swallow the next tap.
    swallowClick.current = false;
    if (!canSwipe || event.pointerType !== 'touch' || !event.isPrimary) return;
    const base = swiped ? -SWIPE_OPEN : 0;
    drag.current = { id: event.pointerId, x0: event.clientX, y0: event.clientY, base, active: false, at: base };
  };
  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    if (!d || event.pointerId !== d.id) return;
    const dx = event.clientX - d.x0;
    const dy = event.clientY - d.y0;
    if (!d.active) {
      if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) {
        drag.current = null; // a scroll
        return;
      }
      if (Math.abs(dx) < 10) return;
      d.active = true;
      try {
        rowRef.current?.setPointerCapture?.(event.pointerId);
      } catch {
        /* the pointer is already gone: the swipe still follows the moves it gets */
      }
    }
    d.at = Math.max(-SWIPE_OPEN - 24, Math.min(0, d.base + dx));
    setOffset(d.at);
  };
  const onPointerEnd = (event: ReactPointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    if (!d || event.pointerId !== d.id) return;
    drag.current = null;
    if (!d.active) return;
    swallowClick.current = true;
    onSwipe(d.at < -SWIPE_COMMIT);
    setOffset(null);
  };

  const shift = offset ?? (swiped ? -SWIPE_OPEN : 0);
  const style = { '--d': fresh ? 300 : 0, ...(shift !== 0 ? { '--sx': `${shift}px` } : {}) } as CSSProperties;

  const main = (
    <>
      <span className="e-main">
        <span className="e-name" title={entry.name}>
          {entry.name}
        </span>
        <span className="e-meta num">{meta}</span>
      </span>
      <span className="e-kcal num">
        {kcal}
        <span className="unit">{t('unit.kcal')}</span>
      </span>
    </>
  );

  return (
    <li
      className={['entry-wrap', fresh ? 'fresh a-unfold' : '', leaving ? 'leaving' : ''].filter(Boolean).join(' ')}
      style={style}
      inert={leaving}
      data-entry={entry.id}
    >
      <div className="entry-clip">
        <div
          ref={rowRef}
          className={['entry', offset !== null ? 'dragging' : '', shift !== 0 ? 'shifted' : ''].filter(Boolean).join(' ')}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerEnd}
          onPointerCancel={onPointerEnd}
        >
          {canSwipe && (
            <button
              type="button"
              className="swipe-del"
              tabIndex={-1}
              aria-hidden="true"
              onClick={() => {
                onSwipe(false);
                onDelete?.(entry);
              }}
            >
              {t('me.delete')}
            </button>
          )}
          <div className="entry-row">
            {editable && (
              <span className="del" inert={!editing}>
                <button type="button" className="del-btn" aria-label={t('me.delete_item', { name: entry.name })} onClick={() => onDelete?.(entry)}>
                  <span>
                    <Icon name="minus" size="xs" />
                  </span>
                </button>
              </span>
            )}
            {editable && onOpen ? (
              <button
                type="button"
                className="e-body"
                aria-haspopup="dialog"
                onClick={() => {
                  if (swallowClick.current) {
                    swallowClick.current = false;
                    return;
                  }
                  if (swiped) {
                    onSwipe(false);
                    return;
                  }
                  onOpen(entry);
                }}
              >
                {main}
              </button>
            ) : (
              <div className="e-body">{main}</div>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}
