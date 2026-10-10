package com.nutricart.app.ui.ember

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// The charts (APP-DESIGN.md §5.4, the web's §7: CaloriesChart.tsx and WeightChart.tsx), drawn in one
// Canvas each. Calories: one capsule per day in front of a ghost capsule that reaches the goal, the
// goal as a dashed line with its value in a pill, the over cap hatched in ink and outlined, today
// striped; tap or drag picks a day and the header (ChartHeader) hands its digits off. Weight: a
// smooth line from water blue to weight teal over a soft area, dashed guides at whole kilograms.
// First open (`enter`): bars rise out of the axis, the goal wipes in, its pill pops; the weight line
// draws itself and its last point pops and pings once. Reduced motion: everything at rest at once.

/** One day of the calories chart. [labelShown] = false hides the axis label (Month labels every 7th day). */
@Immutable
data class ChartDay(
    val label: String,
    val kcal: Int,
    val isToday: Boolean,
    val labelShown: Boolean = true,
    /** The header's name for a past day ("Tuesday 6 Oct"); [label] when null. */
    val longLabel: String? = null,
    /** The day's own verdict when known (its own target); null = [kcal] against the chart's target + 5%. */
    val over: Boolean? = null,
)

// The plot's own text grows with the font size only so far: the axis margin, the pill and the day
// bands are fixed widths (TalkBack reads every value from the chart's description instead), as the
// tab labels are capped.
private val AXIS_TEXT_MAX = 13.dp
private val PILL_TEXT_MAX = 12.5.dp
private val LABEL_TEXT_MAX = 14.dp

private fun TextStyle.cappedAt(max: Dp, density: Density): TextStyle = with(density) {
    if (fontSize.isSp && fontSize.toPx() > max.toPx()) copy(fontSize = max.toSp()) else this@cappedAt
}

/** Over the goal by more than the 5% a day is allowed (AdherenceCalculator.TOLERANCE). */
private const val TOLERANCE = .05f

private fun ChartDay.isOver(target: Int): Boolean =
    over ?: (target > 0 && kcal - target > target * TOLERANCE)

/** The top of the plot: room above the goal for its pill (24%) and above the tallest day (4%). */
private fun chartMax(days: List<ChartDay>, target: Int): Float =
    maxOf(target * 1.24f, (days.maxOfOrNull { it.kcal } ?: 0) * 1.04f, 1000f)

/** Gridlines at 0 and 1,000 only; the 1,000 line goes when the goal pill would sit on it. */
private fun gridValues(max: Float, target: Int): List<Int> =
    if (target > 0 && abs(target - 1000) < max * .1f) listOf(0) else listOf(0, 1000)

private const val BAR_RISE_MS = 760
private const val BAR_RISE_START = 420
private const val GOAL_WIPE_START = 760
private const val GOAL_WIPE_MS = 900
private const val PILL_POP_START = 1100
private const val PILL_POP_MS = 620

/**
 * Calories by day: ghost goal capsules, Ember bars, the over cap (hatch + outline), today striped, a
 * dashed target line with its pill, the right axis 0 and 1,000. Tapping or dragging selects a day.
 *
 * - [selected] null = nothing picked yet (the header shows today, nothing dims); a picked day keeps
 *   full strength and the others dim to 38% (K8). [onSelect] gets the day under the finger.
 * - Week: capsules up to 26 dp. Month: thinner capsules. 90 days: hairline bars with no ghosts, an
 *   over day shows the part above the goal in ink (too thin for a hatch).
 * - [enter] (the first open): bars rise out of the axis 420 + up to 55 ms apart (K3), the goal line
 *   wipes in at 760 ms (K4), its pill pops at 1,100 ms (K5).
 * - TalkBack: one focusable node read as the title, each day ("Sat 2,150") and the target; the
 *   arrow keys walk the days on a keyboard. The header is the live region.
 */
@Composable
fun CaloriesChart(
    days: List<ChartDay>,
    target: Int,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 196.dp,
    enter: Boolean = false,
) {
    val c = Ember.colors
    val type = Ember.type
    val reduced = Ember.motion.reduced
    val nf = rememberIntegerFormat()
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val n = days.size

    // First-open choreography on one clock (ms since the chart appeared).
    val play = enter && !reduced && n > 0
    val step = if (n > 1) min(55f, 400f / (n - 1)) else 0f
    val endMs = max(BAR_RISE_START + step * (n - 1) + BAR_RISE_MS, (PILL_POP_START + PILL_POP_MS).toFloat())
    val clock = remember { Animatable(if (play) 0f else Float.MAX_VALUE) }
    LaunchedEffect(Unit) {
        if (play) clock.animateTo(endMs, tween(endMs.toInt(), easing = LinearEasing))
    }

    // The pick: drawn from the last one to the new one over 260 ms (the others dim to .38).
    var pickFrom by remember { mutableStateOf(selected) }
    var pickTo by remember { mutableStateOf(selected) }
    val pickFade = remember { Animatable(1f) }
    LaunchedEffect(selected, reduced) {
        if (selected == pickTo) return@LaunchedEffect
        pickFrom = pickTo
        pickTo = selected
        if (reduced) {
            pickFade.snapTo(1f)
        } else {
            pickFade.snapTo(0f)
            pickFade.animateTo(1f, tween(260, easing = EmberEasing.Out))
        }
    }

    // Text, measured once per data and style.
    val density = LocalDensity.current
    val axisStyle = type.footnote.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = c.label2, fontFeatureSettings = "tnum")
        .cappedAt(AXIS_TEXT_MAX, density)
    val pillStyle = type.caption.copy(fontSize = 11.sp, color = c.label, fontFeatureSettings = "tnum").cappedAt(PILL_TEXT_MAX, density)
    val labelStyle = type.caption.copy(color = c.label2).cappedAt(LABEL_TEXT_MAX, density)
    val todayLabelStyle = type.caption.copy(color = c.tint).cappedAt(LABEL_TEXT_MAX, density)
    val maxKcal = chartMax(days, target)
    val grid = gridValues(maxKcal, target)
    val gridText = grid.map { measurer.measure(nf.format(it), axisStyle) }
    val pillText = if (target > 0) measurer.measure(nf.format(target), pillStyle) else null
    val xText = days.map { d -> if (d.labelShown) measurer.measure(d.label, if (d.isToday) todayLabelStyle else labelStyle) else null }

    // TalkBack: the title, every day and the target in one sentence.
    val title = stringResource(R.string.stats_chart_title)
    val targetNote = if (target > 0) stringResource(R.string.stats_avg_target_note, target) else null
    val summary = remember(days, target, title, targetNote, nf) {
        buildString {
            append(title)
            append(". ")
            append(days.joinToString(", ") { "${it.label} ${nf.format(it.kcal)}" })
            if (targetNote != null) append(". ").append(targetNote)
        }
    }

    val today = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (n - 1)
    val current by rememberUpdatedState(selected ?: today)
    val select by rememberUpdatedState(onSelect)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || n == 0) return@onKeyEvent false
                val next = when (e.key) {
                    Key.DirectionLeft -> current - 1
                    Key.DirectionRight -> current + 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> n - 1
                    else -> return@onKeyEvent false
                }.coerceIn(0, n - 1)
                select(next)
                true
            }
            .clearAndSetSemantics { contentDescription = summary }
            .pointerInput(n) {
                detectTapGestures { if (n > 0) select(dayAt(it.x, size.width - AXIS_WIDTH.toPx(), n)) }
            }
            .pointerInput(n) {
                // Dragging across the bars walks the days, like scrubbing a chart in Health.
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (n > 0) {
                            last = dayAt(it.x, size.width - AXIS_WIDTH.toPx(), n)
                            select(last)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        val i = dayAt(change.position.x, size.width - AXIS_WIDTH.toPx(), n)
                        if (n > 0 && i != last) {
                            last = i
                            select(i)
                        }
                    },
                )
            }
            .drawBehind {
                val geo = KcalGeometry(this, n, target, maxKcal)
                val t = clock.value
                drawGrid(geo, grid, gridText, c)
                for (i in 0 until n) {
                    val d = days[i]
                    val alpha = pickAlpha(i, pickFrom, pickTo, pickFade.value)
                    val rise = if (play) EmberEasing.Snappy.transform(((t - BAR_RISE_START - step * i) / BAR_RISE_MS).coerceIn(0f, 1f)) else 1f
                    drawDay(geo, i, d, target, c, alpha, rise)
                }
                if (target > 0 && pillText != null) {
                    val wipe = if (play) EmberEasing.InOut.transform(((t - GOAL_WIPE_START) / GOAL_WIPE_MS).coerceIn(0f, 1f)) else 1f
                    val pop = if (play) EmberEasing.Bouncy.transform(((t - PILL_POP_START) / PILL_POP_MS).coerceIn(0f, 1f)) else 1f
                    drawGoal(geo, pillText, c, wipe, pop)
                }
                drawXLabels(geo, xText)
                if (focused) {
                    drawRoundRect(
                        c.focus, topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(3.dp.toPx()),
                    )
                }
            },
    )
}

private val AXIS_WIDTH = 44.dp

/** The day under [x]: the plot is cut into equal bands (the axis margin belongs to the last day). */
private fun dayAt(x: Float, plotW: Float, n: Int): Int =
    floor(x / plotW.coerceAtLeast(1f) * n).toInt().coerceIn(0, max(0, n - 1))

/** The pick's dimming: nothing dims until a day is picked; then the others go to .38. */
private fun pickAlpha(i: Int, from: Int?, to: Int?, fade: Float): Float {
    fun of(sel: Int?) = if (sel == null || sel == i) 1f else .38f
    val a = of(from)
    val b = of(to)
    return a + (b - a) * fade.coerceIn(0f, 1f)
}

/** The calories plot's frame, in pixels: a right axis 44 dp wide, x labels 26 dp tall, 8 dp on top. */
private class KcalGeometry(scope: DrawScope, val n: Int, target: Int, val max: Float) {
    val w = scope.size.width
    val h = scope.size.height
    val axisW = with(scope) { AXIS_WIDTH.toPx() }
    val top = with(scope) { 8.dp.toPx() }
    val base = h - with(scope) { 26.dp.toPx() }
    val plotW = (w - axisW).coerceAtLeast(1f)
    val band = plotW / n.coerceAtLeast(1)
    val bw = min(with(scope) { 26.dp.toPx() }, band * .56f).coerceAtLeast(with(scope) { 1.dp.toPx() })
    val narrow = band < with(scope) { 6.dp.toPx() }
    val goalY: Float? = if (target > 0) y(target.toFloat()) else null

    fun y(v: Float) = base - (v.coerceAtLeast(0f) / max) * (base - top)
    fun cx(i: Int) = band * i + band / 2f
}

private fun DrawScope.drawGrid(geo: KcalGeometry, grid: List<Int>, labels: List<TextLayoutResult>, c: EmberColors) {
    grid.forEachIndexed { k, v ->
        val y = geo.y(v.toFloat())
        drawLine(c.sep, Offset(0f, y), Offset(geo.plotW, y), strokeWidth = 1.dp.toPx())
        val text = labels[k]
        drawText(text, topLeft = Offset(geo.w - 2.dp.toPx() - text.size.width, y + 4.dp.toPx() - text.firstBaseline))
    }
}

/**
 * One day: the ghost capsule up to the goal, then (in one group that rises out of the axis on the
 * first open) the over cap when over and the eaten capsule in front of it.
 */
private fun DrawScope.drawDay(geo: KcalGeometry, i: Int, d: ChartDay, target: Int, c: EmberColors, alpha: Float, rise: Float) {
    val bw = geo.bw
    val x = geo.cx(i) - bw / 2f
    // A dimmed day fades as one group (as the web's column opacity): the cap never shows through its bar.
    if (alpha < 1f) {
        drawIntoCanvas { it.saveLayer(Rect(x - 4.dp.toPx(), 0f, x + bw + 4.dp.toPx(), geo.h), Paint().apply { this.alpha = alpha }) }
    }
    drawDayShapes(geo, x, d, target, c, rise)
    if (alpha < 1f) drawIntoCanvas { it.restore() }
}

private fun DrawScope.drawDayShapes(geo: KcalGeometry, x: Float, d: ChartDay, target: Int, c: EmberColors, rise: Float) {
    val bw = geo.bw
    val r = CornerRadius(bw / 2f)
    val goalY = geo.goalY
    if (goalY != null && !geo.narrow) {
        val gh = max(bw, geo.base - goalY)
        drawRoundRect(c.fill2, Offset(x, geo.base - gh), Size(bw, gh), r)
        drawRoundRect(c.sep, Offset(x, geo.base - gh), Size(bw, gh), r, style = Stroke(1.dp.toPx()))
    }
    if (d.kcal <= 0) {
        // Nothing logged on a past day: a short grey mark on the axis, so an empty day is not a gap.
        if (!d.isToday) drawRoundRect(c.label4, Offset(x, geo.base - 2.dp.toPx()), Size(bw, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        return
    }
    // Over: the goal line's y, where the eaten capsule stops and the over cap starts; null otherwise.
    val overAt = goalY?.takeIf { d.isOver(target) }
    val eatenTop = overAt ?: geo.y(if (target > 0) min(d.kcal, target).toFloat() else d.kcal.toFloat())
    val eatenH = max(bw, geo.base - eatenTop)
    val capTop = if (overAt != null) min(geo.y(d.kcal.toFloat()), overAt - bw / 2f) else geo.base - eatenH
    val bodyH = geo.base - capTop
    val dy = (1f - rise) * bodyH * 1.01f
    // The bars rise out of the axis: nothing shows below it.
    clipRect(left = -8.dp.toPx(), top = geo.top - 40.dp.toPx(), right = geo.plotW + 8.dp.toPx(), bottom = geo.base) {
        translate(top = dy) {
            if (overAt != null) {
                val capH = overAt - capTop + bw / 2f
                if (geo.narrow) {
                    drawRect(c.overLap, Offset(x, capTop), Size(bw, capH))
                } else {
                    drawRoundRect(EmberBrushes.hatchOver(c, this), Offset(x, capTop), Size(bw, capH), r)
                    val o = (if (bw > 10.dp.toPx()) 1.5.dp else 1.dp).toPx()
                    drawRoundRect(
                        c.overLap, Offset(x + o / 2f, capTop + o / 2f), Size(bw - o, capH - o),
                        CornerRadius((bw - o) / 2f), style = Stroke(o),
                    )
                }
            }
            val top = geo.base - eatenH
            val fill: Brush = if (d.isToday) {
                EmberBrushes.stripesToday(c, this)
            } else {
                Brush.verticalGradient(0f to c.ember1, 1f to c.ember2, startY = top, endY = geo.base)
            }
            drawRoundRect(fill, Offset(x, top), Size(bw, eatenH), r)
        }
    }
}

/** The goal: a dashed line across the plot that wipes in, and its value in a pill in the axis margin. */
private fun DrawScope.drawGoal(geo: KcalGeometry, label: TextLayoutResult, c: EmberColors, wipe: Float, pop: Float) {
    val gy = geo.goalY ?: return
    if (wipe > 0f) {
        clipRect(right = geo.plotW * wipe) {
            drawLine(
                c.label2, Offset(0f, gy), Offset(geo.plotW - 4.dp.toPx(), gy), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
    if (pop <= 0f) return
    val left = geo.plotW + 1.dp.toPx()
    val pw = geo.w - left
    val ph = 20.dp.toPx()
    val center = Offset(left + pw / 2f, gy)
    val s = .4f + .6f * pop
    scale(s, pivot = center) {
        val a = pop.coerceIn(0f, 1f)
        drawRoundRect(c.surface, Offset(left, gy - ph / 2f), Size(pw, ph), CornerRadius(ph / 2f), alpha = a)
        drawRoundRect(c.sepStrong, Offset(left, gy - ph / 2f), Size(pw, ph), CornerRadius(ph / 2f), alpha = a, style = Stroke(1.dp.toPx()))
        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, gy - label.size.height / 2f), alpha = a)
    }
}

/**
 * Day labels under their bars; a label wider than its band may borrow room but never leaves the
 * chart, and one that would run into its neighbour is left out (today's, drawn first, always stays).
 */
private fun DrawScope.drawXLabels(geo: KcalGeometry, labels: List<TextLayoutResult?>) {
    val baseline = geo.h - 6.dp.toPx()
    var limit = Float.MAX_VALUE
    for (i in labels.indices.reversed()) {
        val text = labels[i] ?: continue
        val half = text.size.width / 2f
        val x = geo.cx(i).coerceIn(half, geo.w - half)
        if (x + half > limit - 4.dp.toPx()) continue
        drawText(text, topLeft = Offset(x - half, baseline - text.firstBaseline))
        limit = x - half
    }
}

/**
 * The selection header above the chart: the day's kcal and how it compares with [target].
 * "Today so far" / the day's name, its kcal (handing off digit by digit as the pick moves), and one
 * line: "855 under the 2,240 target", "190 over the 2,240 target" (bold, so over never rests on
 * colour) or "40 over · within 5%, on plan". A polite live region, 64 dp tall at least so the chart
 * below never jumps.
 */
@Composable
fun ChartHeader(day: ChartDay, target: Int, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val type = Ember.type
    val nf = rememberIntegerFormat()
    val name = if (day.isToday) stringResource(R.string.chart_today_so_far) else day.longLabel ?: day.label
    val diff = nf.format(abs(day.kcal - target).toLong())
    val goal = nf.format(target.toLong())
    val over = target > 0 && day.isOver(target)
    val verdict = when {
        day.kcal <= 0 && !day.isToday -> stringResource(R.string.legend_not_logged)
        target <= 0 -> null
        over -> stringResource(R.string.chart_over, diff, goal)
        day.kcal > target -> stringResource(R.string.chart_within, diff)
        else -> stringResource(R.string.chart_under, diff, goal)
    }
    Column(
        modifier
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(name, style = type.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2))
        NumberWithUnit(day.kcal.toLong(), stringResource(R.string.kcal_unit), type.stat, format = nf)
        if (verdict != null) {
            BasicText(
                verdict,
                style = if (over) type.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label) else type.footnote.copy(color = c.label2),
            )
        }
    }
}

/**
 * The calories chart's key: On plan (the Ember bar), Over target (the ink hatch), Today so far (the
 * stripes), in caption type; it wraps on a narrow screen or at a large font size.
 */
@Composable
fun CaloriesChartLegend(modifier: Modifier = Modifier) {
    val c = Ember.colors
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(stringResource(R.string.legend_on_plan), 10.dp) {
            drawRoundRect(Brush.verticalGradient(0f to c.ember1, 1f to c.ember2), cornerRadius = CornerRadius(3.dp.toPx()))
        }
        LegendItem(stringResource(R.string.legend_over), 10.dp) {
            val cr = CornerRadius(3.dp.toPx())
            drawRoundRect(EmberBrushes.hatchOver(c, this), cornerRadius = cr)
            val o = 1.dp.toPx()
            drawRoundRect(c.overLap, Offset(o / 2f, o / 2f), Size(size.width - o, size.height - o), cr, style = Stroke(o))
        }
        LegendItem(stringResource(R.string.legend_today), 10.dp) {
            drawRoundRect(EmberBrushes.stripesToday(c, this), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Weight
// ---------------------------------------------------------------------------------------------

private const val LINE_DRAW_START = 560
private const val LINE_DRAW_MS = 1100
private const val AREA_START = 1000
private const val AREA_MS = 560
private const val DOT_START = 1600
private const val DOT_MS = 620
private const val PING_START = 1700
private const val PING_MS = 1100

/**
 * Weight per day ([points] = epochDay to kg, oldest first): water → teal line over a fading area.
 *
 * - A smooth (monotone) line, 3 dp, from water blue to weight teal; the area under it fades from
 *   weight at 26% to nothing; one or two dashed guides at whole kilograms with their values on the
 *   right; the first, middle and last dates under the plot; the latest point as a ringed dot.
 * - [enter] (the first open): the line draws itself (K6, 560 ms in), the area fades up at 1 s, the
 *   last point pops at 1.6 s and pings once (K7).
 * - Fewer than two points: nothing is drawn (the card shows its hint instead).
 * - TalkBack: one node with every weighing ("10 Sep 79.6 kg, …").
 */
@Composable
fun WeightLineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    enter: Boolean = false,
) {
    val c = Ember.colors
    val type = Ember.type
    val reduced = Ember.motion.reduced
    val locale = LocalConfiguration.current.locales[0]
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val kg = rememberDecimalFormat(1)
    val play = enter && !reduced && points.size >= 2
    val endMs = (PING_START + PING_MS).toFloat()
    val clock = remember { Animatable(if (play) 0f else Float.MAX_VALUE) }
    LaunchedEffect(Unit) {
        if (play) clock.animateTo(endMs, tween(endMs.toInt(), easing = LinearEasing))
    }

    val series = remember(points) { WeightSeries.of(points) }
    val dates = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    }
    val density = LocalDensity.current
    val axisStyle = type.footnote.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = c.label2, fontFeatureSettings = "tnum")
        .cappedAt(AXIS_TEXT_MAX, density)
    val labelStyle = type.caption.copy(color = c.label2).cappedAt(LABEL_TEXT_MAX, density)
    val guideText = series?.guides?.map { measurer.measure(kg.format(it), axisStyle) }.orEmpty()
    val dateText = series?.let { s ->
        listOf(s.from, (s.from + s.to) / 2, s.to).map { measurer.measure(LocalDate.ofEpochDay(it).format(dates), labelStyle) }
    }.orEmpty()
    val described = points.map { (day, w) -> "${LocalDate.ofEpochDay(day).format(dates)} ${stringResource(R.string.weight_kg_value, w)}" }
        .joinToString(", ")

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { if (points.size >= 2) contentDescription = described }
            .drawBehind {
                val s = series ?: return@drawBehind
                val geo = WeightGeometry(this, s)
                val t = clock.value
                s.guides.forEachIndexed { k, v ->
                    val y = geo.y(v)
                    drawLine(
                        c.sep, Offset(0f, y), Offset(geo.plotW, y), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                    )
                    val text = guideText[k]
                    drawText(text, topLeft = Offset(geo.w - 2.dp.toPx() - text.size.width, y + 4.dp.toPx() - text.firstBaseline))
                }
                val xs = s.points.map { geo.x(it.first) }
                val ys = s.points.map { geo.y(it.second) }
                val line = monotonePath(xs, ys)

                // The area under the line, fading up after the line has started to draw.
                val area = if (play) EmberEasing.Out.transform(((t - AREA_START) / AREA_MS).coerceIn(0f, 1f)) else 1f
                if (area > 0f) {
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(xs.last(), geo.base)
                        lineTo(xs.first(), geo.base)
                        close()
                    }
                    translate(top = 8.dp.toPx() * (1f - area)) {
                        drawPath(fill, Brush.verticalGradient(listOf(c.weight.copy(alpha = .26f), c.weight.copy(alpha = 0f)), startY = geo.top, endY = geo.base), alpha = area)
                    }
                }

                val draw = if (play) EmberEasing.InOut.transform(((t - LINE_DRAW_START) / LINE_DRAW_MS).coerceIn(0f, 1f)) else 1f
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val ink = Brush.horizontalGradient(listOf(c.water, c.weight), startX = xs.first(), endX = xs.last())
                if (draw >= 1f) {
                    drawPath(line, ink, style = stroke)
                } else if (draw > 0f) {
                    val measure = PathMeasure().apply { setPath(line, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * draw, part, true)
                    drawPath(part, ink, style = stroke)
                }

                // The latest point: a ringed dot that pops, and one ping.
                val last = Offset(xs.last(), ys.last())
                val ping = if (play) ((t - PING_START) / PING_MS).coerceIn(0f, 1f) else 1f
                if (ping > 0f && ping < 1f) {
                    val e = EmberEasing.Out.transform(ping)
                    drawCircle(c.weight, radius = 6.dp.toPx() * (1f + 1.6f * e), center = last, alpha = .7f * (1f - e), style = Stroke(2.dp.toPx()))
                }
                val dot = if (play) EmberEasing.Bouncy.transform(((t - DOT_START) / DOT_MS).coerceIn(0f, 1f)) else 1f
                if (dot > 0f) {
                    scale(.4f + .6f * dot, pivot = last) {
                        val a = dot.coerceIn(0f, 1f)
                        drawCircle(c.surface, radius = 5.5.dp.toPx(), center = last, alpha = a)
                        drawCircle(c.weight, radius = 5.5.dp.toPx(), center = last, alpha = a, style = Stroke(2.5.dp.toPx()))
                    }
                }

                // First, middle and last dates: anchored at the start, the centre and the end; a date that
                // would run into one already drawn (a short series) is left out, the last one wins.
                val baseline = geo.base + 18.dp.toPx()
                val placed = ArrayList<ClosedFloatingPointRange<Float>>()
                val gap = 8.dp.toPx()
                listOf(2, 0, 1).forEach { k ->
                    val text = dateText.getOrNull(k) ?: return@forEach
                    val x = when (k) {
                        0 -> geo.x(s.from)
                        1 -> geo.x((s.from + s.to) / 2) - text.size.width / 2f
                        else -> geo.x(s.to) - text.size.width
                    }.coerceIn(0f, (geo.plotW - text.size.width).coerceAtLeast(0f))
                    val span = (x - gap)..(x + text.size.width + gap)
                    if (placed.any { it.start < span.endInclusive && span.start < it.endInclusive }) return@forEach
                    placed += span
                    drawText(text, topLeft = Offset(x, baseline - text.firstBaseline))
                }
            },
    )
}

/** The weight series' axis: a little room round the line, whole-kilogram guides. */
private class WeightSeries(
    val points: List<Pair<Long, Double>>,
    val from: Long,
    val to: Long,
    val lo: Double,
    val hi: Double,
    val guides: List<Double>,
) {
    companion object {
        fun of(points: List<Pair<Long, Double>>): WeightSeries? {
            if (points.size < 2) return null
            val values = points.map { it.second }
            val min = values.min()
            val max = values.max()
            val pad = max(.3, (max - min) * .15)
            val lo = min - pad
            val hi = max + pad
            return WeightSeries(points, points.first().first, max(points.last().first, points.first().first + 1), lo, hi, guides(lo, hi))
        }

        /** Whole kilograms inside the axis, at most two (the outer ones, so they frame the line). */
        private fun guides(lo: Double, hi: Double): List<Double> {
            val whole = (ceil(lo).toInt()..floor(hi).toInt()).map { it.toDouble() }
            if (whole.isEmpty()) {
                val half = ceil(lo * 2) / 2
                return if (half <= hi) listOf(half) else emptyList()
            }
            if (whole.size <= 2) return whole
            if (whole.size == 3) return listOf(whole[0], whole[2])
            val step = max(1, (whole.size - 1) / 3)
            return listOf(whole[step], whole[whole.size - 1 - step])
        }
    }
}

/** The weight plot's frame: a right axis 40 dp wide, dates 24 dp tall, 14 dp on top, 6 dp inside. */
private class WeightGeometry(scope: DrawScope, private val s: WeightSeries) {
    val w = scope.size.width
    val h = scope.size.height
    val top = with(scope) { 14.dp.toPx() }
    val base = h - with(scope) { 24.dp.toPx() }
    val plotW = (w - with(scope) { 40.dp.toPx() }).coerceAtLeast(1f)
    private val pad = with(scope) { 6.dp.toPx() }

    fun x(day: Long): Float = pad + (day - s.from).toFloat() / (s.to - s.from).toFloat() * (plotW - 2 * pad)
    fun y(kg: Double): Float = top + ((s.hi - kg) / (s.hi - s.lo)).toFloat() * (base - top)
}

/**
 * A monotone cubic through the points (d3's curveMonotoneX, what the web's recharts line uses): smooth,
 * and it never overshoots a weighing, so the line never shows a weight nobody had.
 */
private fun monotonePath(xs: List<Float>, ys: List<Float>): Path {
    val path = Path()
    val n = xs.size
    if (n == 0) return path
    path.moveTo(xs[0], ys[0])
    if (n == 1) return path
    if (n == 2) {
        path.lineTo(xs[1], ys[1])
        return path
    }
    fun slope3(i: Int): Float {
        val h0 = xs[i] - xs[i - 1]
        val h1 = xs[i + 1] - xs[i]
        val s0 = if (h0 != 0f) (ys[i] - ys[i - 1]) / h0 else 0f
        val s1 = if (h1 != 0f) (ys[i + 1] - ys[i]) / h1 else 0f
        val p = if (h0 + h1 != 0f) (s0 * h1 + s1 * h0) / (h0 + h1) else 0f
        val m = (sign(s0) + sign(s1)) * minOf(abs(s0), abs(s1), .5f * abs(p))
        return if (m.isFinite()) m else 0f
    }
    val t = FloatArray(n)
    for (i in 1 until n - 1) t[i] = slope3(i)
    fun slope2(i: Int, j: Int, tj: Float): Float {
        val h = xs[j] - xs[i]
        return if (h != 0f) (3f * (ys[j] - ys[i]) / h - tj) / 2f else tj
    }
    t[0] = slope2(0, 1, t[1])
    t[n - 1] = slope2End(xs, ys, t[n - 2])
    for (i in 0 until n - 1) {
        val dx = (xs[i + 1] - xs[i]) / 3f
        path.cubicTo(xs[i] + dx, ys[i] + dx * t[i], xs[i + 1] - dx, ys[i + 1] - dx * t[i + 1], xs[i + 1], ys[i + 1])
    }
    return path
}

/** d3's end tangent: (3 × the last secant − the tangent before it) / 2. */
private fun slope2End(xs: List<Float>, ys: List<Float>, before: Float): Float {
    val n = xs.size
    val h = xs[n - 1] - xs[n - 2]
    return if (h != 0f) (3f * (ys[n - 1] - ys[n - 2]) / h - before) / 2f else before
}
