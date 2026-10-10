import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { supabase } from '../lib/supabase';
import { useDay, useOwnerRealtime, useWeek } from '../lib/queries';
import { useMyTargets } from '../lib/myTargets';
import { useAddWater, useDeleteFood, useLogWeight, useUndoWater } from '../lib/tracker';
import { todayEpochDay } from '../lib/dates';
import { MEAL_SLOTS, groupByMeal, sumKcal, sumWater, type DaySummary, type FoodLogEntry } from '../lib/diary';
import { daySummaryPayload, type SummaryPolicy } from '../domain/summary';
import {
  entryAsProduct,
  entryAtGrams,
  entryCopy,
  findArrival,
  lastWaterAt,
  longDate,
  macroTotals,
  publishedAt,
  readArrival,
  shortDate,
  weekChange,
  weightOn,
  type Arrival,
  type EntryRow,
} from '../lib/dayView';
import { buildWeekRows, longWeekday, markerState, shortWeekday, weekStats } from '../lib/weekView';
import { DESKTOP_QUERY, prefersReducedMotion, setNavDirection, useMediaQuery, useRouteFirstOpen } from '../lib/motion';
import { guessMealSlot } from '../domain/habits';
import { LargeTitle } from '../components/ui/LargeTitle';
import { DayNav } from '../components/ui/DayNav';
import { Button, ButtonLink } from '../components/ui/Button';
import { CardHead } from '../components/ui/Card';
import { DayMarker } from '../components/ui/DayMarker';
import { EmptyState } from '../components/ui/EmptyState';
import { Icon } from '../components/ui/Icon';
import { Notice } from '../components/ui/Notice';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { Skeleton } from '../components/ui/Skeleton';
import { Sheet } from '../components/ui/Sheet';
import { AmountStepper } from '../components/ui/AmountStepper';
import { Digits } from '../components/ui/Digits';
import { useToast } from '../components/ui/Toast';
import { DayHero } from '../components/DayHero';
import { MacrosCard } from '../components/MacrosCard';
import { ActivityTiles } from '../components/ActivityTiles';
import { MealCard, useFreshEntries } from '../components/MealCard';
import { WaterCard } from '../components/WaterCard';
import { WeightCard } from '../components/WeightCard';

/** How long the row takes to fold away before it leaves the list. */
const FOLD_MS = 320;
/** The moment back from Add food is over by then: the ring has landed and the new row has stopped glowing. */
const ARRIVAL_MS = 3000;

const withId = (set: ReadonlySet<string>, id: string) => new Set(set).add(id);
const withoutId = (set: ReadonlySet<string>, id: string) => {
  const next = new Set(set);
  next.delete(id);
  return next;
};

/** A new diary row id, in the shape tracker.ts gives the website's own rows. */
const newEntryId = () => `web:f:${crypto.randomUUID()}`;

/**
 * Writes a diary row the page built itself (a deleted row back from Undo, a
 * row at a new amount), then the day's summary from the server's own rows, as
 * tracker.ts does after its writes, so a partner's ring follows.
 */
async function writeEntry(row: EntryRow, policy: SummaryPolicy): Promise<void> {
  const { error } = await supabase.from('food_log_entries').insert(row);
  if (error) throw error;
  try {
    const [entries, existing] = await Promise.all([
      supabase.from('food_log_entries').select('kcal').eq('owner_id', row.owner_id).eq('epoch_day', row.epoch_day).is('deleted_at', null),
      supabase.from('day_summaries').select('target_kcal').eq('owner_id', row.owner_id).eq('epoch_day', row.epoch_day).maybeSingle(),
    ]);
    if (entries.error || existing.error) return;
    const eaten = ((entries.data ?? []) as { kcal: number }[]).reduce((a, r) => a + r.kcal, 0);
    const payload = daySummaryPayload(policy, eaten, (existing.data as { target_kcal: number } | null) ?? null);
    if (payload) await supabase.from('day_summaries').upsert({ owner_id: row.owner_id, epoch_day: row.epoch_day, ...payload });
  } catch {
    // The row is written, which is what counts: failing here would invite a second copy. The
    // summary catches up with the next write.
  }
}

/* Focus when a diary row leaves the list (a delete, a new amount): never left on <body>. */

const rowItem = (id: string) => [...document.querySelectorAll<HTMLElement>('li[data-entry]')].find((li) => li.dataset.entry === id) ?? null;

/** The control that stands for a row: its minus disc in Edit mode, the row itself otherwise. */
const rowControl = (li: Element | null | undefined, disc: boolean) => li?.querySelector<HTMLElement>(disc ? '.del-btn' : 'button.e-body') ?? null;

/**
 * Where focus goes when row `id` leaves: the next row of its meal, else the one
 * before, else the meal's "Add food" row.
 */
function focusTargetNear(id: string, disc: boolean): HTMLElement | null {
  const li = rowItem(id);
  if (!li) return null;
  const rows = [...(li.parentElement?.children ?? [])].filter((r) => !r.classList.contains('leaving'));
  const at = rows.indexOf(li);
  return rowControl(rows[at + 1] ?? rows[at - 1], disc) ?? li.closest('.meal')?.querySelector<HTMLElement>('.add-row') ?? null;
}

const focusQuietly = (el: HTMLElement | null) => {
  if (el?.isConnected && !el.closest('[inert]')) el.focus({ preventScroll: true });
};

/**
 * Focuses what `target` returns once the entry sheet has gone (its exit takes a
 * moment, and the page is inert under it) and only if focus was lost with the
 * row that opened it, never taking it from somewhere the user has moved on to.
 */
function focusWhenSheetGone(target: () => HTMLElement | null) {
  const start = Date.now();
  const check = () => {
    if (document.querySelector('dialog.entry-sheet') && Date.now() - start < 2000) {
      setTimeout(check, 40);
      return;
    }
    const active = document.activeElement;
    if (!active || active === document.body) focusQuietly(target());
  };
  setTimeout(check, 0);
}

/**
 * The signed-in user's own diary: the phone's dashboard and Diary, on the web.
 * A large title with the date and the day navigator, the day's ring with what
 * is left, the macros, what the phone measured, the four meals (Edit mode,
 * swipe or disc to delete with Undo, a tap for Edit amount), water and weight.
 * Desktop: the hero and meals on the left, a sticky column with the activity
 * tiles, the week at a glance, water and weight on the right.
 *
 * Back from Add food (location.state.justAdded) the ring re-sweeps from the old
 * value, the numbers hand off, the new row unfolds with a fading glow; then the
 * state is cleared so a reload does not replay it.
 */
export function MyDay() {
  const { t, tag } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const session = useSession();
  const first = useRouteFirstOpen();
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const today = todayEpochDay();

  // Back from Add food: what was logged, read once when the page opens.
  const [arrival, setArrival] = useState<Arrival | null>(() => {
    const a = readArrival(location.state);
    return a && a.epochDay <= today ? a : null;
  });
  const [sharedRing, setSharedRing] = useState(arrival != null);
  const [epochDay, setEpochDay] = useState(() => arrival?.epochDay ?? today);

  const me = useMyTargets(epochDay);
  const userId = me.userId ?? '';
  const qc = useQueryClient();
  const day = useDay(userId, epochDay);
  // The day before is loaded once (see WarmDay), so the previous-day button lands at once.
  const [warmed, setWarmed] = useState<number | null>(null);
  const week = useWeek(userId, today);
  useOwnerRealtime(me.userId);
  const addWater = useAddWater(me.userId);
  const undoWater = useUndoWater(me.userId);
  const deleteFood = useDeleteFood(me.userId);
  const logWeight = useLogWeight(me.userId);

  // The moment back from Add plays once: the history entry forgets it (without scrolling the
  // page: the hero stays where it is), and the hero ring stops being a shared element.
  useEffect(() => {
    if (!arrival) return;
    const shared = setTimeout(() => setSharedRing(false), 1000);
    const done = setTimeout(() => {
      setArrival(null);
      navigate(location.pathname, { replace: true, state: null, preventScrollReset: true });
    }, ARRIVAL_MS);
    return () => {
      clearTimeout(shared);
      clearTimeout(done);
    };
    // Once, for the arrival the page opened with.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // A day that is still loading keeps the last one on screen (dimmed and inert), so the ring
  // and the numbers move from the previous day's values instead of blinking out. A day that
  // failed to load does not: it gets the error and its Retry.
  const lastShown = useRef<typeof day.data>(undefined);
  if (day.data) lastShown.current = day.data;
  const data = day.data ?? (day.isError ? undefined : lastShown.current);
  const stale = !day.data && data != null;

  // Rows deleted here leave the list at once (the server's copy follows with the next refetch).
  const [hidden, setHidden] = useState<ReadonlySet<string>>(() => new Set());
  const [leaving, setLeaving] = useState<ReadonlySet<string>>(() => new Set());
  // Rows this page wrote itself (an undone delete, a new amount), on screen at once and until
  // the server's copy arrives.
  const [written, setWritten] = useState<readonly EntryRow[]>([]);
  const [editing, setEditing] = useState(false);
  const [sheet, setSheet] = useState<{ entry: FoodLogEntry; mode: 'actions' | 'amount' } | null>(null);

  const onServer = new Set(data?.entries.map((e) => e.id));
  const live = data
    ? [
        ...data.entries.filter((e) => e.deleted_at === null && !hidden.has(e.id)),
        ...(stale ? [] : written.filter((e) => e.epoch_day === epochDay && !onServer.has(e.id) && !hidden.has(e.id))),
      ]
    : [];
  const counted = live.filter((e) => !leaving.has(e.id));
  const groups = groupByMeal(live);
  const eaten = sumKcal(counted);
  const [fresh, markFresh] = useFreshEntries(data && !stale ? live : null, epochDay);

  // The row Add food just logged, and the values on screen before it (taken once, when the hero appears).
  const arrived = arrival && data && !stale && arrival.epochDay === epochDay ? findArrival(live, arrival) : null;
  if (arrived) markFresh(arrived.id);
  const before = useRef<{ eaten: number | null; slot: string | null; meal: number } | null>(null);
  if (before.current === null && data && !stale) {
    before.current = arrived
      ? { eaten: eaten - arrived.kcal, slot: arrived.meal, meal: sumKcal(groups.get(arrived.meal) ?? []) - arrived.kcal }
      : { eaten: null, slot: null, meal: 0 };
  }

  // A phone account's target includes the day's activity, which only the
  // phone knows: its published row wins there. Eaten is always the live sum.
  const published = data?.summary ?? null;
  const targetKcal = me.targets?.kcal ?? null;
  const target = me.primaryClient === 'phone' && published ? published.target_kcal : targetKcal;
  const summary: DaySummary | null =
    target !== null
      ? {
          epoch_day: epochDay,
          target_kcal: target,
          eaten_kcal: Math.round(eaten),
          active_kcal: published?.active_kcal ?? null,
          steps: published?.steps ?? null,
          workout_kcal: published?.workout_kcal ?? 0,
        }
      : null;

  // The server's copies of written rows have arrived: the page's own can go.
  useEffect(() => {
    if (!day.data) return;
    const ids = new Set(day.data.entries.map((e) => e.id));
    setWritten((w) => (w.some((e) => ids.has(e.id)) ? w.filter((e) => !ids.has(e.id)) : w));
  }, [day.data]);

  // --- deleting, with Undo ----------------------------------------------------
  // A delete is written at once, as it always was: a reload or a closed tab during the
  // toast must not lose it. Undo then logs the row again (entryCopy: same food, meal and
  // time, a new id), so the phone takes it back too.
  const policyRef = useRef(me.summary);
  policyRef.current = me.summary;
  const tRef = useRef(t);
  tRef.current = t;
  /** Deletes on their way, or done, that Undo may still reverse. */
  const deletes = useRef(new Map<string, { entry: FoodLogEntry; done: Promise<boolean> }>());
  /** Rows on their way to the server: a delete of one waits for it. */
  const writes = useRef(new Map<string, Promise<void>>());
  const writeSettled = (id: string) => (writes.current.get(id) ?? Promise.resolve()).catch(() => undefined);

  const refetchDay = () => {
    void qc.invalidateQueries({ queryKey: ['day', userId] });
    void qc.invalidateQueries({ queryKey: ['week', userId] });
  };

  const restore = (id: string) => {
    const d = deletes.current.get(id);
    if (!d) return; // the delete failed, and the row is already back
    deletes.current.delete(id);
    const copy = entryCopy(d.entry, newEntryId());
    markFresh(copy.id);
    setLeaving((s) => withoutId(s, id));
    setHidden((s) => withId(s, id));
    setWritten((w) => [...w, copy]);
    const write = d.done.then(async (deleted) => {
      // The delete never happened, so the row itself is back (see remove): no copy needed.
      if (!deleted) {
        setWritten((w) => w.filter((e) => e.id !== copy.id));
        return;
      }
      await writeEntry(copy, policyRef.current);
      refetchDay();
    });
    writes.current.set(copy.id, write);
    write
      .catch(() => {
        setWritten((w) => w.filter((e) => e.id !== copy.id));
        toast({ text: tRef.current('error.generic'), icon: 'info' });
      })
      .finally(() => writes.current.delete(copy.id));
  };

  const remove = (entry: FoodLogEntry) => {
    if (deletes.current.has(entry.id)) return;
    // Focus on the row (its minus disc in Edit mode) moves to its neighbour before the row folds.
    if (rowItem(entry.id)?.contains(document.activeElement)) focusQuietly(focusTargetNear(entry.id, editing));
    const write = () => deleteFood.mutateAsync({ id: entry.id, epochDay: entry.epoch_day, summary: me.summary });
    // A row still on its way (Undo a moment ago) is deleted once it is there; any other at once.
    const done = (writes.current.has(entry.id) ? writeSettled(entry.id).then(write) : write()).then(
      () => true,
      () => false,
    );
    deletes.current.set(entry.id, { entry, done });
    void done.then((ok) => {
      if (ok) return;
      // It never reached the server: the row comes back, and the page says why (unless
      // Undo has already brought it back).
      const undone = deletes.current.get(entry.id)?.done !== done;
      deletes.current.delete(entry.id);
      setLeaving((s) => withoutId(s, entry.id));
      setHidden((s) => withoutId(s, entry.id));
      if (!undone) toast({ text: tRef.current('error.generic'), icon: 'info' });
    });
    setLeaving((s) => withId(s, entry.id));
    setTimeout(
      () => {
        setLeaving((s) => withoutId(s, entry.id));
        if (deletes.current.get(entry.id)?.done === done) setHidden((s) => withId(s, entry.id));
      },
      prefersReducedMotion() ? 0 : FOLD_MS,
    );
    toast({ text: t('me.deleted', { name: entry.name }), undo: () => restore(entry.id) });
  };

  // Nothing left to edit: Edit mode ends by itself.
  const anyEntries = live.length > 0;
  if (editing && data && !stale && !anyEntries) setEditing(false);

  // --- states -----------------------------------------------------------------
  const isToday = epochDay === today;
  const remaining = summary ? summary.target_kcal - summary.eaten_kcal : null;
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const compact =
    remaining === null
      ? t('me.title')
      : `${t('me.title')} · ${remaining >= 0 ? t('day.compact_left', { n: nf.format(remaining) }) : t('day.compact_over', { n: nf.format(-remaining) })}`;

  const header = (
    <LargeTitle
      eyebrow={longDate(epochDay, tag)}
      title={t('me.title')}
      compact={compact}
      actions={
        me.onboarded ? (
          <>
            <DayNav
              label={isToday ? t('day.today') : shortDate(epochDay, tag)}
              onPrev={() => setEpochDay((d) => d - 1)}
              onNext={() => setEpochDay((d) => Math.min(today, d + 1))}
              nextDisabled={epochDay >= today}
            />
            {desktop && (
              <ButtonLink
                variant="ink"
                icon="plus"
                to={`/me/add/${epochDay}/${guessMealSlot(new Date().getHours())}`}
                viewTransition
                onClick={() => setNavDirection('forward')}
              >
                {t('me.add')}
              </ButtonLink>
            )}
          </>
        ) : null
      }
    />
  );

  if (session.loading || me.loading || (me.onboarded && !data && !day.isError)) {
    return (
      <>
        {header}
        <DaySkeleton label={t('loading')} />
      </>
    );
  }

  if (!me.onboarded) {
    return (
      <>
        {header}
        <EmptyState
          icon="day"
          title={t('me.no_profile')}
          action={
            <ButtonLink variant="ink" to="/me/onboarding" viewTransition onClick={() => setNavDirection('forward')}>
              {t('welcome.track_start')}
            </ButtonLink>
          }
        />
      </>
    );
  }

  if (!data) {
    return (
      <>
        {header}
        <OfflineBanner />
        <Notice
          tone="error"
          title={t('error.generic')}
          action={
            <Button variant="fill" size="sm" type="button" icon="refresh" onClick={() => day.refetch()}>
              {t('error.retry')}
            </Button>
          }
        />
      </>
    );
  }

  const water = sumWater(data.water);
  const showTiles = published != null && (published.steps != null || published.active_kcal != null);
  const weights = week.data?.weights ?? [];
  const kgShown = isToday ? me.weightKg : weightOn(weights, epochDay);
  const from = before.current?.eaten ?? null;

  return (
    <>
      {header}
      <OfflineBanner />
      <div className={stale ? 'today-grid stale' : 'today-grid'} inert={stale} aria-busy={stale || undefined}>
        <div className="today-main">
          <DayHero summary={summary} eatenFallback={eaten} epochDay={epochDay} first={first && !arrival} from={from} shared={sharedRing}>
            <MacrosCard eaten={macroTotals(counted)} targets={me.targets} />
          </DayHero>
          <div className="meals-col">
            <div className="section-head a-fade-up" style={{ '--d': 400 } as CSSProperties}>
              <h2 className="title-2">{t('me.meals')}</h2>
              {anyEntries && (
                <Button variant="plain" type="button" className="edit-toggle" onClick={() => setEditing((e) => !e)}>
                  {editing ? t('me.done') : t('me.edit')}
                </Button>
              )}
            </div>
            <div className="stack meals">
              {MEAL_SLOTS.map((slot, i) => (
                <MealCard
                  key={slot}
                  slot={slot}
                  index={i}
                  entries={groups.get(slot) ?? []}
                  editable
                  editing={editing}
                  epochDay={epochDay}
                  fresh={fresh}
                  leaving={leaving}
                  totalFrom={before.current?.slot === slot ? before.current.meal : undefined}
                  onDelete={remove}
                  onOpen={(entry) => setSheet({ entry, mode: 'actions' })}
                />
              ))}
            </div>
          </div>
        </div>
        <div className="today-side">
          {showTiles && <ActivityTiles steps={published.steps} active={published.active_kcal} updatedAt={publishedAt(published)} />}
          {desktop && week.data && (
            <WeekMini week={week.data} today={today} fallbackTarget={targetKcal} live={stale ? null : { epochDay, eaten: Math.round(eaten) }} />
          )}
          <WaterCard
            ml={water}
            lastAt={lastWaterAt(data.water)}
            editable
            onAdd={(ml) => addWater.mutate({ ml, epochDay })}
            onUndo={() => undoWater.mutate({ epochDay })}
          />
          <WeightCard
            epochDay={epochDay}
            isToday={isToday}
            kg={kgShown}
            latestKg={me.weightKg}
            change={kgShown != null ? weekChange(weights, epochDay) : null}
            saving={logWeight.isPending}
            onSave={(kg) => logWeight.mutateAsync({ weightKg: kg, epochDay })}
          />
        </div>
      </div>
      {userId && warmed !== epochDay - 1 && <WarmDay ownerId={userId} epochDay={epochDay - 1} onDone={setWarmed} />}

      <Sheet open={sheet != null} onClose={() => setSheet(null)} label={sheet?.entry.name ?? ''} className="entry-sheet">
        {sheet && (
          <EntrySheet
            key={sheet.entry.id}
            entry={sheet.entry}
            mode={sheet.mode}
            onMode={(mode) => setSheet({ entry: sheet.entry, mode })}
            onClose={() => setSheet(null)}
            onDelete={() => {
              const entry = sheet.entry;
              // The row that opened the sheet is going: focus lands on its neighbour once the sheet has.
              const next = focusTargetNear(entry.id, false);
              setSheet(null);
              remove(entry);
              focusWhenSheetGone(() => next);
            }}
            onAmount={async (grams) => {
              const entry = sheet.entry;
              const next = entryAtGrams(entry, grams, newEntryId());
              if (!next) return;
              // Logged again at the new amount (same meal and time, so it keeps its place), then
              // the old row goes: never a moment with neither.
              await writeSettled(entry.id);
              await writeEntry(next, me.summary);
              setWritten((w) => [...w, next]);
              setHidden((s) => withId(s, entry.id));
              setSheet(null);
              focusWhenSheetGone(() => rowControl(rowItem(next.id), false));
              deleteFood.mutateAsync({ id: entry.id, epochDay: entry.epoch_day, summary: me.summary }).catch(() => {
                // The old row is still on the server: show it again rather than hide what is there.
                setHidden((s) => withoutId(s, entry.id));
                toast({ text: t('error.generic'), icon: 'info' });
                refetchDay();
              });
            }}
          />
        )}
      </Sheet>
    </>
  );
}

/** The loading page at the final geometry: the ring's track, two lines, three cards. */
function DaySkeleton({ label }: { label: string }) {
  return (
    <div className="today-grid day-skeleton" aria-busy="true">
      <div className="today-main">
        <div className="hero">
          <div className="ring-wrap">
            <Skeleton variant="ring" label={label} />
          </div>
          <div className="hero-side">
            <div className="kpis">
              <Skeleton variant="line" width="60%" height={14} />
              <Skeleton variant="line" width="40%" height={14} />
            </div>
          </div>
        </div>
        <div className="stack meals">
          <Skeleton variant="block" height={140} />
          <Skeleton variant="block" height={120} />
        </div>
      </div>
      <div className="today-side">
        <Skeleton variant="block" height={150} />
      </div>
    </div>
  );
}

/**
 * Loads one day into the cache, then unmounts: the day before is there the
 * moment ‹ is pressed, yet it is not watched. An unwatched query is only marked
 * stale by the page's polls, realtime events and writes; it refetches when it
 * is shown, not on every one of them.
 */
export function WarmDay({ ownerId, epochDay, onDone }: { ownerId: string; epochDay: number; onDone: (epochDay: number) => void }) {
  const day = useDay(ownerId, epochDay);
  const settled = day.data !== undefined || day.isError;
  useEffect(() => {
    if (settled) onDone(epochDay);
  }, [settled, epochDay, onDone]);
  return null;
}

interface WeekMiniProps {
  week: NonNullable<ReturnType<typeof useWeek>['data']>;
  today: number;
  fallbackTarget: number | null;
  /** The day on screen and the hero's eaten total for it, which the week's own copy may trail. */
  live: { epochDay: number; eaten: number } | null;
}

/** Desktop: the week at a glance in the side column, seven markers and the days on plan; it opens My week. */
function WeekMini({ week, today, fallbackTarget, live }: WeekMiniProps) {
  const { t, tp, tag } = useI18n();
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  // The day on screen counts what the hero counts (a row deleted or brought back a moment
  // ago), so the two rings agree and this one does not move again when the refetch lands.
  const source =
    live && week.days.includes(live.epochDay)
      ? { ...week, entries: [...week.entries.filter((e) => e.epoch_day !== live.epochDay), { epoch_day: live.epochDay, kcal: live.eaten }] }
      : week;
  const rows = buildWeekRows(source, today, { fallbackTarget });
  const stats = weekStats(rows, today);
  const onPlan = tp('week.days_on_plan', stats.logged, { good: stats.good, logged: stats.logged });
  return (
    <Link
      className="card week-mini a-rise"
      style={{ '--d': 640 } as CSSProperties}
      to="/me/week"
      viewTransition
      aria-label={`${t('me.week')}: ${onPlan}`}
    >
      <CardHead
        as="span"
        label={t('me.week')}
        meta={
          <>
            {onPlan}
            <Icon name="right" size="xs" />
          </>
        }
      />
      <div className="days small">
        {rows.map((row, i) => {
          const state = markerState(row);
          const name = t(`week.marker_${state === 'GOOD' ? 'good' : state === 'OVER' ? 'over' : state === 'TODAY' ? 'today' : 'empty'}`, {
            day: longWeekday(row.epochDay, tag),
            kcal: nf.format(row.eaten),
          });
          return (
            <DayMarker
              key={row.epochDay}
              state={state}
              progress={row.target ? row.eaten / row.target : 0}
              label={row.isToday ? t('day.today') : shortWeekday(row.epochDay, tag)}
              name={name}
              size={30}
              delay={700 + i * 50}
            />
          );
        })}
      </div>
    </Link>
  );
}

interface EntrySheetProps {
  entry: FoodLogEntry;
  mode: 'actions' | 'amount';
  onMode: (mode: 'actions' | 'amount') => void;
  onClose: () => void;
  onDelete: () => void;
  onAmount: (grams: number) => Promise<void>;
}

/** What a tap on a diary row offers: Edit amount (logged again at the new amount) and Delete. */
function EntrySheet({ entry, mode, onMode, onClose, onDelete, onAmount }: EntrySheetProps) {
  const { t, tag } = useI18n();
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const [text, setText] = useState(entry.grams != null ? String(Math.round(entry.grams)) : '');
  const [saving, setSaving] = useState(false);
  const [failed, setFailed] = useState(false);
  const editRef = useRef<HTMLButtonElement>(null);
  const formRef = useRef<HTMLFormElement>(null);

  // Edit amount swaps the content under the focused button, so focus follows: to the amount
  // field where there is a keyboard, to the form on a touch screen (the phone's keyboard would
  // cover the sheet), and back to Edit amount on Cancel.
  const shownMode = useRef(mode);
  useEffect(() => {
    if (shownMode.current === mode) return;
    shownMode.current = mode;
    if (mode === 'actions') {
      editRef.current?.focus({ preventScroll: true });
      return;
    }
    const field = formRef.current?.querySelector('input');
    const touch = typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;
    if (field && !touch) {
      field.focus({ preventScroll: true });
      field.select();
    } else formRef.current?.focus({ preventScroll: true });
  }, [mode]);
  const product = entryAsProduct(entry);
  const grams = Number(text.trim().replace(',', '.'));
  const valid = Number.isFinite(grams) && grams > 0 && grams <= 5000;
  const kcal = product && valid ? Math.round((product.kcalPer100g * grams) / 100) : Math.round(entry.kcal);
  const meta = `${entry.grams !== null ? `${nf.format(Math.round(entry.grams))} ${t('unit.g')} · ` : ''}${nf.format(Math.round(entry.kcal))} ${t('unit.kcal')}`;

  return (
    <div className="es">
      <div className="es-head">
        <span className={`mtile ${entry.meal}`} aria-hidden="true">
          <Icon name={entry.meal === 'BREAKFAST' ? 'sunrise' : entry.meal === 'LUNCH' ? 'sun' : entry.meal === 'DINNER' ? 'moon' : 'cookie'} size="sm" />
        </span>
        <div className="t">
          <p className="eyebrow">{t(`meal.${entry.meal}`)}</p>
          <h2>{entry.name}</h2>
          <p className="num">{meta}</p>
        </div>
      </div>
      {mode === 'actions' ? (
        <div className="es-actions">
          {product && (
            <Button ref={editRef} variant="fill" size="lg" type="button" icon="pencil" onClick={() => onMode('amount')}>
              {t('me.edit_amount')}
            </Button>
          )}
          <Button variant="danger" size="lg" type="button" icon="trash" onClick={onDelete}>
            {t('me.delete')}
          </Button>
          <Button variant="plain" type="button" className="es-cancel" onClick={onClose}>
            {t('add.cancel')}
          </Button>
        </div>
      ) : (
        <form
          ref={formRef}
          className="es-amount"
          tabIndex={-1}
          aria-label={t('me.edit_amount')}
          onSubmit={(event) => {
            event.preventDefault();
            if (!valid || saving) return;
            setSaving(true);
            setFailed(false);
            onAmount(grams).catch(() => {
              setSaving(false);
              setFailed(true);
            });
          }}
        >
          {failed && <Notice tone="warning" title={t('error.generic')} role="alert" />}
          <div className="es-kcal num">
            <Digits value={kcal} gradient />
            <span className="unit">{t('unit.kcal')}</span>
          </div>
          <AmountStepper
            value={text}
            onChange={setText}
            unit={t('unit.g')}
            step={10}
            max={5000}
            label={t('add.grams')}
            decLabel={t('add.less')}
            incLabel={t('add.more')}
          />
          <Button variant="ink" size="lg" type="submit" disabled={!valid || saving}>
            {t('settings.save')}
          </Button>
          <Button variant="plain" type="button" className="es-cancel" onClick={() => onMode('actions')}>
            {t('add.cancel')}
          </Button>
        </form>
      )}
    </div>
  );
}
