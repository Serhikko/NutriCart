import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider, type Locale } from '../../../lib/i18n';
import {
  axisDate,
  buildWeekRows,
  dayLabel,
  kcalGridValues,
  verdict,
  weekRange,
  weekStats,
  weightGuides,
  weightSeries,
  type WeekDay,
} from '../../../lib/weekView';
import type { DaySummary, WeightEntry } from '../../../lib/diary';
import en from '../../../locales/en.json';
import uk from '../../../locales/uk.json';
import { WeekOverview } from '../WeekOverview';

const TODAY = 20735; // Friday 9 October 2026
const DAYS = Array.from({ length: 7 }, (_, i) => TODAY - 6 + i);
const summary = (epoch_day: number, eaten_kcal: number, target_kcal = 2000): DaySummary => ({
  epoch_day,
  eaten_kcal,
  target_kcal,
  active_kcal: null,
  steps: null,
  workout_kcal: 0,
});

describe('weekView', () => {
  it('counts your own diary first and a partner’s published summary first', () => {
    const week = { days: DAYS, summaries: [summary(TODAY, 900)], entries: [{ epoch_day: TODAY, kcal: 1000.4 }] };
    expect(buildWeekRows(week, TODAY).at(-1)?.eaten).toBe(1000);
    expect(buildWeekRows(week, TODAY, { preferSummary: true }).at(-1)?.eaten).toBe(900);
    // No summary: the profile's target stands in, or nothing does.
    expect(buildWeekRows(week, TODAY, { fallbackTarget: 1800 })[0].target).toBe(1800);
    expect(buildWeekRows(week, TODAY)[0].target).toBeNull();
  });

  it('averages the completed logged days, and judges days with the 5% tolerance', () => {
    const week = {
      days: DAYS,
      summaries: [summary(DAYS[0], 2200), summary(DAYS[1], 2050), summary(DAYS[2], 1800), summary(TODAY, 500)],
      entries: [],
    };
    const rows = buildWeekRows(week, TODAY);
    const stats = weekStats(rows, TODAY);
    expect(stats.average).toBe(Math.round((2200 + 2050 + 1800) / 3));
    expect(stats.good).toBe(3); // 2050 is within 5%, 1800 under, today's 500 so far
    expect(stats.logged).toBe(4);
    expect(stats.goal).toBe(2000);
    expect(stats.streak).toBe(1); // today only: yesterday is empty
    expect(verdict(2200, 2000)).toBe('over');
    expect(verdict(2050, 2000)).toBe('within');
    expect(verdict(1800, 2000)).toBe('under');
  });

  it('only today logged: the average is today', () => {
    const rows = buildWeekRows({ days: DAYS, summaries: [summary(TODAY, 640)], entries: [] }, TODAY);
    expect(weekStats(rows, TODAY).average).toBe(640);
    expect(weekStats(buildWeekRows({ days: DAYS, summaries: [], entries: [] }, TODAY), TODAY).average).toBeNull();
  });

  it('keeps 30 days of weights, one per day, with the delta and whole-kilogram guides', () => {
    const w = (epoch_day: number, weight_kg: number, source: WeightEntry['source'] = 'MANUAL'): WeightEntry => ({
      epoch_day,
      weight_kg,
      source,
    });
    const series = weightSeries(
      [w(TODAY - 40, 70), w(TODAY - 30, 66.4), w(TODAY - 10, 65.2, 'HEALTH_CONNECT'), w(TODAY - 10, 65), w(TODAY, 64.2)],
      TODAY,
    );
    expect(series.points.map((p) => p.kg)).toEqual([66.4, 65, 64.2]);
    expect(series.delta).toBe(-2.2);
    expect(series.latest?.kg).toBe(64.2);
    expect(series.guides).toEqual([64, 66]);
    expect(weightGuides(64.05, 64.95)).toEqual([64.5]);
    expect(weightGuides(80.5, 90.5)).toEqual([84, 87]);
  });

  it('drops the 1,000 gridline when the goal pill would sit on it', () => {
    expect(kcalGridValues(2600, 2100)).toEqual([0, 1000]);
    expect(kcalGridValues(1400, 1050)).toEqual([0]);
  });

  it('names the week with Intl, in every language', () => {
    // ICU versions differ on the spaces round the dash; the words do not.
    expect(weekRange(TODAY - 6, TODAY, 'en-GB')).toMatch(/^3\s?–\s?9 October$/);
    expect(weekRange(TODAY - 6, TODAY, 'uk-UA')).toMatch(/^3\s?–\s?9 жовтня$/);
    expect(weekRange(TODAY - 6, TODAY, 'be-BY')).toMatch(/^3\s?–\s?9 кастрычніка$/);
    expect(weekRange(TODAY - 6, TODAY, 'ru-RU')).toMatch(/^3\s?–\s?9 октября$/);
    expect(weekRange(TODAY - 9, TODAY - 3, 'en-GB')).toMatch(/^30 September\s?–\s?6 October$/);
  });

  it('names a day with the weekday in the nominative, and the axis with the date alone', () => {
    // Chromium declines a Ukrainian weekday formatted with its date ("Пʼятницю"); Node's ICU may not,
    // so this pins the words rather than proving the decline away.
    expect(dayLabel(TODAY, 'en-GB')).toBe('Friday 9 Oct');
    expect(dayLabel(TODAY, 'uk-UA')).toBe('Пʼятниця, 9 жовт.');
    expect(dayLabel(TODAY - 5, 'uk-UA')).toBe('Неділя, 4 жовт.');
    // Belarusian and Russian write the same comma after the weekday ("субота, 10 кастрычніка", "суббота, 10 октября").
    expect(dayLabel(TODAY, 'ru-RU')).toBe('Пятница, 9 окт.');
    expect(dayLabel(TODAY - 5, 'ru-RU')).toBe('Воскресенье, 4 окт.');
    expect(dayLabel(TODAY, 'be-BY')).toMatch(/^Пятніца, 9 кас\.?$/);
    expect(axisDate(TODAY, 'en-GB')).toBe('9 Oct');
    expect(axisDate(TODAY, 'uk-UA')).toBe('9 жовт.');
  });
});

function renderWeek(
  rows: WeekDay[] | null,
  weights: WeightEntry[] = [],
  extra: Partial<Parameters<typeof WeekOverview>[0]> = {},
  locale: Locale = 'en',
) {
  localStorage.setItem('nutricart.locale', locale);
  return render(
    <I18nProvider>
      <MemoryRouter>
        <WeekOverview
          loading={false}
          failed={false}
          onRetry={() => undefined}
          rows={rows}
          weights={weights}
          today={TODAY}
          emptyAction={null}
          {...extra}
        />
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('WeekOverview (no matchMedia, ResizeObserver or Web Animations)', () => {
  const week = {
    days: DAYS,
    summaries: [summary(DAYS[0], 2290), summary(DAYS[1], 1840), summary(DAYS[2], 1720), summary(TODAY, 1230)],
    entries: [],
  };
  const rows = buildWeekRows(week, TODAY);

  it('renders the average, a named marker per day, both charts and their tables', () => {
    renderWeek(rows, [
      { epoch_day: TODAY - 3, weight_kg: 64.6, source: 'MANUAL' },
      { epoch_day: TODAY, weight_kg: 64.2, source: 'MANUAL' },
    ]);
    expect(screen.getByRole('heading', { name: 'Daily average' })).toBeInTheDocument();
    expect(screen.getAllByText('1,950').length).toBeGreaterThan(0); // (2290 + 1840 + 1720) / 3
    expect(screen.getByRole('img', { name: 'Saturday: 2,290 kcal, over goal' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Sunday: 1,840 kcal, on plan' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Today: 1,230 kcal so far' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Tuesday: nothing logged' })).toBeInTheDocument();
    expect(screen.getByText('3 of 4 days on plan')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Calories against target' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Weight' })).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Calories against target' });
    expect(within(table).getAllByRole('row')).toHaveLength(8);
    expect(within(table).getByRole('rowheader', { name: 'Today' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Weight' })).toBeInTheDocument();
  });

  it('shows the latest weight, then any weight picked with the arrow keys', () => {
    renderWeek(rows, [
      { epoch_day: TODAY - 12, weight_kg: 65.1, source: 'MANUAL' },
      { epoch_day: TODAY - 3, weight_kg: 64.6, source: 'MANUAL' },
      { epoch_day: TODAY, weight_kg: 64.2, source: 'MANUAL' },
    ]);
    const chart = screen.getByRole('group', { name: 'Weight' });
    const head = document.querySelector('.wt-head') as HTMLElement;
    expect(head).toHaveAttribute('aria-live', 'polite');
    expect(chart).toHaveAttribute('tabindex', '0');
    expect(within(head).getByText('Today')).toBeInTheDocument();
    expect(within(head).getByText('64.2')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'ArrowLeft' });
    expect(within(head).getByText('Tuesday 6 Oct')).toBeInTheDocument();
    expect(within(head).getByText('64.6')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'Home' });
    expect(within(head).getByText('Sunday 27 Sept')).toBeInTheDocument();
    expect(within(head).getByText('65.1')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'ArrowLeft' }); // already the first: stays
    expect(within(head).getByText('65.1')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'End' });
    expect(within(head).getByText('Today')).toBeInTheDocument();
    expect(within(head).getByText('64.2')).toBeInTheDocument();
    // The 30-day change stays put whichever weight is shown.
    expect(within(head).getByText('0.9 kg in 30 days')).toBeInTheDocument();
  });

  it('keeps the drawn line and bars when a day or weight is picked (no replayed entrance)', () => {
    renderWeek(rows, [
      { epoch_day: TODAY - 3, weight_kg: 64.6, source: 'MANUAL' },
      { epoch_day: TODAY, weight_kg: 64.2, source: 'MANUAL' },
    ]);
    const line = document.querySelector('.wt-line');
    const bar = document.querySelector('.kcal-chart .bar-g');
    expect(line).not.toBeNull();
    expect(bar).not.toBeNull();
    fireEvent.keyDown(screen.getByRole('group', { name: 'Weight' }), { key: 'ArrowLeft' });
    fireEvent.keyDown(screen.getByRole('group', { name: 'Calories against target' }), { key: 'Home' });
    expect(document.querySelector('.wt-line')).toBe(line);
    expect(document.querySelector('.kcal-chart .bar-g')).toBe(bar);
    // The picks still show: the other days dim, and the picked weight gets its mark.
    expect(document.querySelectorAll('.kcal-chart .bar-col.dim')).toHaveLength(6);
    expect(document.querySelector('.wt-sel-dot')).not.toBeNull();
  });

  it('starts on today and walks the days with the arrow keys', () => {
    renderWeek(rows);
    expect(screen.getByText('Today so far', { selector: '.s-l' })).toBeInTheDocument();
    expect(screen.getByText('770 under the 2,000 goal')).toBeInTheDocument();
    const chart = screen.getByRole('group', { name: 'Calories against target' });
    for (let i = 0; i < 6; i += 1) fireEvent.keyDown(chart, { key: 'ArrowLeft' });
    expect(screen.getByText('290 over the 2,000 goal')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'ArrowRight' });
    expect(screen.getByText('160 under the 2,000 goal')).toBeInTheDocument();
    fireEvent.keyDown(chart, { key: 'End' });
    expect(screen.getByText('770 under the 2,000 goal')).toBeInTheDocument();
  });

  // The counted forms of "days on plan" live in the locale files; until both have them the caption
  // falls back to the one uncounted template (checked above), so this waits for them.
  const pluralReady = 'week.days_on_plan_one' in en && 'week.days_on_plan_one' in uk;
  it.skipIf(!pluralReady)('counts the days on plan in the page language: one day, then a few', () => {
    const first = buildWeekRows({ days: DAYS, summaries: [summary(TODAY, 640)], entries: [] }, TODAY);
    const a = renderWeek(first);
    expect(screen.getByText('1 of 1 day on plan')).toBeInTheDocument();
    a.unmount();
    const b = renderWeek(first, [], {}, 'uk');
    expect(screen.getByText('1 з 1 дня за планом')).toBeInTheDocument();
    b.unmount();
    renderWeek(rows, [], {}, 'uk');
    expect(screen.getByText('3 з 4 днів за планом')).toBeInTheDocument();
  });

  it('shows the empty, failed and loading states', () => {
    const empty = buildWeekRows({ days: DAYS, summaries: [], entries: [] }, TODAY);
    const { unmount } = renderWeek(empty);
    expect(screen.getByText('No data for these days yet.')).toBeInTheDocument();
    unmount();
    const failed = renderWeek(null, [], { failed: true });
    expect(screen.getByText('Something went wrong.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    failed.unmount();
    renderWeek(null, [], { loading: true });
    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });
});
