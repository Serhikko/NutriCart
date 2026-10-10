package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// The water glass Today's card and the quick-add sheet both draw: the same glass, the same water,
// the same fill and drain, so the two never drift apart.

/** One glass is 250 ml. */
const val WaterGlassMl = 250

/** Eight glasses are the picture of a day's water. */
const val WaterGlassCount = 8

// The glass and its water, drawn on a 19 × 28 grid (the web's glass).
private val GlassPath = PathParser().parsePathString("M1.5 2.5h16l-1.7 22a2 2 0 0 1-2 1.9H5.2a2 2 0 0 1-2-1.9z").toPath()
private val WaterPath = PathParser()
    .parsePathString("M3.1 9.5c2 1.2 4.2 1.2 6.4 0s4.4-1.2 6.4 0l-1.2 15a1.6 1.6 0 0 1-1.6 1.5H5.9a1.6 1.6 0 0 1-1.6-1.5z")
    .toPath()

/**
 * One glass, 19 × 28 dp: a `fill2` body with a hairline rim, its water a blue gradient that rises from
 * the bottom (scaleY). A tap fills it on the bouncy spring, Undo drains it in 220 ms. With
 * [enterDelayMillis] (Today's first open of the day) a full glass starts empty and fills on the
 * bouncy curve after that delay; without it the glass opens as it is. Decorative: the total next to
 * it says how much.
 */
@Composable
fun WaterGlass(full: Boolean, modifier: Modifier = Modifier, enterDelayMillis: Int? = null) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val entering = enterDelayMillis != null && !reduced
    val level = remember { Animatable(if (full && !entering) 1f else 0f) }
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(full, reduced) {
        val opening = firstRun[0]
        firstRun[0] = false
        val goal = if (full) 1f else 0f
        when {
            level.value == goal -> Unit
            reduced -> level.snapTo(goal)
            opening && enterDelayMillis != null && full ->
                level.animateTo(1f, tween(620, enterDelayMillis, EmberEasing.Bouncy))
            full -> level.animateTo(1f, EmberSprings.bouncy())
            else -> level.animateTo(0f, tween(220, easing = EmberEasing.In))
        }
    }
    // In the glass's own 19 × 28 units: the gradient runs from the water's top to the glass's bottom.
    val water = Brush.verticalGradient(listOf(c.water2, c.water), startY = 9.5f, endY = 26f)
    Spacer(
        modifier
            .size(19.dp, 28.dp)
            .drawBehind {
                scale(size.width / 19f, size.height / 28f, pivot = Offset.Zero) {
                    drawPath(GlassPath, c.fill2)
                    val l = level.value
                    // The water rises from the bottom of the glass (y = 26).
                    if (l > 0f) scale(1f, l, pivot = Offset(9.5f, 26f)) { drawPath(WaterPath, water) }
                    drawPath(GlassPath, c.sepStrong, style = Stroke(1.1f))
                }
            },
    )
}
