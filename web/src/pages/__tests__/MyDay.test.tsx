import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nProvider } from '../../lib/i18n';
import { todayEpochDay } from '../../lib/dates';
import { ToastProvider } from '../../components/ui/Toast';
import type { FoodLogEntry, Nudge } from '../../lib/diary';

const TODAY = todayEpochDay();
const at = (day: number, hh: number, mm: number) => {
  const d = new Date((day * 86_400_000));
  return new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), hh, mm).toISOString();
};
const entry = (id: string, day: number, meal: FoodLogEntry['meal'], name: string, kcal: number, hh = 8): FoodLogEntry => ({
  id,
  owner_id: 'u1',
  epoch_day: day,
  meal,
  name,
  grams: 100,
  servings: null,
  kcal,
  protein_g: 10,
  fat_g: 5,
  carbs_g: 20,
  logged_at: at(day, hh, 5),
  deleted_at: null,
});

const world = vi.hoisted(() => ({
  days: new Map<number, { entries: FoodLogEntry[]; water: { id: string; epoch_day: number; ml: number; deleted_at: string | null }[]; summary: null }>(),
  /** Days whose load fails. */
  failing: new Set<number>(),
  nudges: [] as Nudge[],
  /** The list of people you follow has not arrived yet. */
  followedPending: false,
}));
/** What the page wrote to Supabase itself (Undo's copy, a row at a new amount). */
const db = vi.hoisted(() => ({ inserts: [] as { table: string; row: Record<string, unknown> }[] }));
const writes = vi.hoisted(() => ({
  deleteFood: vi.fn(async (_: unknown) => undefined),
  addWater: vi.fn(),
  logWeight: vi.fn(async (_: unknown) => undefined),
}));

vi.mock('../../lib/session', () => ({ useSession: () => ({ userId: 'u1', loading: false, session: null }) }));
vi.mock('../../lib/myTargets', () => ({
  useMyTargets: () => ({
    userId: 'u1',
    loading: false,
    onboarded: true,
    details: {},
    weightKg: 64.2,
    targets: { kcal: 2000, proteinG: 100, fatG: 60, carbsG: 250 },
    primaryClient: 'web',
    summary: { targetKcal: 2000, primaryClient: 'web' },
  }),
}));
vi.mock('../../lib/supabase', () => {
  const from = (table: string) => {
    const query: Record<string, unknown> = {};
    Object.assign(query, {
      select: () => query,
      eq: () => query,
      is: () => query,
      maybeSingle: async () => ({ data: null, error: null }),
      then: (resolve: (value: unknown) => unknown) => resolve({ data: [], error: null }),
      insert: async (row: Record<string, unknown>) => {
        db.inserts.push({ table, row });
        return { error: null };
      },
      upsert: async () => ({ error: null }),
    });
    return query;
  };
  return { supabase: { from } };
});
vi.mock('../../lib/queries', () => ({
  useDay: (_owner: string, day: number) =>
    world.failing.has(day) ? { data: undefined, isError: true, refetch: vi.fn() } : { data: world.days.get(day), isError: false, refetch: vi.fn() },
  useWeek: () => ({ data: undefined }),
  useOwnerRealtime: () => undefined,
  useFollowed: () =>
    world.followedPending ? { data: undefined, isError: false } : { data: [{ linkId: 'l1', ownerId: 'p1', ownerName: 'Andrii' }], isError: false },
  useMyProfile: () => ({ data: 'Olena' }),
  useMyNudges: () => ({ data: world.nudges }),
  // Sending succeeds at once.
  useSendNudge: () => ({
    mutate: (_body: string, options?: { onSuccess?: () => void }) => options?.onSuccess?.(),
    isPending: false,
    isSuccess: false,
    isError: false,
  }),
}));
vi.mock('../../lib/tracker', () => ({
  useAddWater: () => ({ mutate: writes.addWater }),
  useUndoWater: () => ({ mutate: vi.fn() }),
  useDeleteFood: () => ({ mutateAsync: writes.deleteFood }),
  useLogFood: () => ({ mutateAsync: vi.fn(async () => undefined) }),
  useLogWeight: () => ({ mutateAsync: writes.logWeight, isPending: false }),
}));

const { MyDay } = await import('../MyDay');
const { Day } = await import('../Day');
const { WaterCard } = await import('../../components/WaterCard');
const { WeightCard } = await import('../../components/WeightCard');

// ScrollRestoration-free routers still call scrollTo on some navigations; jsdom has no scrolling.
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;

function renderAt(path: string, state?: unknown) {
  localStorage.setItem('nutricart.locale', 'en');
  const router = createMemoryRouter(
    [
      { path: '/me/day', element: <MyDay /> },
      { path: '/a/:ownerId/day', element: <Day /> },
      { path: '/me/add/:epochDay/:slot', element: <p>add</p> },
    ],
    { initialEntries: [{ pathname: path, state }] },
  );
  const view = render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nProvider>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
  return { router, ...view };
}

beforeEach(() => {
  world.days.clear();
  world.days.set(TODAY, {
    entries: [
      entry('a', TODAY, 'BREAKFAST', 'Greek yoghurt 2%', 110, 7),
      entry('b', TODAY, 'LUNCH', 'A very long product name that goes on and on, with a brand and a flavour, and a size', 500, 13),
    ],
    water: [{ id: 'w1', epoch_day: TODAY, ml: 500, deleted_at: null }],
    summary: null,
  });
  world.days.set(TODAY - 1, { entries: [entry('y', TODAY - 1, 'DINNER', 'Varenyky', 900, 19)], water: [], summary: null });
  world.nudges = [];
  world.failing.clear();
  world.followedPending = false;
  db.inserts = [];
  writes.deleteFood.mockClear();
  writes.addWater.mockClear();
  sessionStorage.clear();
});
afterEach(() => vi.useRealTimers());

describe('MyDay', () => {
  it('renders the whole day without matchMedia, animations or observers', () => {
    renderAt('/me/day');
    expect(screen.getByRole('heading', { level: 1, name: 'My day' })).toBeInTheDocument();
    // The ring is a picture named by the day line.
    expect(screen.getByRole('img', { name: '610 / 2,000 kcal, 1,390 left' })).toBeInTheDocument();
    for (const meal of ['Breakfast', 'Lunch', 'Dinner', 'Snacks']) expect(screen.getByRole('heading', { level: 3, name: meal })).toBeInTheDocument();
    expect(screen.getAllByText('Nothing yet')).toHaveLength(2);
    expect(screen.getAllByRole('link', { name: 'Add food' })[0]).toHaveAttribute('href', `/me/add/${TODAY}/BREAKFAST`);
    expect(screen.getByRole('meter', { name: 'Protein' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+250 ml' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Weight today' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'previous day' })).toBeInTheDocument();
  });

  it('builds the hero on the first open of the day only', () => {
    renderAt('/me/day');
    expect(document.querySelector('.hero .ring-wrap')).toHaveClass('a-ring');
  });

  it('names the ring "In the zone" while the badge shows', () => {
    world.days.set(TODAY, { entries: [entry('z', TODAY, 'LUNCH', 'Borscht', 1900, 13)], water: [], summary: null });
    renderAt('/me/day');
    expect(screen.getByRole('img', { name: '1,900 / 2,000 kcal, 100 left, In the zone' })).toBeInTheDocument();
  });

  it('fills a new glass at once, without the first-open entrance', () => {
    localStorage.setItem('nutricart.locale', 'en');
    const glasses = () => [...document.querySelectorAll('.liq-g')].map((g) => g.classList.contains('a-liquid'));
    const view = render(
      <I18nProvider>
        <WaterCard ml={500} editable />
      </I18nProvider>,
    );
    expect(glasses()).toEqual([true, true, false, false, false, false, false, false]);
    view.rerender(
      <I18nProvider>
        <WaterCard ml={750} editable />
      </I18nProvider>,
    );
    // The third glass is full now, but only the two from the first open have the staggered entrance.
    expect((document.querySelectorAll('.liq-g')[2] as HTMLElement).style.getPropertyValue('--f')).toBe('1');
    expect(glasses()).toEqual([true, true, false, false, false, false, false, false]);
  });

  it('says which way the weight went, not only with the arrow', () => {
    localStorage.setItem('nutricart.locale', 'en');
    const card = (change: number) => (
      <I18nProvider>
        <WeightCard epochDay={TODAY} isToday kg={64.2} latestKg={64.2} change={change} onSave={async () => undefined} />
      </I18nProvider>
    );
    const view = render(card(-0.6));
    const chip = document.querySelector('.weight-now .delta')!;
    expect(chip.querySelector('.sr')!.textContent).toMatch(/^[-−]0\.6 kg this week$/);
    expect(chip.querySelector('.delta-v')).toHaveAttribute('aria-hidden', 'true');
    view.rerender(card(0.4));
    expect(document.querySelector('.weight-now .delta .sr')!.textContent).toBe('+0.4 kg this week');
  });

  it('adds water for the day shown', () => {
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: '+250 ml' }));
    expect(writes.addWater).toHaveBeenCalledWith({ ml: 250, epochDay: TODAY });
  });

  it('labels the weight with the date on an earlier day', () => {
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: 'previous day' }));
    expect(screen.getByText('Varenyky')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Weight today' })).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /^Weight on \d+ \w+$/ })).toBeInTheDocument();
  });

  it('writes a delete from Edit mode at once, and Undo logs the row again', async () => {
    vi.useFakeTimers();
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Greek yoghurt 2%' }));
    // Written straight away: a reload during the toast must not lose it.
    expect(writes.deleteFood).toHaveBeenCalledWith({ id: 'a', epochDay: TODAY, summary: { targetKcal: 2000, primaryClient: 'web' } });
    act(() => vi.advanceTimersByTime(400));
    expect(screen.queryByText('Greek yoghurt 2%')).not.toBeInTheDocument();
    expect(screen.getByText('Deleted Greek yoghurt 2%')).toBeInTheDocument();
    fireEvent.click(within(document.querySelector<HTMLElement>('.toast-region')!).getByRole('button', { name: 'Undo' }));
    // Back on screen at once, then written as a new row: same food, meal and time.
    expect(screen.getByText('Greek yoghurt 2%')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
    });
    expect(db.inserts).toHaveLength(1);
    const copy = db.inserts[0];
    expect(copy.table).toBe('food_log_entries');
    expect(copy.row).toMatchObject({ owner_id: 'u1', epoch_day: TODAY, meal: 'BREAKFAST', name: 'Greek yoghurt 2%', grams: 100, kcal: 110, deleted_at: null });
    expect(copy.row.logged_at).toBe(at(TODAY, 7, 5));
    expect(copy.row.id).toMatch(/^web:f:/);
    expect(copy.row.id).not.toBe('a');
    expect(writes.deleteFood).toHaveBeenCalledTimes(1);
  });

  it('writes a delete from the action sheet at once', () => {
    renderAt('/me/day');
    fireEvent.click(screen.getByText('Greek yoghurt 2%').closest('button')!);
    const sheet = screen.getByRole('dialog', { name: 'Greek yoghurt 2%' });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Delete' }));
    expect(writes.deleteFood).toHaveBeenCalledWith({ id: 'a', epochDay: TODAY, summary: { targetKcal: 2000, primaryClient: 'web' } });
  });

  it('brings a row back with an error when its delete fails', async () => {
    writes.deleteFood.mockImplementationOnce(async () => {
      throw new Error('offline');
    });
    vi.useFakeTimers();
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Greek yoghurt 2%' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });
    expect(screen.getByText('Greek yoghurt 2%')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument();
  });

  it('moves focus to the next row, or the meal\'s Add food, when a row is deleted', () => {
    world.days.get(TODAY)!.entries.push(entry('c', TODAY, 'BREAKFAST', 'Oat porridge', 210, 8));
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const disc = screen.getByRole('button', { name: 'Delete Greek yoghurt 2%' });
    disc.focus();
    fireEvent.click(disc);
    // Edit mode: the next row's disc, so rows can be deleted one after another.
    const next = screen.getByRole('button', { name: 'Delete Oat porridge' });
    expect(document.activeElement).toBe(next);
    fireEvent.click(next);
    const breakfast = screen.getByRole('heading', { level: 3, name: 'Breakfast' }).closest('section')!;
    expect(document.activeElement).toBe(within(breakfast).getByRole('link', { name: 'Add food' }));
  });

  it('lands focus on the page after Delete in the action sheet', () => {
    vi.useFakeTimers();
    world.days.get(TODAY)!.entries.push(entry('c', TODAY, 'BREAKFAST', 'Oat porridge', 210, 8));
    renderAt('/me/day');
    fireEvent.click(screen.getByText('Greek yoghurt 2%').closest('button')!);
    fireEvent.click(within(screen.getByRole('dialog', { name: 'Greek yoghurt 2%' })).getByRole('button', { name: 'Delete' }));
    act(() => vi.advanceTimersByTime(100));
    expect(document.activeElement).toBe(screen.getByText('Oat porridge').closest('button'));
  });

  it('logs a new amount in place, with focus in the amount and then on the new row', async () => {
    vi.useFakeTimers();
    renderAt('/me/day');
    fireEvent.click(screen.getByText('Greek yoghurt 2%').closest('button')!);
    const sheet = screen.getByRole('dialog', { name: 'Greek yoghurt 2%' });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Edit amount' }));
    const field = within(sheet).getByRole('textbox', { name: 'Grams' });
    expect(document.activeElement).toBe(field);
    fireEvent.change(field, { target: { value: '200' } });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Save' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(db.inserts).toHaveLength(1);
    expect(db.inserts[0].row).toMatchObject({ name: 'Greek yoghurt 2%', meal: 'BREAKFAST', grams: 200, kcal: 220, protein_g: 20, deleted_at: null });
    expect(db.inserts[0].row.logged_at).toBe(at(TODAY, 7, 5));
    expect(writes.deleteFood).toHaveBeenCalledWith({ id: 'a', epochDay: TODAY, summary: { targetKcal: 2000, primaryClient: 'web' } });
    // Focus waits for the sheet to have gone.
    act(() => vi.advanceTimersByTime(100));
    const row = screen.getByText('Greek yoghurt 2%').closest('button')!;
    expect(row).toHaveTextContent('200 g');
    expect(document.activeElement).toBe(row);
  });

  it('shows the error and Retry for a day that failed to load, not the day before it', () => {
    world.failing.add(TODAY - 1);
    renderAt('/me/day');
    fireEvent.click(screen.getByRole('button', { name: 'previous day' }));
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(screen.queryByText('Greek yoghurt 2%')).not.toBeInTheDocument();
  });

  it('marks the row Add food reported back, and forgets the report afterwards', () => {
    vi.useFakeTimers();
    const { router } = renderAt('/me/day', { justAdded: { epochDay: TODAY, slot: 'BREAKFAST', name: 'Greek yoghurt 2%', kcal: 110 } });
    const row = screen.getByText('Greek yoghurt 2%').closest('li');
    expect(row).toHaveClass('fresh');
    // The shared ring lands on the hero: the hero itself does not turn in (only the first open builds it).
    expect(document.querySelector('.hero .ring-wrap')).not.toHaveClass('a-ring');
    expect(document.querySelector('.hero .stage-light')).not.toHaveClass('a-bloom');
    act(() => vi.advanceTimersByTime(3_100));
    expect(router.state.location.state).toBeNull();
    expect(router.state.location.pathname).toBe('/me/day');
  });
});

describe('Day (a partner)', () => {
  it('is read only, with the nudge card and dated nudges', () => {
    world.days.set(TODAY, { entries: [entry('p', TODAY, 'LUNCH', 'Chicken shawarma', 598, 12)], water: [], summary: null });
    world.nudges = [
      { id: 'n1', owner_id: 'p1', from_id: 'u1', from_name: 'Olena', text: 'Today one', created_at: new Date().toISOString(), seen_at: null },
      { id: 'n2', owner_id: 'p1', from_id: 'u1', from_name: 'Olena', text: 'Older one', created_at: at(TODAY - 3, 21, 5), seen_at: at(TODAY - 3, 21, 9) },
    ];
    renderAt('/a/p1/day');
    expect(screen.getByRole('heading', { level: 1, name: "Andrii's day" })).toBeInTheDocument();
    expect(screen.getByText('Chicken shawarma')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Add food' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Send a nudge' })).toBeInTheDocument();
    expect(document.getElementById('nudge')).not.toBeNull();
    // A nudge from another day says which day before the time (harness bug 6).
    const older = screen.getByText('Older one').closest('.bubble')!;
    expect(older.querySelector('.stamp')!.textContent).toMatch(/\d+ \w+,? \d\d:\d\d · seen/);
    const today = screen.getByText('Today one').closest('.bubble')!;
    expect(today.querySelector('.stamp')!.textContent).toMatch(/^\d\d:\d\d · not seen yet$/);
  });

  it('confirms a sent nudge with a toast and empties the field', () => {
    renderAt('/a/p1/day');
    const field = screen.getByRole('textbox', { name: 'Send a nudge' });
    fireEvent.change(field, { target: { value: 'Lunch?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(field).toHaveValue('');
    expect(document.querySelector('.toast-region .toast')).not.toBeNull();
  });

  it('keeps the nudge card usable when a day fails to load', () => {
    world.failing.add(TODAY - 1);
    renderAt('/a/p1/day');
    fireEvent.click(screen.getByRole('button', { name: 'previous day' }));
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    expect(document.getElementById('nudge')!.closest('[inert]')).toBeNull();
  });

  it('names nobody until the list of people you follow arrives', () => {
    world.followedPending = true;
    renderAt('/a/p1/day');
    expect(screen.queryByText(/NutriCart's day/)).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Loading…');
  });
});
