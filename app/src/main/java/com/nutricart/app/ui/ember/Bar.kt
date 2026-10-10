package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Capsule bars (the web's .bar and .comp-bar): the fill is a full-width capsule moved by translation
// and clipped by the track, so its round end always looks right at any value, and the motion is a
// transform, never a width.

/**
 * A capsule progress bar: a [height] `fill` track whose fill is a full-width capsule moved by
 * translation, so its round end always looks right. [brush] paints the fill instead of [color] (the
 * Ember shopping progress). [fillDelayMillis] = first-open fill on the bars spring after that delay.
 *
 * Later changes of [fraction] move the fill on the snappy spring; reduced motion: at once.
 * With a [contentDescription] ("Protein 72 of 140 g") TalkBack reads it with the progress; without
 * one the bar is decorative (its numbers are on screen next to it).
 */
@Composable
fun Bar(
    fraction: Float,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    fillDelayMillis: Int? = null,
    brush: Brush? = null,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val fill = remember { Animatable(if (fillDelayMillis != null && !reduced) 0f else f) }
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(f, reduced) {
        val first = firstRun[0]
        firstRun[0] = false
        when {
            reduced -> fill.snapTo(f)
            first && fillDelayMillis != null -> {
                motionDelay(fillDelayMillis.toLong())
                fill.animateTo(f, EmberSprings.bars())
            }
            else -> fill.animateTo(f, EmberSprings.snappy())
        }
    }
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics {
            progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
            this.contentDescription = contentDescription
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Spacer(
        modifier
            .then(semantics)
            .fillMaxWidth()
            .height(height)
            .clip(EmberShapes.capsule)
            .drawBehind {
                drawRect(c.fill)
                val v = fill.value.coerceIn(0f, 1f)
                if (v <= 0f) return@drawBehind
                val r = CornerRadius(size.height / 2f)
                translate(left = (v - 1f) * size.width) {
                    if (brush != null) drawRoundRect(brush, cornerRadius = r) else drawRoundRect(color, cornerRadius = r)
                }
            },
    )
}

/**
 * Protein / fat / carbs as one row of capsules split by their share of the calories (protein, fat,
 * carbs colours), 3 dp apart. [growDelayMillis] = the first-open growth (S9): each segment grows
 * from its left end, 70 ms apart; later changes slide the split on the snappy spring (O2, S10).
 * Decorative: the macro columns next to it carry the numbers.
 */
@Composable
fun CompositionBar(
    proteinKcal: Float,
    fatKcal: Float,
    carbsKcal: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    growDelayMillis: Int? = null,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val goals = floatArrayOf(proteinKcal, fatKcal, carbsKcal).map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
    val shares = remember { goals.map { Animatable(it) } }
    goals.forEachIndexed { i, goal ->
        LaunchedEffect(goal, reduced) {
            if (reduced) shares[i].snapTo(goal) else shares[i].animateTo(goal, EmberSprings.snappy())
        }
    }
    val grow = remember { List(3) { Animatable(if (growDelayMillis != null && !reduced) 0f else 1f) } }
    if (growDelayMillis != null) {
        for (i in 0 until 3) {
            LaunchedEffect(Unit) {
                if (reduced) grow[i].snapTo(1f) else grow[i].animateTo(1f, tween(700, growDelayMillis + 70 * i, EmberEasing.Snappy))
            }
        }
    }
    val colors = listOf(c.protein, c.fat, c.carbs)
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val values = shares.map { it.value.coerceAtLeast(0f) }
                val total = values.sum()
                val r = CornerRadius(size.height / 2f)
                if (total <= 0f) {
                    drawRoundRect(c.fill, cornerRadius = r)
                    return@drawBehind
                }
                val gap = 3.dp.toPx()
                // Segments too small to show take no gap, so the row always spans the whole width.
                val shown = values.count { it / total * size.width >= 1f }
                val room = size.width - gap * (shown - 1).coerceAtLeast(0)
                var x = 0f
                values.forEachIndexed { i, v ->
                    val w = v / total * room
                    if (v / total * size.width < 1f) return@forEachIndexed
                    val g = grow[i].value.coerceIn(0f, 1.05f)
                    if (g > 0f) {
                        drawRoundRect(colors[i], topLeft = Offset(x, 0f), size = Size((w * g).coerceAtLeast(0f), size.height), cornerRadius = r)
                    }
                    x += w + gap
                }
            },
    )
}
