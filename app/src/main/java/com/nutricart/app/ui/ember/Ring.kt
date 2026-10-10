package com.nutricart.app.ui.ember

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The Ember day ring (APP-DESIGN.md §3.4 R, the web's Ring.tsx): one thick stroke that fills from
// amber through vermilion to raspberry, its round ends drawn as circles in the same gradients (so a
// cap is exactly the colour of the stroke under it and nothing is ever rotated), an ink second lap
// past the goal, the raspberry "this food" arc, one pass of light, and the zone halo.

/** A ring's drawn diameter and stroke. */
@Immutable
data class RingSize(val size: Dp, val stroke: Dp)

/** Every ring size the app draws. The hero is `min(252.dp, 66% of width)`: [rememberHeroRingSize]. */
object RingSizes {
    val Hero = RingSize(252.dp, 26.dp)
    val Plan = RingSize(140.dp, 16.dp)
    val Onboarding = RingSize(128.dp, 15.dp)
    val Sheet = RingSize(88.dp, 11.dp)
    val Summary = RingSize(52.dp, 7.dp)
    val DayCard = RingSize(34.dp, 5.dp)
    val Marker = RingSize(36.dp, 5.dp)
    val Mini = RingSize(28.dp, 4.dp)
}

/**
 * The hero ring for this screen: 252 dp, or 66% of the width on a narrow phone, with the stroke in
 * the same 26 / 252 proportion.
 */
@Composable
@ReadOnlyComposable
fun rememberHeroRingSize(): RingSize {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
    val size = min(RingSizes.Hero.size.value, width * .66f)
    return RingSize(size.dp, (RingSizes.Hero.stroke.value * size / RingSizes.Hero.size.value).dp)
}

// Two laps is the most the ring can show (the second one in ink); anything more reads the same.
private const val MAX_P = 2f

private fun fraction(v: Float, target: Float): Float =
    if (target > 0f && v.isFinite()) (v / target).coerceIn(0f, MAX_P) else 0f

/**
 * The Ember day ring (§3.4 R): [value] of [target] fills from amber through vermilion to raspberry;
 * past the target an ink second lap. [from] = the value it last showed (rememberLastShown), [add] =
 * the raspberry "this food" arc, [muted] dims the rest, [sweep] plays the first-open sweep after
 * [delayMillis], [shineAtMillis] one light pass, [zone] the zone halo. The canvas has no semantics:
 * pass [contentDescription] (the day line) or merge it into a parent.
 *
 * - [sweep] sweeps from [from] (or 0) when the ring appears: the first open of the day, a day change
 *   that recomposes it, back from Add. Without it the ring shows its value at once.
 * - Any later change of [value] or [target] sweeps from wherever the ring is drawn at that moment
 *   (never from 0), so an interrupted sweep simply turns round.
 * - [zone] pulses the zone halo twice behind the ring, 1250 ms after it turns true (Z1); pass it for
 *   the moment only (the first open of the day in the zone, or a change that crosses 90%).
 * - [shineAtMillis] runs one pass of light round the ring after that many ms; a new value replays it.
 * - Reduced motion ("Remove animations"): final values at once, no light pass, no zone halo.
 * - It draws from its actual size (the stroke scales with it), so a shared-bounds morph redraws cleanly.
 */
@Composable
fun Ring(
    value: Float,
    target: Float,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    from: Float? = null,
    add: Float? = null,
    muted: Boolean = false,
    sweep: Boolean = false,
    delayMillis: Int = 0,
    shineAtMillis: Int? = null,
    zone: Boolean = false,
    contentDescription: String? = null,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val goal = fraction(value, target)
    val p = remember { Animatable(if (sweep && !reduced) fraction(from ?: 0f, target) else goal) }
    // The first run waits for its delay; later runs (a change while shown) start at once.
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(goal, reduced) {
        val first = firstRun[0]
        firstRun[0] = false
        if (reduced) {
            p.snapTo(goal)
            return@LaunchedEffect
        }
        if (first && sweep && p.value != goal) motionDelay(delayMillis.toLong())
        p.animateTo(goal, EmberSprings.ring())
    }

    // "This food": its length follows the amount on the snappy spring.
    val addGoal = add?.let { (fraction(it, target)).coerceAtLeast(0f) }
    val g = remember { Animatable(addGoal ?: 0f) }
    LaunchedEffect(addGoal, reduced) {
        val to = addGoal ?: 0f
        if (reduced) g.snapTo(to) else g.animateTo(to, EmberSprings.snappy())
    }

    // One pass of light: 0 → 1 over 900 ms after its delay (keyed by the delay, so asking again replays).
    val shine = remember(shineAtMillis) { Animatable(0f) }
    LaunchedEffect(shineAtMillis, reduced) {
        if (shineAtMillis != null && !reduced) {
            shine.animateTo(1f, tween(900, shineAtMillis, EmberEasing.InOut))
        }
    }

    // The zone halo: two pulses, 0 → 2 in linear time, the curves applied when drawing.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(zone, reduced) {
        if (zone && !reduced) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1000, 1250, LinearEasing))
            pulse.animateTo(2f, tween(1000, 0, LinearEasing))
        } else {
            pulse.snapTo(0f)
        }
    }

    val described = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(modifier.then(described).size(size), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val d = this.size.minDimension
                    // The stroke follows the drawn diameter (a shared-bounds morph draws it at any size).
                    val s = stroke.toPx() * d / size.toPx().coerceAtLeast(1f)
                    val geo = RingGeometry(d, s)
                    val a = EmberBrushes.ringA(c, d, s)
                    val b = EmberBrushes.ringB(c, d, s)
                    val dx = (this.size.width - d) / 2f
                    val dy = (this.size.height - d) / 2f
                    onDrawBehind {
                        translate(dx, dy) {
                            if (pulse.value > 0f) drawZoneHalo(c, d, pulse.value)
                            drawRing(c, geo, a, b, p.value, g.value, if (muted) .32f else 1f)
                            val t = shine.value
                            if (t > 0f && t < 1f) drawShine(geo, t, 6.dp.toPx() * d / size.toPx().coerceAtLeast(1f))
                        }
                    }
                },
        )
        center()
    }
}

/** Where things sit on a ring of diameter [d] and stroke [s] (in its own square, origin top left). */
private class RingGeometry(val d: Float, val s: Float) {
    val r = (d - s) / 2f
    val c = Offset(d / 2f, d / 2f)
    val topLeft = Offset(s / 2f, s / 2f)
    val arcSize = Size(2 * r, 2 * r)

    /** The point [q] of a turn clockwise from 12 o'clock. */
    fun at(q: Float): Offset {
        val a = q * 2f * PI.toFloat()
        return Offset(c.x + r * sin(a), c.y - r * cos(a))
    }
}

/** Steps 1–6 of §3.4 R: track, two halves, caps, closing shadow, the ink lap, "this food". */
private fun DrawScope.drawRing(
    c: EmberColors,
    geo: RingGeometry,
    a: Brush,
    b: Brush,
    p: Float,
    g: Float,
    todayAlpha: Float,
) {
    val s = geo.s
    drawCircle(c.emberTrack, radius = geo.r, center = geo.c, style = Stroke(s))
    if (p > 0f) {
        // Muted (the sheet preview): today's part fades as one group, so the halves' overlap and the
        // caps never show through each other.
        val muted = todayAlpha < 1f
        if (muted) {
            drawIntoCanvas {
                it.saveLayer(Rect(-s, -s, geo.d + s, geo.d + s), Paint().apply { alpha = todayAlpha })
            }
        }
        // Two butt-ended halves: the right one (amber to vermilion) runs 0.4% under the left one
        // (raspberry to vermilion), both flat across 6 o'clock, so they meet without a seam.
        drawArc(a, -90f, 360f * min(p, .504f), false, geo.topLeft, geo.arcSize, style = Stroke(s, cap = StrokeCap.Butt))
        if (p > .5f) {
            drawArc(b, 90f, 360f * (p - .5f).coerceAtMost(.5f), false, geo.topLeft, geo.arcSize, style = Stroke(s, cap = StrokeCap.Butt))
        }
        drawCircle(a, radius = s / 2f, center = geo.at(0f))
        val end = min(p, 1f)
        if (p > .9f) {
            // Just ahead of the end cap a soft shade, so near closure the cap reads as lying over the start.
            val at = geo.at(end + .012f)
            val shade = Brush.radialGradient(
                .5f to Color.Black.copy(alpha = .55f), 1f to Color.Transparent, center = at, radius = s * .62f,
            )
            drawCircle(shade, radius = s * .62f, center = at, alpha = ((p - .9f) * 8f).coerceIn(0f, 1f))
        }
        drawCircle(if (end <= .5f) a else b, radius = s / 2f, center = geo.at(end))
        if (muted) drawIntoCanvas { it.restore() }
    }
    if (p > 1f) {
        // Past the goal: the second lap in ink, never another warm hue.
        drawArc(c.overLap, -90f, 360f * (p - 1f).coerceAtMost(1f), false, geo.topLeft, geo.arcSize, style = Stroke(s, cap = StrokeCap.Round))
    }
    if (g > .0005f) {
        drawArc(c.ember3, -90f + 360f * p, 360f * g, false, geo.topLeft, geo.arcSize, style = Stroke(s, cap = StrokeCap.Round))
    }
}

/** One pass of light (T5, Z2): a white 18° dash at half opacity running from −30° to 330°. */
private fun DrawScope.drawShine(geo: RingGeometry, t: Float, inset: Float) {
    val alpha = when {
        t < .15f -> t / .15f
        t > .85f -> (1f - t) / .15f
        else -> 1f
    }
    val w = (geo.s - inset).coerceAtLeast(2.dp.toPx())
    drawArc(
        Color.White, -90f - 30f + 360f * t, 18f, false, geo.topLeft, geo.arcSize,
        alpha = .5f * alpha, style = Stroke(w, cap = StrokeCap.Round),
    )
}

/**
 * The T4 glow as a ring of light round a ring of diameter [d]: transparent to 52% of its radius,
 * `emberGlow` at 66%, gone by 96%, 128% of the ring. A gradient, never a blur.
 */
private fun DrawScope.drawGlow(c: EmberColors, d: Float, alpha: Float, scale: Float) {
    val radius = d * .64f
    val center = Offset(d / 2f, d / 2f)
    val brush = Brush.radialGradient(
        0f to Color.Transparent, .52f to Color.Transparent, .66f to c.emberGlow, .96f to Color.Transparent,
        center = center, radius = radius,
    )
    scale(scale, pivot = center) { drawCircle(brush, radius = radius, center = center, alpha = alpha.coerceIn(0f, 1f)) }
}

/** Z1: the glow pulses twice (alpha 0 → .9 → 0, scale .94 → 1.12), [t] = 0 … 2 in linear time. */
private fun DrawScope.drawZoneHalo(c: EmberColors, d: Float, t: Float) {
    if (t >= 2f) return
    val u = t - t.toInt()
    val alpha = if (u < .3f) .9f * EmberEasing.Out.transform(u / .3f) else .9f * (1f - EmberEasing.Out.transform((u - .3f) / .7f))
    drawGlow(c, d, alpha, .94f + .18f * EmberEasing.Out.transform(u))
}

// ---------------------------------------------------------------------------------------------
// The hero's entrance (T1) and its glow (T4): modifiers for the ring's wrapper on Today, the Week
// average and the recipe hero, so the Ring itself stays one plain drawing.
// ---------------------------------------------------------------------------------------------

/**
 * The soft Ember glow behind a hero (T4): a ring-shaped radial gradient 128% of the element, drawn
 * behind its content and outside its bounds (nothing is clipped). [bloom] (the first open) scales it
 * in from .6 with a fade on tween(1300, [delayMillis], Out); otherwise it simply rests there.
 */
fun Modifier.ringHalo(bloom: Boolean = false, delayMillis: Int = 180): Modifier =
    this.then(HaloElement(bloom, delayMillis))

private data class HaloElement(val bloom: Boolean, val delayMillis: Int) : ModifierNodeElement<HaloNode>() {
    override fun create() = HaloNode(bloom, delayMillis)

    // The bloom is decided once, when the glow first appears.
    override fun update(node: HaloNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "ringHalo"
        properties["bloom"] = bloom
    }
}

private class HaloNode(private val bloom: Boolean, private val delayMillis: Int) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var progress: Animatable<Float, AnimationVector1D>? = null

    // Read once, on attach, on purpose: the moment is decided when the ring first appears, and a
    // later change of the motion setting must neither replay nor cut it.
    @SuppressLint("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        if (!bloom || progress != null || currentValueOf(LocalEmberMotion).reduced) return
        val a = Animatable(0f)
        progress = a
        coroutineScope.launch { a.animateTo(1f, tween(1300, delayMillis, EmberEasing.Out)) }
    }

    override fun ContentDrawScope.draw() {
        val t = progress?.value ?: 1f
        val d = size.minDimension
        translate((size.width - d) / 2f, (size.height - d) / 2f) {
            drawGlow(currentValueOf(LocalEmberColors), d, t, .6f + .4f * t)
        }
        drawContent()
    }
}

/**
 * The hero ring's first-open entrance (T1): scale .9 → 1, a −10° turn and a fade on
 * tween(820, [delayMillis], Smooth). Only when [play] (the first open); reduced motion: none.
 */
fun Modifier.ringEntrance(play: Boolean, delayMillis: Int = 40): Modifier =
    this.then(RingEntranceElement(play, delayMillis))

private data class RingEntranceElement(val play: Boolean, val delayMillis: Int) :
    ModifierNodeElement<RingEntranceNode>() {
    override fun create() = RingEntranceNode(play, delayMillis)

    override fun update(node: RingEntranceNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "ringEntrance"
        properties["play"] = play
    }
}

private class RingEntranceNode(private val play: Boolean, private val delayMillis: Int) :
    Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {

    private var progress: Animatable<Float, AnimationVector1D>? = null

    // Read once, on attach, on purpose: the moment is decided when the ring first appears, and a
    // later change of the motion setting must neither replay nor cut it.
    @SuppressLint("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        if (!play || progress != null || currentValueOf(LocalEmberMotion).reduced) return
        val a = Animatable(0f)
        progress = a
        coroutineScope.launch { a.animateTo(1f, tween(820, delayMillis, EmberEasing.Smooth)) }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val t = progress?.value ?: return@placeWithLayer
                val rest = 1f - t
                alpha = t.coerceIn(0f, 1f)
                scaleX = 1f - .1f * rest
                scaleY = 1f - .1f * rest
                rotationZ = -10f * rest
                transformOrigin = TransformOrigin.Center
            }
        }
    }
}
