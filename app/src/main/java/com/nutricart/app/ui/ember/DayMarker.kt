package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import kotlin.math.PI
import kotlin.math.min

// Day markers (the web's DayMarker.tsx and the mock's calendar): exactly one state per day, and none
// of them relies on colour alone: a check, a "!", a partial ring, a dashed ring, a plain number.

/** A day's verdict. Never by colour alone: check, "!", a partial ring, a dashed ring, a plain number. */
enum class MarkerState { Good, Over, Today, Empty, Future }

/**
 * One day marker (week row) or calendar cell: Good = Ember disc + white check (or number), Over =
 * 2.5 dp ink ring + "!", Today = a mini Ring at [progress], Empty = dashed label4 ring, Future = the
 * number in label4. [label] under the marker (short weekday), [dayNumber] inside it (calendar).
 * [popDelayMillis] = the first-open pop on the bouncy spring.
 *
 * - With [popDelayMillis] (K2) the disc pops (scale .4 → 1 with a fade, 620 ms on the bouncy curve),
 *   today's mini ring sweeps from 0 120 ms later, and the label fades up 60 ms after its disc.
 * - Today's label is in `tint`; [progress] may pass 1 (the ring's second lap is ink).
 * - [contentDescription] is the full sentence (`marker_*`: "Tuesday 6 October: 2,060 kcal, on
 *   plan"); the visible label and number are part of the picture.
 * - The label shrinks to fit a narrow column at large font sizes instead of running into its neighbour.
 */
@Composable
fun DayMarker(
    state: MarkerState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    label: String? = null,
    dayNumber: String? = null,
    progress: Float = 0f,
    size: Dp = 36.dp,
    popDelayMillis: Int? = null,
) {
    val c = Ember.colors
    val type = Ember.type
    val reduced = Ember.motion.reduced
    val popAt = popDelayMillis?.takeIf { !reduced }
    val play = popAt != null
    val pop = remember { Animatable(if (play) 0f else 1f) }
    val rise = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (popAt != null) pop.animateTo(1f, tween(620, popAt, EmberEasing.Bouncy))
    }
    LaunchedEffect(Unit) {
        if (popAt != null) rise.animateTo(1f, tween(560, popAt + 60, EmberEasing.Out))
    }
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Column(
        modifier.then(semantics),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val discModifier = Modifier
            .size(size)
            .graphicsLayer {
                val e = pop.value
                scaleX = .4f + .6f * e
                scaleY = .4f + .6f * e
                alpha = e.coerceIn(0f, 1f)
            }
        // A number inside never outgrows its disc, whatever the font scale.
        val numberStyle = with(LocalDensity.current) {
            val px = min(13.sp.toPx(), size.toPx() * .4f)
            type.caption.copy(fontSize = px.toSp(), lineHeight = px.toSp(), fontFeatureSettings = "tnum")
        }
        val numberColor = when (state) {
            MarkerState.Good -> c.onAccent
            MarkerState.Today -> c.tint
            MarkerState.Future -> c.label4
            else -> c.label
        }
        if (state == MarkerState.Today) {
            Ring(
                value = progress,
                target = 1f,
                size = size,
                stroke = if (dayNumber != null || size < 34.dp) size * (4.5f / 36f) else size * (5f / 36f),
                modifier = discModifier,
                sweep = play,
                delayMillis = (popDelayMillis ?: 0) + 120,
            ) {
                if (dayNumber != null) BasicText(dayNumber, style = numberStyle.copy(color = numberColor), maxLines = 1)
            }
        } else {
            Box(
                discModifier.drawBehind { drawDisc(state, c) },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    dayNumber != null -> BasicText(dayNumber, style = numberStyle.copy(color = numberColor), maxLines = 1)
                    state == MarkerState.Good -> EmberIcon(EmberIcons.Check, null, size = size * (16f / 36f), tint = c.onAccent, strokeWidth = 3f)
                    state == MarkerState.Over -> EmberIcon(EmberIcons.Bang, null, size = size * (16f / 36f), tint = c.overLap, strokeWidth = 3f)
                }
            }
        }
        if (label != null) {
            val labelFloor = with(LocalDensity.current) { 8.dp.toSp() }
            BasicText(
                label,
                modifier = Modifier.graphicsLayer {
                    alpha = rise.value.coerceIn(0f, 1f)
                    translationY = 8.dp.toPx() * (1f - rise.value)
                },
                style = type.caption.copy(
                    color = if (state == MarkerState.Today) c.tint else c.label2,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                softWrap = false,
                // The floor is 8 dp whatever the font scale (8 sp would be 16 dp at 2×): a long "today"
                // ("Сьогодні") in a seventh of the width shrinks to fit instead of being cut off.
                autoSize = TextAutoSize.StepBased(minFontSize = labelFloor, maxFontSize = type.caption.fontSize),
            )
        }
    }
}

/** The disc itself: Ember disc, ink ring, dashed ring, or nothing (a future day). */
private fun DrawScope.drawDisc(state: MarkerState, c: EmberColors) {
    val d = size.minDimension
    when (state) {
        MarkerState.Good -> drawCircle(EmberBrushes.emberIcon(c), radius = d / 2f)
        MarkerState.Over -> {
            val w = 2.5.dp.toPx() * d / 36.dp.toPx()
            drawCircle(c.overLap, radius = d / 2f - w / 2f, style = Stroke(w))
        }
        MarkerState.Empty -> drawDashedRing(c, d, 2.dp.toPx() * d / 36.dp.toPx())
        MarkerState.Today, MarkerState.Future -> Unit
    }
}

/** Nothing logged: a grey ring of 16 short round-capped dashes (the web's 3.6 / 2.65 per 100). */
private fun DrawScope.drawDashedRing(c: EmberColors, d: Float, w: Float) {
    val r = d * 17f / 36f
    val length = 2f * PI.toFloat() * r
    drawCircle(
        c.label4,
        radius = r,
        style = Stroke(
            width = w,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(length * .036f, length * .0265f)),
        ),
    )
}

/**
 * The adherence calendar's key: On plan (Ember disc), Over target (ink ring), Not logged (dashed
 * ring), in caption type; it wraps on a narrow screen or at a large font size.
 */
@Composable
fun MarkerLegend(modifier: Modifier = Modifier) {
    val c = Ember.colors
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(stringResource(R.string.legend_on_plan)) { drawCircle(EmberBrushes.emberIcon(c), radius = size.minDimension / 2f) }
        LegendItem(stringResource(R.string.legend_over)) {
            val w = 2.dp.toPx()
            drawCircle(c.label, radius = size.minDimension / 2f - w / 2f, style = Stroke(w))
        }
        LegendItem(stringResource(R.string.legend_not_logged)) {
            val w = 1.5.dp.toPx()
            val r = size.minDimension / 2f - w / 2f
            val length = 2f * PI.toFloat() * r
            drawCircle(
                c.label4, radius = r,
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(length / 12f, length / 12f))),
            )
        }
    }
}

/** One key entry: a [swatchSize] swatch and its words (the charts' key uses 10 dp squares). */
@Composable
internal fun LegendItem(text: String, swatchSize: Dp = 12.dp, swatch: DrawScope.() -> Unit) {
    val c = Ember.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.size(swatchSize).drawBehind(swatch))
        BasicText(text, style = Ember.type.caption.copy(fontWeight = Ember.type.footnote.fontWeight, color = c.label2))
    }
}
