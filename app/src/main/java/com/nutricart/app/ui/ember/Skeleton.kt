package com.nutricart.app.ui.ember

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Placeholders at the final geometry while data loads (the web's Skeleton.tsx, G12): shapes that
// pulse gently (alpha 1 ↔ .55 over 1.6 s) only while they are composed, so an idle screen draws no
// frames; with "Remove animations" they stand still at 70%. Skeletons are decorative: the screen
// says "Loading" once where it needs to (an EmberSpinner with a description, or its own text).

/** The shared pulse: 1 → .55 → 1 on tween(1600, InOut), or a still .7 with reduced motion. */
@Composable
private fun rememberPulse(): State<Float>? {
    if (Ember.motion.reduced) return null
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = .55f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EmberEasing.InOut), RepeatMode.Reverse),
        label = "pulse",
    )
}

private fun Modifier.pulse(alpha: State<Float>?): Modifier =
    graphicsLayer { this.alpha = alpha?.value ?: .7f }

// Placeholders use the translucent fill, as on the web: it reads on the page and on a card alike, and
// stays quiet on the dark theme's black stage, where the opaque surface3 looked heavy.
private val EmberColors.placeholder: Color get() = fill

/** A placeholder block that pulses (alpha .55 ↔ 1) while loading; static at 70% with reduced motion. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = EmberShapes.card) {
    val alpha = rememberPulse()
    Box(modifier.clearAndSetSemantics { }.pulse(alpha).background(Ember.colors.placeholder, shape))
}

/** One line of placeholder text, [width] wide. */
@Composable
fun SkeletonLine(width: Dp, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Skeleton(modifier.width(width).height(height), EmberShapes.capsule)
}

/** A ring's track as a placeholder (the hero while Today loads). */
@Composable
fun SkeletonRing(size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    val alpha = rememberPulse()
    val color = Ember.colors.placeholder
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .size(size)
            .pulse(alpha)
            .drawBehind {
                val s = stroke.toPx()
                drawCircle(color, radius = (this.size.minDimension - s) / 2f, style = Stroke(s))
            },
    )
}

/**
 * [count] placeholder list rows at a result row's geometry: a 40 dp glyph, two lines and a number at
 * the end, 68 dp tall (put them inside the group or card the real rows will fill).
 */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    val alpha = rememberPulse()
    val fill = Ember.colors.placeholder
    Column(modifier.clearAndSetSemantics { }.fillMaxWidth().pulse(alpha)) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).background(fill, EmberShapes.field))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.72f).height(12.dp).background(fill, EmberShapes.capsule))
                    Box(Modifier.fillMaxWidth(.44f).height(10.dp).background(fill, EmberShapes.capsule))
                }
                Box(Modifier.width(36.dp).height(14.dp).background(fill, EmberShapes.capsule))
            }
        }
    }
}

/**
 * An Ember arc that rotates while something loads (and only then: it leaves with the loading
 * state). The arc grows from a clear tail to a raspberry head over the track. With "Remove
 * animations" it stands still. [contentDescription] ("Loading…") makes it the one node TalkBack reads.
 */
@Composable
fun EmberSpinner(size: Dp = 22.dp, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val turn = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "turn",
        )
    }
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    val tail = remember(c) {
        Brush.sweepGradient(
            0f to c.ember1.copy(alpha = 0f), .08f to c.ember1.copy(alpha = .35f), .5f to c.ember2, .74f to c.ember3, 1f to c.ember3,
        )
    }
    Spacer(
        modifier
            .then(described)
            .size(size)
            .drawBehind {
                val d = this.size.minDimension
                val s = (d * .13f).coerceAtLeast(2.dp.toPx())
                val r = (d - s) / 2f
                val topLeft = Offset((this.size.width - 2 * r) / 2f, (this.size.height - 2 * r) / 2f)
                drawCircle(c.emberTrack, radius = r, style = Stroke(s))
                rotate(turn?.value ?: 0f) {
                    // The sweep gradient starts at 3 o'clock; the arc covers its last 270°.
                    drawArc(
                        tail, startAngle = 0f, sweepAngle = 270f, useCenter = false,
                        topLeft = topLeft, size = Size(2 * r, 2 * r), style = Stroke(s, cap = StrokeCap.Butt),
                    )
                    val head = Offset(center.x, center.y - r)
                    drawCircle(c.ember3, radius = s / 2f, center = head)
                }
            },
    )
}
