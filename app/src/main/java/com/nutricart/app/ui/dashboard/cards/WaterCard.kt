package com.nutricart.app.ui.dashboard.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.rememberLastShownValue
import java.time.LocalDate

/** One glass is 250 ml; eight glasses are the picture of a day's water. */
private const val GlassMl = 250
private const val Glasses = 8

/**
 * Water: today's total (its digits hand off as it changes) beside eight glasses of 250 ml that fill
 * from the bottom, then "+250 ml", "+500 ml" and Undo (off at 0). On the first open of the day the
 * glasses fill one after another; a tap fills the next one at once, Undo empties the last.
 */
@Composable
internal fun WaterCard(
    waterMl: Int,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
) {
    val t = Ember.type
    val ml = stringResource(R.string.ml_unit)
    // Read on every composition: after midnight the next refresh moves everything to the new day.
    val day = LocalDate.now().toEpochDay()
    val last = rememberLastShownValue("water/$day", waterMl.toLong())
    EmberCard(modifier) {
        CardHead(stringResource(R.string.water_label), icon = EmberIcons.Drop, metric = Metric.Water)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            NumberWithUnit(
                waterMl.toLong(), ml, t.stat.copy(fontSize = 30.sp),
                from = if (first) null else last,
                delayMillis = 240,
                unitSize = 15.sp,
            )
            Row(
                Modifier.padding(bottom = 2.dp).clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (i in 0 until Glasses) Glass(full = waterMl >= (i + 1) * GlassMl, index = i, first = first)
            }
        }
        // The units come from the strings: "+250" and "ml", kept together. One row, Undo at the end;
        // at large text sizes the buttons wrap instead.
        val add250 = stringResource(R.string.water_add_250) + "\u00A0" + ml
        val add500 = stringResource(R.string.water_add_500) + "\u00A0" + ml
        val undo = stringResource(R.string.water_undo)
        if (LocalDensity.current.fontScale < 1.3f) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmberButton(add250, { onAdd(250) }, variant = ButtonVariant.Water)
                EmberButton(add500, { onAdd(500) }, variant = ButtonVariant.Water)
                Spacer(Modifier.weight(1f))
                EmberButton(undo, onUndo, variant = ButtonVariant.Plain, enabled = waterMl > 0)
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                EmberButton(add250, { onAdd(250) }, variant = ButtonVariant.Water)
                EmberButton(add500, { onAdd(500) }, variant = ButtonVariant.Water)
                EmberButton(undo, onUndo, variant = ButtonVariant.Plain, enabled = waterMl > 0)
            }
        }
    }
}

// The glass and its water, drawn on a 19 × 28 grid (the web's glass).
private val GlassPath = PathParser().parsePathString("M1.5 2.5h16l-1.7 22a2 2 0 0 1-2 1.9H5.2a2 2 0 0 1-2-1.9z").toPath()
private val WaterPath = PathParser()
    .parsePathString("M3.1 9.5c2 1.2 4.2 1.2 6.4 0s4.4-1.2 6.4 0l-1.2 15a1.6 1.6 0 0 1-1.6 1.5H5.9a1.6 1.6 0 0 1-1.6-1.5z")
    .toPath()

/**
 * One glass: a `fill2` body with a hairline rim, its water a blue gradient that rises from the
 * bottom (scaleY) on the bouncy curve, 560 ms + 55 ms per glass on the first open, at once on a tap;
 * it drains in 220 ms when undone. Decorative: the total next to it says how much.
 */
@Composable
private fun Glass(full: Boolean, index: Int, first: Boolean) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val level = remember { Animatable(if (full && !(first && !reduced)) 1f else 0f) }
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(full, reduced) {
        val opening = firstRun[0]
        firstRun[0] = false
        val goal = if (full) 1f else 0f
        when {
            reduced -> level.snapTo(goal)
            opening && first && full -> level.animateTo(1f, tween(620, 560 + 55 * index, EmberEasing.Bouncy))
            full -> level.animateTo(1f, EmberSprings.bouncy())
            else -> level.animateTo(0f, tween(220, easing = EmberEasing.In))
        }
    }
    // In the glass's own 19 × 28 units: the gradient runs from the water's top to the glass's bottom.
    val water = Brush.verticalGradient(listOf(c.water2, c.water), startY = 9.5f, endY = 26f)
    Spacer(
        Modifier
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
