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
  Area,
  CartesianGrid,
  ComposedChart,
  Curve,
  DefaultZIndexes,
  Line,
  XAxis,
  YAxis,
  ZIndexLayer,
  usePlotArea,
  useXAxisScale,
  useYAxisScale,
  type LineDrawShapeProps,
  type XAxisTickContentProps,
  type YAxisTickContentProps,
} from 'recharts';
import { useI18n } from '../../lib/i18n';
import { useMediaQuery } from '../../lib/motion';
import { axisDate, dayLabel, type WeightPoint, type WeightSeries } from '../../lib/weekView';
import { Card, CardHead } from '../ui/Card';
import { Digits } from '../ui/Digits';
import { Icon } from '../ui/Icon';
import { ChartTable } from './ChartTable';
import { useChartWidth } from './useChartWidth';

/**
 * Weight over the last 30 days (DESIGN.md §7.2): the latest value, how far it
 * moved in the window, and a smooth line from water blue to weight teal over
 * a soft area, with dashed guides at whole kilograms.
 *
 * - Tap a point (or hover it with a mouse, or use the arrow keys once the chart
 *   has focus) and the header shows that day's date and weight, a thin rule and
 *   a dot marking it on the line, as the calories chart's header does for its
 *   days; until then it shows the latest weight and when it was taken.
 * - On the first open the line draws itself (pathLength 1, the dash offset runs
 *   1 to 0), the area fades up after it and the last point pops and pings once
 *   (motion.css). recharts draws it all with its own animation off.
 * - The drawing is aria-hidden; the weights are a visually hidden table.
 */

const AXIS_W = 40;
const HEIGHT = 150;
const X_AXIS_H = 24;
/** The dates' padding inside the plot, so the first and last points are not cut by its edges. */
const X_PAD = 6;
const MARGIN = { top: 14, right: 0, bottom: 0, left: 0 };
const HOVER_QUERY = '(hover: hover) and (pointer: fine)';

/** The point the reader picked, for its mark inside the drawing (null until they pick one). */
const MarkContext = createContext<WeightPoint | null>(null);

interface WeightChartProps {
  series: WeightSeries;
  /** The last point pings once (the first open of the page only). */
  ping?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function WeightChart({ series, ping = false, className, style }: WeightChartProps) {
  const { t, tag } = useI18n();
  const kg = useMemo(() => new Intl.NumberFormat(tag, { minimumFractionDigits: 1, maximumFractionDigits: 1 }), [tag]);
  const signedKg = useMemo(
    () => new Intl.NumberFormat(tag, { minimumFractionDigits: 1, maximumFractionDigits: 1, signDisplay: 'exceptZero' }),
    [tag],
  );
  const fineHover = useMediaQuery(HOVER_QUERY, false);
  const [plotRef, width] = useChartWidth<HTMLDivElement>();
  const titleId = `wt-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const { points, latest, delta, lo, hi, guides, from, to } = series;
  const unit = t('unit.kg');

  // The latest weight is shown until the reader picks a day. A pick is kept as its day, not its
  // place in the list, so a weight arriving later (a live update) never moves it to another day.
  const [picked, setPicked] = useState<number | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const lastIndex = points.length - 1;
  const chosen = hovered ?? picked;
  const found = chosen == null ? -1 : points.findIndex((p) => p.epochDay === chosen);
  const selected = found >= 0 ? found : lastIndex;
  const exploring = found >= 0;
  const shown = points[selected] ?? latest;
  const when = shown ? (shown.epochDay === to ? t('day.today') : dayLabel(shown.epochDay, tag)) : '';

  // A tap, click or hover picks the point nearest the pointer along the dates axis.
  const plotW = Math.max(1, width - AXIS_W);
  const xOf = (day: number) => X_PAD + ((day - from) / Math.max(1, to - from)) * Math.max(1, plotW - 2 * X_PAD);
  const nearestAt = (clientX: number, el: HTMLElement) => {
    const x = clientX - el.getBoundingClientRect().left;
    let best = lastIndex;
    let bestGap = Infinity;
    points.forEach((p, i) => {
      const gap = Math.abs(xOf(p.epochDay) - x);
      if (gap < bestGap) {
        best = i;
        bestGap = gap;
      }
    });
    return best;
  };
  const onClick = (e: MouseEvent<HTMLDivElement>) => {
    if (points.length === 0) return;
    setPicked(points[nearestAt(e.clientX, e.currentTarget)].epochDay);
  };
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (!fineHover || e.pointerType !== 'mouse' || points.length === 0) return;
    const day = points[nearestAt(e.clientX, e.currentTarget)].epochDay;
    if (day !== hovered) setHovered(day);
  };
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    // Left and right walk the weights; up and down are left to scroll the page.
    let next: number | null = null;
    if (e.key === 'ArrowLeft') next = Math.max(0, selected - 1);
    else if (e.key === 'ArrowRight') next = Math.min(lastIndex, selected + 1);
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = lastIndex;
    if (next == null || lastIndex < 0) return;
    e.preventDefault();
    setHovered(null);
    setPicked(points[next].epochDay);
  };

  // The drawing depends on the series and the size only, never on the pick: recharts rebuilds the
  // line's element whenever the chart re-renders with new props, which would replay its first-open
  // draw on every tap or hover. The picked point reaches its mark through a context instead.
  const drawing = useMemo(() => {
    if (width <= 0) return null;
    const mid = Math.round((from + to) / 2);
    const xTick = (p: XAxisTickContentProps) => {
      const i = p.index;
      const anchor = i === 0 ? 'start' : i === 2 ? 'end' : 'middle';
      return (
        <text className="xlab" x={p.payload.coordinate} y={Number(p.y) + 12} textAnchor={anchor}>
          {axisDate(Number(p.payload.value), tag)}
        </text>
      );
    };
    const yTick = (p: YAxisTickContentProps) => (
      <text className="axis" x={width - 2} y={Number(p.y) + 4} textAnchor="end">
        {kg.format(Number(p.payload.value))}
      </text>
    );
    // The line and its last point in one shape: the curve gets pathLength 1 so it can draw itself.
    const lineShape = (p: LineDrawShapeProps) => {
      const last = p.points?.at(-1);
      const hasLast = last != null && last.x != null && last.y != null;
      return (
        <g className="wt-line-g">
          <Curve type={p.type} points={p.points} connectNulls={p.connectNulls} pathLength={1} className="wt-line a-draw" />
          {hasLast && ping && <circle className="wt-ping a-ping" cx={last.x!} cy={last.y!} r={6} />}
          {hasLast && <circle className="wt-dot a-pop" cx={last.x!} cy={last.y!} r={6} />}
        </g>
      );
    };
    return (
      <ComposedChart width={width} height={HEIGHT} data={points} margin={MARGIN} accessibilityLayer={false}>
        <CartesianGrid vertical={false} horizontalValues={guides} strokeDasharray="2 4" />
        <XAxis
          dataKey="epochDay"
          type="number"
          domain={[from, to]}
          ticks={[from, mid, to]}
          interval={0}
          axisLine={false}
          tickLine={false}
          padding={{ left: X_PAD, right: X_PAD }}
          height={X_AXIS_H}
          tick={xTick}
        />
        <YAxis
          orientation="right"
          width={AXIS_W}
          domain={[lo, hi]}
          ticks={guides}
          axisLine={false}
          tickLine={false}
          tick={yTick}
          allowDataOverflow
        />
        <Area
          dataKey="kg"
          type="monotone"
          baseValue={lo}
          stroke="none"
          dot={false}
          activeDot={false}
          isAnimationActive={false}
          className="wt-area a-fade-up"
        />
        <Line dataKey="kg" type="monotone" dot={false} activeDot={false} isAnimationActive={false} shape={lineShape} />
        <PickedMark />
      </ComposedChart>
    );
  }, [width, points, from, to, lo, hi, guides, ping, kg, tag]);

  return (
    <Card metric="weight" className={['chart-card', 'wt-card', className].filter(Boolean).join(' ')} labelledBy={titleId} style={style}>
      <CardHead id={titleId} icon="scale" label={t('week.weight')} meta={t('week.last30')} />
      {shown && (
        <div className="wt-head" aria-live="polite">
          <span className="s-l">{when}</span>
          <span className="big-v num">
            <Digits value={shown.kg} format={(n) => kg.format(n)} />
            <span className="unit">{unit}</span>
          </span>
          {delta != null && (
            <span className={delta > 0 ? 'delta up' : delta < 0 ? 'delta' : 'delta flat'}>
              <span className="delta-v" aria-hidden="true">
                {delta !== 0 && <Icon name={delta < 0 ? 'arrowDown' : 'arrowUp'} />}
                {t('week.weight_delta', { kg: kg.format(Math.abs(delta)) })}
              </span>
              <span className="sr">{t('week.weight_delta', { kg: signedKg.format(delta) })}</span>
            </span>
          )}
        </div>
      )}
      <div
        className="chart wt-chart"
        role="group"
        aria-labelledby={titleId}
        tabIndex={points.length > 1 ? 0 : undefined}
        onKeyDown={onKeyDown}
        onClick={onClick}
        onPointerMove={onPointerMove}
        onPointerLeave={() => setHovered(null)}
      >
        <div className="chart-plot" ref={plotRef} aria-hidden="true">
          <MarkContext.Provider value={exploring ? shown : null}>{drawing}</MarkContext.Provider>
        </div>
        <ChartTable
          caption={t('week.weight')}
          head={[t('nav.day'), t('week.weight')]}
          rows={points.map((p) => [dayLabel(p.epochDay, tag), `${kg.format(p.kg)} ${unit}`])}
        />
      </div>
    </Card>
  );
}

/**
 * The picked point: a hairline down the plot behind the line and a dot on the
 * line above it, placed by the chart's own scales. Both glide to the next pick
 * (charts.css), so they sit at the origin and move by custom properties.
 */
function PickedMark() {
  const point = useContext(MarkContext);
  const plot = usePlotArea();
  const xScale = useXAxisScale();
  const yScale = useYAxisScale();
  if (!point || !plot || !xScale || !yScale) return null;
  const x = xScale(point.epochDay);
  const y = yScale(point.kg);
  if (x == null || y == null || !Number.isFinite(x) || !Number.isFinite(y)) return null;
  const place = { '--x': `${x}px`, '--y': `${y}px` } as CSSProperties;
  return (
    <>
      <ZIndexLayer zIndex={DefaultZIndexes.area + 1}>
        <line className="wt-rule" style={place} x1={0} x2={0} y1={plot.y} y2={plot.y + plot.height} />
      </ZIndexLayer>
      <ZIndexLayer zIndex={DefaultZIndexes.scatter}>
        <circle className="wt-dot wt-sel-dot" style={place} cx={0} cy={0} r={6} />
      </ZIndexLayer>
    </>
  );
}
