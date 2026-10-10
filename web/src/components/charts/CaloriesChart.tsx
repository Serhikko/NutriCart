import {
  createContext,
  useContext,
  useId,
  useMemo,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type MouseEvent,
  type PointerEvent,
} from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  DefaultZIndexes,
  XAxis,
  YAxis,
  ZIndexLayer,
  usePlotArea,
  useYAxisScale,
  type BarShapeProps,
  type XAxisTickContentProps,
  type YAxisTickContentProps,
} from 'recharts';
import { useI18n } from '../../lib/i18n';
import { DESKTOP_QUERY, useMediaQuery } from '../../lib/motion';
import { cap1, dayLabel, kcalChartMax, kcalGridValues, shortWeekday, verdict, type WeekDay } from '../../lib/weekView';
import { Card, CardHead } from '../ui/Card';
import { Digits } from '../ui/Digits';
import { ChartTable } from './ChartTable';
import { useChartWidth } from './useChartWidth';

/**
 * "Calories against target" (DESIGN.md §7.1): one capsule per day in front of
 * a ghost capsule that reaches that day's goal, the goal as a dashed line with
 * its value in a pill, and a header that rolls to whichever day you pick.
 *
 * - Eaten capsules use the Ember gradient; today is striped (still going).
 * - A day over its goal by more than the 5% tolerance gets an over cap from
 *   the goal up to its value, hatched in ink and outlined, and the header says
 *   "over" in bold, so on plan and over never differ by hue alone.
 * - Tap a day (or hover it with a mouse, or use the arrow keys once the chart
 *   has focus) and the header shows that day, its kcal handing off digit by
 *   digit, while the other days dim. There is no floating tooltip.
 * - recharts draws the axes, the grid and the bars (all animation off); the
 *   motion is CSS (motion.css): bars rise out of the axis, the goal line
 *   wipes in, the pill pops, on the first open only.
 * - Picking a day never re-renders the drawing: recharts rebuilds a bar's
 *   element whenever the chart re-renders with new props, which would replay
 *   the rise on every tap or hover of the first open. The drawing is memoised
 *   on the data and the size, and the bars learn the pick from a context.
 * - The drawing is aria-hidden; the same numbers are a visually hidden table.
 */

const AXIS_W = 44;
const X_AXIS_H = 26;

/** The picked day, for the bars to dim the others by (see the note on re-rendering above). */
interface Pick {
  selected: number;
  exploring: boolean;
}
const PickContext = createContext<Pick>({ selected: -1, exploring: false });
const MARGIN = { top: 8, right: 0, bottom: 0, left: 0 };
const HOVER_QUERY = '(hover: hover) and (pointer: fine)';

interface CaloriesChartProps {
  rows: WeekDay[];
  /** The goal the dashed line marks (today's, else the latest day's). */
  goal: number | null;
  className?: string;
  style?: CSSProperties;
}

export function CaloriesChart({ rows, goal, className, style }: CaloriesChartProps) {
  const { t, tag } = useI18n();
  const nf = useMemo(() => new Intl.NumberFormat(tag, { maximumFractionDigits: 0 }), [tag]);
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const fineHover = useMediaQuery(HOVER_QUERY, false);
  const [plotRef, width] = useChartWidth<HTMLDivElement>();
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const titleId = `kc-${uid}`;
  const clipId = `kc-clip-${uid}`;

  // Today is selected until the reader picks another day (the last day when today is not in view).
  const todayIndex = rows.findIndex((r) => r.isToday);
  const fallbackIndex = todayIndex >= 0 ? todayIndex : rows.length - 1;
  const [picked, setPicked] = useState<number | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const clamp = (i: number) => Math.min(rows.length - 1, Math.max(0, i));
  const selected = clamp(hovered ?? picked ?? fallbackIndex);
  // Nothing dims until the reader has picked a day themselves.
  const exploring = hovered != null || picked != null;

  const height = desktop ? 290 : 196;
  const maxBar = desktop ? 34 : 26;
  const max = kcalChartMax(rows, goal);
  const plotW = Math.max(1, width - AXIS_W);

  const row = rows[selected];
  const label = row ? (row.isToday ? t('week.today_so_far') : dayLabel(row.epochDay, tag)) : '';
  let desc: string | null = null;
  let over = false;
  if (row && row.target != null && row.target > 0) {
    const goalText = nf.format(row.target);
    if (row.eaten <= 0 && !row.isToday) desc = t('day.nothing');
    else {
      const v = verdict(row.eaten, row.target);
      const diff = nf.format(Math.abs(row.eaten - row.target));
      over = v === 'over';
      desc = t(v === 'over' ? 'week.over' : v === 'within' ? 'week.within' : 'week.under', { kcal: diff, target: goalText });
    }
  } else if (row && row.eaten <= 0 && !row.isToday) desc = t('day.nothing');

  // A tap, click or hover picks the day under the pointer: the plot is cut into equal bands.
  const indexAt = (clientX: number, el: HTMLElement) => {
    const x = clientX - el.getBoundingClientRect().left;
    return clamp(Math.floor((x / plotW) * rows.length));
  };
  const onClick = (e: MouseEvent<HTMLDivElement>) => {
    if (rows.length === 0) return;
    setPicked(indexAt(e.clientX, e.currentTarget));
  };
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (!fineHover || e.pointerType !== 'mouse') return;
    const i = indexAt(e.clientX, e.currentTarget);
    if (i !== hovered) setHovered(i);
  };
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    // Left and right walk the days; up and down are left to scroll the page.
    const step: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1 };
    let next: number | null = null;
    if (e.key in step) next = clamp(selected + step[e.key]);
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = rows.length - 1;
    if (next == null) return;
    e.preventDefault();
    setHovered(null);
    setPicked(next);
  };

  const unit = t('unit.kcal');
  const pick = useMemo<Pick>(() => ({ selected, exploring }), [selected, exploring]);

  // The drawing depends on the data and the size only, never on the pick (see the note above).
  const drawing = useMemo(() => {
    if (width <= 0) return null;
    const grid = kcalGridValues(max, goal);
    const shape = (p: BarShapeProps) => <CapsuleBar bar={p} row={rows[p.index]} max={max} maxBar={maxBar} clipId={clipId} />;
    const xTick = (p: XAxisTickContentProps) => {
      const r = rows[p.index];
      if (!r) return null;
      const text = r.isToday ? t('day.today') : shortWeekday(r.epochDay, tag);
      let x = p.payload.coordinate;
      if (p.index === rows.length - 1) {
        // The last day's label may borrow the axis margin when it is wider than its band
        // ("Сьогодні" at 360 px), so it never runs into the day before it.
        const band = plotW / rows.length;
        const estimate = text.length * 0.6 * 12;
        x = Math.min(Math.max(x, x - band / 2 + estimate / 2 + 1), width - estimate / 2);
      }
      return (
        <text className={r.isToday ? 'xlab today' : 'xlab'} x={x} y={Number(p.y) + 12} textAnchor="middle">
          {text}
        </text>
      );
    };
    const yTick = (p: YAxisTickContentProps) => (
      <text className="axis" x={width - 2} y={Number(p.y) + 4} textAnchor="end">
        {nf.format(Number(p.payload.value))}
      </text>
    );
    return (
      <BarChart width={width} height={height} data={rows} margin={MARGIN} barCategoryGap="22%" accessibilityLayer={false}>
        <PlotClip id={clipId} />
        <CartesianGrid vertical={false} horizontalValues={grid} />
        <XAxis dataKey="epochDay" axisLine={false} tickLine={false} interval={0} height={X_AXIS_H} tick={xTick} />
        <YAxis
          orientation="right"
          width={AXIS_W}
          domain={[0, max]}
          ticks={grid}
          axisLine={false}
          tickLine={false}
          tick={yTick}
          allowDataOverflow
        />
        <Bar dataKey="eaten" isAnimationActive={false} activeBar={false} shape={shape} />
        {goal != null && goal > 0 && <GoalLine goal={goal} label={nf.format(goal)} chartWidth={width} />}
      </BarChart>
    );
    // plotW follows from width.
  }, [width, height, rows, max, maxBar, clipId, goal, nf, t, tag]);

  return (
    <Card className={['chart-card', 'kcal-card', className].filter(Boolean).join(' ')} labelledBy={titleId} style={style}>
      <CardHead id={titleId} label={t('week.kcal')} meta={t('week.last7')} />
      <div className="sel-head" aria-live="polite">
        <span className="s-l">{label}</span>
        <span className="s-v num">
          <Digits value={row?.eaten ?? 0} />
          <span className="unit">{unit}</span>
        </span>
        {desc && <span className={over ? 's-d over' : 's-d'}>{desc}</span>}
      </div>
      <div
        className="chart kcal-chart"
        role="group"
        aria-labelledby={titleId}
        tabIndex={0}
        onKeyDown={onKeyDown}
        onClick={onClick}
        onPointerMove={onPointerMove}
        onPointerLeave={() => setHovered(null)}
      >
        <div className="chart-plot" ref={plotRef} aria-hidden="true">
          <PickContext.Provider value={pick}>{drawing}</PickContext.Provider>
        </div>
        <ChartTable
          caption={t('week.kcal')}
          head={[t('nav.day'), cap1(t('week.eaten')), cap1(t('week.target'))]}
          rows={rows.map((r) => [
            r.isToday ? t('day.today') : dayLabel(r.epochDay, tag),
            `${nf.format(r.eaten)} ${unit}`,
            r.target != null ? `${nf.format(r.target)} ${unit}` : '—',
          ])}
        />
      </div>
      <div className="legend">
        <span>
          <i className="lg-ok" aria-hidden="true" />
          {t('week.legend_ok')}
        </span>
        <span>
          <i className="lg-over" aria-hidden="true" />
          {t('week.legend_over')}
        </span>
        <span>
          <i className="lg-today" aria-hidden="true" />
          {t('week.today_so_far')}
        </span>
      </div>
    </Card>
  );
}

/** The plot, as a clip for the bars: they rise out of the axis, so nothing may show below it. */
function PlotClip({ id }: { id: string }) {
  const plot = usePlotArea();
  if (!plot) return null;
  return (
    <defs>
      <clipPath id={id}>
        <rect x={plot.x - 8} y={plot.y - 40} width={plot.width + 16} height={plot.height + 40} />
      </clipPath>
    </defs>
  );
}

interface CapsuleBarProps {
  bar: BarShapeProps;
  row: WeekDay | undefined;
  max: number;
  maxBar: number;
  clipId: string;
}

/**
 * One day: the ghost capsule up to its goal, then (in one group that rises on
 * the first open) the over cap when over and the eaten capsule in front of it.
 */
function CapsuleBar({ bar, row, max, maxBar, clipId }: CapsuleBarProps) {
  const { selected, exploring } = useContext(PickContext);
  if (!row) return null;
  const { x, width, background, index } = bar;
  const dim = exploring && index !== selected;
  const bw = Math.max(4, Math.min(maxBar, width));
  const bx = x + (width - bw) / 2;
  const r = bw / 2;
  const top = background?.y ?? 0;
  const plotH = background?.height ?? 0;
  const base = top + plotH;
  const yOf = (v: number) => base - (Math.max(0, v) / max) * plotH;
  const target = row.target != null && row.target > 0 ? row.target : null;
  const goalY = target != null ? yOf(target) : null;
  const isOver = target != null && verdict(row.eaten, target) === 'over';

  let body = null;
  if (isOver && goalY != null) {
    const capTop = Math.min(yOf(row.eaten), goalY - r);
    const eatenH = Math.max(bw, base - goalY);
    body = (
      <>
        <rect className="over-cap" x={bx} y={capTop} width={bw} height={goalY - capTop + r} rx={r} />
        <rect className="eaten" x={bx} y={base - eatenH} width={bw} height={eatenH} rx={r} />
      </>
    );
  } else if (row.eaten > 0) {
    const shown = target != null ? Math.min(row.eaten, target) : row.eaten;
    const h = Math.max(bw, base - yOf(shown));
    body = <rect className="eaten" x={bx} y={base - h} width={bw} height={h} rx={r} />;
  }

  const cls = ['bar-col', row.isToday ? 'today' : '', dim ? 'dim' : ''].filter(Boolean).join(' ');
  return (
    <g className={cls} clipPath={`url(#${clipId})`}>
      {goalY != null && <rect className="ghost" x={bx} y={goalY} width={bw} height={Math.max(bw, base - goalY)} rx={r} />}
      {body && (
        <g className="bar-g a-bar-rise" style={{ '--d': 420 + index * 55 } as CSSProperties}>
          {body}
        </g>
      )}
    </g>
  );
}

/** The goal: a dashed line across the plot that wipes in, and its value in a pill in the axis margin. */
function GoalLine({ goal, label, chartWidth }: { goal: number; label: string; chartWidth: number }) {
  const plot = usePlotArea();
  const scale = useYAxisScale();
  if (!plot || !scale) return null;
  const gy = scale(goal);
  if (gy == null || !Number.isFinite(gy)) return null;
  const right = plot.x + plot.width;
  return (
    <ZIndexLayer zIndex={DefaultZIndexes.line}>
      <g className="goal-wipe a-wipe">
        <line className="goal" x1={plot.x} x2={right - 4} y1={gy} y2={gy} />
      </g>
      <g className="goal-pill a-pop">
        <rect x={right + 1} y={gy - 10} width={Math.max(0, chartWidth - right - 1)} height={20} rx={10} />
        <text x={(right + 1 + chartWidth) / 2} y={gy + 4} textAnchor="middle">
          {label}
        </text>
      </g>
    </ZIndexLayer>
  );
}
