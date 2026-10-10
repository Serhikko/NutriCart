package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

// Every number that changes on screen (APP-DESIGN.md §3.4 D, the web's Digits.tsx). There is no
// count-up anywhere: a number either arrives digit by digit (the first open) or hands off only the
// digits that changed, so it never shows an intermediate value, a leading zero or two digits stacked.
//
// - One leaf layout draws the glyphs itself (TextMeasurer), each digit in a window exactly one em
//   tall with soft top and bottom edges; separators (",", ".", "−", the Ukrainian no-break space)
//   are never clipped. The number's text exists once, for TalkBack and tests.
// - Change: in each changed cell the old digit leaves against the direction of the change and is
//   gone by 25% of the run; the new one is invisible until 25%, then arrives on the snappy curve.
//   520 ms, 40 ms apart from the left. (The web lets the old digit linger to 28%; ending it as the
//   new one starts means a cell never holds two glyphs in any frame.)
// - If the number of digits or separators changes, the whole number arrives again instead.
// - A change that comes within one hand-off of the last (a held stepper, fast typing) swaps at
//   once, so the number never lags behind the value.
// - Enter (first open): each digit rises .55 em and fades in, `stepMillis` apart; a separator fades
//   in place with the digit before it.
// - Gradient: one Ember gradient across the whole number though every glyph is drawn on its own.

/** Whole numbers for the current locale: "1,230" in English, "1 230" (no-break space) in Ukrainian. */
@Composable
fun rememberIntegerFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

/** Decimals for the current locale with exactly [digits] fraction digits: "9.7" / "9,7". */
@Composable
fun rememberDecimalFormat(digits: Int): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, digits) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
    }
}

/**
 * A number whose digits change one by one (§3.4 D): only changed cells animate, from [from] (or the
 * previous value) to [value]; [enter] plays the first-open arrival after [delayMillis], [stepMillis]
 * apart. Never intermediate values. [gradient] paints the Ember numeral gradient (≥ 28 sp bold only).
 * TalkBack reads the formatted value once.
 *
 * [from] is the value this number showed before it appeared (back from Add, a day change): it shows
 * [from] and hands off to [value] after [delayMillis]. It is ignored with [enter]. Later changes of
 * [value] hand off at once. Reduced motion: the value, always at rest.
 */
@Composable
fun Digits(
    value: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    format: NumberFormat = rememberIntegerFormat(),
    enter: Boolean = false,
    delayMillis: Int = 0,
    stepMillis: Int = 60,
    gradient: Boolean = false,
    from: Long? = null,
    color: Color = Color.Unspecified,
) {
    val text = format.shown(value)
    DigitsCore(
        text, value.toDouble(), from?.let { format.shown(it) }, from?.toDouble(), style,
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        enter, delayMillis, stepMillis, gradient, color,
    )
}

/** [Digits] for a decimal value (weight, water litres): the format decides the fraction digits. */
@Composable
fun Digits(
    value: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    format: NumberFormat = rememberDecimalFormat(1),
    enter: Boolean = false,
    delayMillis: Int = 0,
    stepMillis: Int = 60,
    gradient: Boolean = false,
    from: Double? = null,
    color: Color = Color.Unspecified,
) {
    val text = format.shown(value)
    DigitsCore(
        text, value, from?.let { format.shown(it) }, from, style,
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        enter, delayMillis, stepMillis, gradient, color,
    )
}

/**
 * [Digits] plus its unit on one baseline: the unit at 56% of the number's size, SemiBold, label2,
 * after a 0.18 em gap; the pair never wraps. [unitSize] overrides the 56% where the context sets a
 * smaller unit (the week average's 20 sp "kcal" next to its 76 sp number, as on the web).
 */
@Composable
fun NumberWithUnit(
    value: Long,
    unit: String,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    format: NumberFormat = rememberIntegerFormat(),
    enter: Boolean = false,
    delayMillis: Int = 0,
    stepMillis: Int = 60,
    gradient: Boolean = false,
    from: Long? = null,
    color: Color = Color.Unspecified,
    unitSize: TextUnit = TextUnit.Unspecified,
) {
    val text = format.shown(value)
    UnitRow(text, unit, numberStyle, unitSize, modifier, if (enter) delayMillis + stepMillis * (text.count(::isDigit) - 1).coerceAtLeast(0) else null) {
        DigitsCore(
            text, value.toDouble(), from?.let { format.shown(it) }, from?.toDouble(), numberStyle, it,
            enter, delayMillis, stepMillis, gradient, color,
        )
    }
}

/** [NumberWithUnit] for a decimal value ("78.4 kg"). */
@Composable
fun NumberWithUnit(
    value: Double,
    unit: String,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    format: NumberFormat = rememberDecimalFormat(1),
    enter: Boolean = false,
    delayMillis: Int = 0,
    stepMillis: Int = 60,
    gradient: Boolean = false,
    from: Double? = null,
    color: Color = Color.Unspecified,
    unitSize: TextUnit = TextUnit.Unspecified,
) {
    val text = format.shown(value)
    UnitRow(text, unit, numberStyle, unitSize, modifier, if (enter) delayMillis + stepMillis * (text.count(::isDigit) - 1).coerceAtLeast(0) else null) {
        DigitsCore(
            text, value, from?.let { format.shown(it) }, from, numberStyle, it,
            enter, delayMillis, stepMillis, gradient, color,
        )
    }
}

/**
 * A big number (the ring centre, the week average, the sheet and recipe kcal) that shrinks to fit
 * its width instead of clipping: at font scale 2.0 the hero numeral steps down towards [minFontSize].
 * With a [unit] it is a [NumberWithUnit], otherwise [Digits]; the motion parameters are theirs.
 * The shrink is a scale of the drawn number (crisp: text is drawn, not a bitmap).
 */
@Composable
fun FittedNumber(
    value: Long,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    unit: String? = null,
    minFontSize: TextUnit = 36.sp,
    format: NumberFormat = rememberIntegerFormat(),
    enter: Boolean = false,
    delayMillis: Int = 0,
    stepMillis: Int = 60,
    gradient: Boolean = false,
    from: Long? = null,
    color: Color = Color.Unspecified,
    unitSize: TextUnit = TextUnit.Unspecified,
) {
    val minScale = if (numberStyle.fontSize.isSpecified && minFontSize.isSpecified && numberStyle.fontSize.value > 0f) {
        (minFontSize.value / numberStyle.fontSize.value).coerceIn(0f, 1f)
    } else {
        1f
    }
    Layout(
        modifier = modifier,
        content = {
            if (unit != null) {
                NumberWithUnit(value, unit, numberStyle, Modifier, format, enter, delayMillis, stepMillis, gradient, from, color, unitSize)
            } else {
                Digits(value, numberStyle, Modifier, format, enter, delayMillis, stepMillis, gradient, from, color)
            }
        },
    ) { measurables, constraints ->
        val p = measurables.first().measure(Constraints(maxHeight = constraints.maxHeight))
        val room = constraints.maxWidth
        val scale = if (room != Constraints.Infinity && p.width > room) max(minScale, room.toFloat() / p.width) else 1f
        val w = (p.width * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (p.height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val lines = buildMap<AlignmentLine, Int> {
            if (p[FirstBaseline] != AlignmentLine.Unspecified) put(FirstBaseline, (p[FirstBaseline] * scale).roundToInt())
            if (p[LastBaseline] != AlignmentLine.Unspecified) put(LastBaseline, (p[LastBaseline] * scale).roundToInt())
        }
        layout(w, h, lines) {
            p.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun UnitRow(
    number: String,
    unit: String,
    style: TextStyle,
    unitSize: TextUnit,
    modifier: Modifier,
    arriveAtMillis: Int?,
    digits: @Composable (Modifier) -> Unit,
) {
    val c = Ember.colors
    val type = Ember.type
    // On an arrival the unit fades in with the last digit, so it never waits on screen alone.
    val arriveAt = arriveAtMillis?.takeIf { !Ember.motion.reduced }
    val shown = remember { Animatable(if (arriveAt != null) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (arriveAt != null) shown.animateTo(1f, tween(ENTER_MS, arriveAt, EmberEasing.Snappy))
    }
    // The web's .unit: 56% of the number after a gap of .18 em of the unit itself (a tenth of the
    // number); a smaller unit keeps that tenth, as the web's week average keeps 8 px before its "kcal".
    val size = if (unitSize.isSpecified) unitSize else style.fontSize * type.unitRatio
    val gap = if (size.isSp && style.fontSize.isSp) {
        with(LocalDensity.current) { max((size * .18f).toDp().value, (style.fontSize * .1f).toDp().value).dp }
    } else {
        0.dp
    }
    val unitStyle = remember(style, size, c.label2) {
        style.copy(
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            color = c.label2,
            letterSpacing = 0.em,
            fontFeatureSettings = null,
            lineHeight = 1.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
    }
    Row(
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString("$number $unit") },
        verticalAlignment = Alignment.Bottom,
    ) {
        digits(Modifier.alignByBaseline())
        BasicText(
            text = unit,
            style = unitStyle,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .alignByBaseline()
                .padding(start = gap)
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The engine
// ---------------------------------------------------------------------------------------------

private fun isDigit(ch: Char) = ch in '0'..'9'

/** The formatted value as it is shown: a real minus sign (U+2212), not the hyphen NumberFormat writes. */
private fun NumberFormat.shown(value: Long): String = format(value).replace('-', '\u2212')
private fun NumberFormat.shown(value: Double): String = format(value).replace('-', '\u2212')

/** Two texts with the same shape differ only in digits, so they can hand off cell by cell. */
private fun shapeOf(text: String) = buildString(text.length) { text.forEach { append(if (isDigit(it)) '0' else it) } }

private const val CHANGE_MS = 520
private const val CHANGE_STAGGER_MS = 40
private const val OUT_GONE_AT = .25f
private const val IN_FROM = .25f
private const val OUT_TRAVEL_EM = .4f
private const val IN_TRAVEL_EM = .5f
private const val ENTER_MS = 560
private const val ENTER_TRAVEL_EM = .55f
private const val RAPID_NANOS = CHANGE_MS * 1_000_000L

/** The whole number arriving digit by digit; one per shape, kept while it runs. */
private class Arrival(val base: Int, val step: Int, digits: Int) {
    val endMs: Float = (base + step * (digits - 1).coerceAtLeast(0) + ENTER_MS).toFloat()
    var started = false
    var done = false
}

/** What one text does when it appears: hand off from [old], arrive with [arrival], or just stand. */
private class Plan(
    val text: String,
    val old: String?,
    val dir: Int,
    val delay: Int,
    val arrival: Arrival?,
    val isChange: Boolean,
    val createdNanos: Long,
) {
    val endMs: Float = (delay + CHANGE_STAGGER_MS * (text.count(::isDigit) - 1).coerceAtLeast(0) + CHANGE_MS).toFloat()
    var started = false
}

/** One number's memory: what it shows, when it last changed, and the two clocks (in ms). */
private class DigitsState {
    var committed: String? = null
    var committedValue = 0.0
    var lastChangeNanos = Long.MIN_VALUE / 4
    var running: Plan? = null
    val handoff = Animatable(0f)
    val entry = Animatable(0f)

    fun plan(
        text: String,
        value: Double,
        fromText: String?,
        fromValue: Double?,
        enter: Boolean,
        delay: Int,
        step: Int,
        reduced: Boolean,
        now: Long,
        current: Plan?,
    ): Plan {
        val digits = text.count(::isDigit)
        val shown = committed
        if (shown == null) {
            // Appearing: arrive, hand off from the value it showed before, or simply stand there.
            return when {
                reduced -> Plan(text, null, 1, 0, null, false, now)
                enter -> Plan(text, null, 1, 0, Arrival(delay, step, digits), false, now)
                fromText != null && fromValue != null && fromText != text ->
                    if (shapeOf(fromText) == shapeOf(text)) {
                        Plan(text, fromText, if (value >= fromValue) 1 else -1, delay, null, false, now)
                    } else {
                        Plan(text, null, 1, 0, Arrival(delay, step, digits), false, now)
                    }
                else -> Plan(text, null, 1, 0, null, false, now)
            }
        }
        val arriving = current?.arrival?.takeIf { !it.done }
        return when {
            reduced -> Plan(text, null, 1, 0, null, true, now)
            shapeOf(shown) != shapeOf(text) -> Plan(text, null, 1, 0, Arrival(0, step, digits), true, now)
            // Still arriving: the new digits simply take their places in the arrival.
            arriving != null -> Plan(text, null, 1, 0, arriving, true, now)
            running != null || now - lastChangeNanos < RAPID_NANOS -> Plan(text, null, 1, 0, null, true, now)
            else -> Plan(text, shown, if (value >= committedValue) 1 else -1, 0, null, true, now)
        }
    }

    fun commit(plan: Plan, value: Double) {
        if (committed == plan.text) return
        committed = plan.text
        committedValue = value
        if (plan.isChange) lastChangeNanos = plan.createdNanos
    }
}

@Composable
private fun DigitsCore(
    text: String,
    value: Double,
    fromText: String?,
    fromValue: Double?,
    style: TextStyle,
    modifier: Modifier,
    enter: Boolean,
    delayMillis: Int,
    stepMillis: Int,
    gradient: Boolean,
    color: Color,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val state = remember { DigitsState() }
    val previous = remember { arrayOfNulls<Plan>(1) }
    val plan = remember(text) {
        state.plan(
            text, value, fromText, fromValue, enter, delayMillis, stepMillis, reduced, System.nanoTime(), previous[0],
        )
    }
    SideEffect {
        previous[0] = plan
        state.commit(plan, value)
    }

    val arrival = plan.arrival
    LaunchedEffect(arrival) {
        if (arrival == null || arrival.started) return@LaunchedEffect
        state.entry.snapTo(0f)
        arrival.started = true
        state.entry.animateTo(arrival.endMs, tween(arrival.endMs.roundToInt(), easing = LinearEasing))
        arrival.done = true
    }
    LaunchedEffect(plan) {
        if (plan.old == null) return@LaunchedEffect
        state.handoff.snapTo(0f)
        plan.started = true
        state.running = plan
        try {
            state.handoff.animateTo(plan.endMs, tween(plan.endMs.roundToInt(), easing = LinearEasing))
        } finally {
            // A newer hand-off may already have taken over: only clear our own.
            if (state.running === plan) state.running = null
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = 24)
    // A window exactly one em tall: the line box is the font size, trimmed, digits centred in it.
    val cellStyle = remember(style) {
        style.copy(
            lineHeight = 1.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            textDirection = TextDirection.Ltr,
        )
    }
    val full = measurer.measure(text, cellStyle, softWrap = false, maxLines = 1)
    val xs = remember(full) { FloatArray(text.length) { full.getBoundingBox(it).left } }
    val glyphs = remember(measurer, cellStyle) { GlyphCache(measurer, cellStyle) }
    val ink = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> c.label
    }
    val paint = if (gradient) GlyphPaint.Gradient(c) else GlyphPaint.Solid(ink)

    Layout(
        modifier = modifier.drawBehind {
            // Both clocks are read on every draw, whatever the plan's flags say. On a device the
            // effects above start only after the first frame is drawn; a draw that skipped the reads
            // until `started` was set would subscribe to nothing, and a number in its own layer would
            // keep that first picture (the old value, or a blank arrival) for good.
            val handoff = state.handoff.value
            val entry = state.entry.value
            val handoffMs = if (plan.started) handoff else 0f
            val entryMs = when {
                arrival == null || arrival.done -> Float.MAX_VALUE
                arrival.started -> entry
                else -> 0f
            }
            drawDigits(full, xs, glyphs, plan, handoffMs, entryMs, paint)
        },
    ) { _, constraints ->
        val w = full.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = full.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        val baseline = full.firstBaseline.roundToInt()
        layout(w, h, mapOf(FirstBaseline to baseline, LastBaseline to baseline)) {}
    }
}

private sealed interface GlyphPaint {
    class Solid(val color: Color) : GlyphPaint
    class Gradient(val c: EmberColors) : GlyphPaint
}

/** One laid-out glyph per character, measured once per style. */
private class GlyphCache(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val map = HashMap<Char, TextLayoutResult>()
    fun of(ch: Char): TextLayoutResult =
        map.getOrPut(ch) { measurer.measure(ch.toString(), style, softWrap = false, maxLines = 1) }
}

private val SoftEdges = Brush.verticalGradient(
    0f to Color.Transparent, .12f to Color.Black, .88f to Color.Black, 1f to Color.Transparent,
)

private fun DrawScope.drawDigits(
    full: TextLayoutResult,
    xs: FloatArray,
    glyphs: GlyphCache,
    plan: Plan,
    handoffMs: Float,
    entryMs: Float,
    paint: GlyphPaint,
) {
    val text = plan.text
    if (text.isEmpty()) return
    val em = full.size.height.toFloat()
    val width = full.size.width.toFloat()
    val arrival = plan.arrival?.takeIf { entryMs < it.endMs }
    val arriving = arrival != null
    val old = plan.old
    val changing = old != null && handoffMs < plan.endMs

    fun glyph(ch: Char, x: Float, dy: Float, alpha: Float) {
        if (alpha <= 0f) return
        val layout = glyphs.of(ch)
        when (paint) {
            is GlyphPaint.Solid -> drawText(layout, paint.color, Offset(x, dy), alpha.coerceAtMost(1f))
            is GlyphPaint.Gradient ->
                drawText(layout, EmberBrushes.emberText(paint.c, width, x), Offset(x, dy), alpha.coerceAtMost(1f))
        }
    }

    // Arrival: digit k starts at base + step × k; a separator goes with the digit before it.
    fun arrivalOf(k: Int): Float {
        if (arrival == null) return 1f
        val local = ((entryMs - arrival.base - arrival.step * k) / ENTER_MS).coerceIn(0f, 1f)
        return EmberEasing.Snappy.transform(local)
    }

    // Digits first, inside one layer whose top and bottom edges are soft (the cell window); the layer
    // is only needed while something moves, at rest every digit sits well inside the window.
    val masked = arriving || changing
    if (masked) {
        drawIntoCanvas { it.saveLayer(Rect(-em, 0f, width + em, em), Paint()) }
    }
    var k = 0
    for (i in text.indices) {
        val ch = text[i]
        if (!isDigit(ch)) continue
        val x = xs[i]
        val oldCh = old?.getOrNull(i)
        if (changing && oldCh != null && oldCh != ch) {
            val local = ((handoffMs - plan.delay - CHANGE_STAGGER_MS * k) / CHANGE_MS).coerceIn(0f, 1f)
            if (local < OUT_GONE_AT) {
                val q = EmberEasing.In.transform(local / OUT_GONE_AT)
                glyph(oldCh, x, -OUT_TRAVEL_EM * em * plan.dir * q, 1f - q)
            }
            if (local >= IN_FROM) {
                val e = EmberEasing.Snappy.transform((local - IN_FROM) / (1f - IN_FROM))
                glyph(ch, x, IN_TRAVEL_EM * em * plan.dir * (1f - e), e)
            }
        } else {
            val e = arrivalOf(k)
            glyph(ch, x, ENTER_TRAVEL_EM * em * (1f - e), e)
        }
        k++
    }
    if (masked) {
        drawRect(SoftEdges, topLeft = Offset(-em, 0f), size = Size(width + 2 * em, em), blendMode = BlendMode.DstIn)
        drawIntoCanvas { it.restore() }
    }
    // Separators are never clipped (a comma hangs below the window); they only fade in an arrival.
    k = 0
    for (i in text.indices) {
        val ch = text[i]
        if (isDigit(ch)) {
            k++
            continue
        }
        glyph(ch, xs[i], 0f, arrivalOf((k - 1).coerceAtLeast(0)))
    }
}
