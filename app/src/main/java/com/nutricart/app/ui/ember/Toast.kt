package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The Ember toast (G11): an ink capsule that pops in above the tab bar while its Ember check draws
// on, and slips away after 2.6 s (5 s with an action). It keeps every existing SnackbarHostState
// flow: screens still call showSnackbar(), only the host is ours.

enum class ToastIcon { Check, Info, Warning }

/**
 * What a screen shows through its SnackbarHostState: `hostState.showSnackbar(EmberToastVisuals(…))`.
 * Plain `showSnackbar(message)` calls still work (they show with the Info glyph).
 */
class EmberToastVisuals(
    override val message: String,
    val icon: ToastIcon = ToastIcon.Info,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

/** Multi-line messages keep the capsule's ends (half of its 46 dp single-line height). */
private val ToastShape = RoundedCornerShape(23.dp)

/**
 * The Ember toast: an ink capsule with an Ember check / info / warning glyph, floating [bottomPadding]
 * above the bottom (the tab bar's clearance, a docked CTA's, or 24 dp on stacked screens and in
 * sheets). The caller adds the navigation-bar inset (LargeTitleScaffold does).
 */
@Composable
fun EmberToastHost(hostState: SnackbarHostState, modifier: Modifier = Modifier, bottomPadding: Dp) {
    val current = hostState.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current

    // How long a toast stays: 2.6 s, 5 s with an action or a long message, longer when the user's
    // accessibility settings ask for more time. This is a reading timeout, not choreography, so it
    // runs on real time (motionDelay would cut it to nothing with "Remove animations" on).
    LaunchedEffect(current) {
        val data = current ?: return@LaunchedEffect
        val visuals = data.visuals
        if (visuals.duration == SnackbarDuration.Indefinite) return@LaunchedEffect
        val base = if (visuals.actionLabel != null || visuals.duration == SnackbarDuration.Long) {
            EmberDurations.ToastAction
        } else {
            EmberDurations.Toast
        }.toLong()
        val millis = accessibility?.calculateRecommendedTimeoutMillis(
            base, containsIcons = true, containsText = true, containsControls = visuals.actionLabel != null,
        ) ?: base
        delay(millis)
        data.dismiss()
    }

    // The toast on screen: the current one, or the last one while it leaves.
    var shown by remember { mutableStateOf<SnackbarData?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val presence = remember { Animatable(0f) }
    val check = remember { Animatable(1f) }
    LaunchedEffect(current) {
        val previous = shown
        if (previous != null && previous !== current) {
            leaving = true
            presence.animateTo(0f, tween(240, easing = EmberEasing.In))
        }
        leaving = false
        shown = current
        if (current != null) {
            presence.snapTo(0f)
            check.snapTo(0f)
            launch { check.animateTo(1f, tween(420, 220, EmberEasing.Out)) }
            presence.animateTo(1f, EmberSprings.bouncy())
        }
    }
    val data = shown ?: return

    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ToastCapsule(
            data = data,
            check = { check.value },
            modifier = Modifier.graphicsLayer {
                val p = presence.value
                val rest = 1f - p
                if (leaving) {
                    // Out: down 8 dp, .96, fading (240 ms, ease-in).
                    alpha = p.coerceIn(0f, 1f)
                    translationY = 8.dp.toPx() * rest
                    scaleX = 1f - .04f * rest
                } else {
                    // In: up from 18 dp and .9 on the bouncy spring.
                    alpha = p.coerceIn(0f, 1f)
                    translationY = 18.dp.toPx() * rest
                    scaleX = 1f - .1f * rest
                }
                scaleY = scaleX
                transformOrigin = TransformOrigin(.5f, 1f)
                // Fade each draw, not an offscreen copy: that would clip the shadow to the capsule.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        )
    }
}

@Composable
private fun ToastCapsule(data: SnackbarData, check: () -> Float, modifier: Modifier) {
    val c = Ember.colors
    val visuals = data.visuals
    val icon = (visuals as? EmberToastVisuals)?.icon ?: ToastIcon.Info
    val action = visuals.actionLabel
    Row(
        modifier
            .emberShadow(Elevation.Float, ToastShape, c.isDark)
            .clip(ToastShape)
            .background(c.ink)
            .heightIn(min = 46.dp)
            // One polite announcement for glyph and text; the action stays its own button.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = if (action != null) 0.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToastGlyph(icon, check)
        Spacer(Modifier.width(8.dp))
        BasicText(
            visuals.message,
            style = Ember.type.subhead,
            color = { c.onInk },
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 12.dp),
        )
        if (action != null) {
            Spacer(Modifier.width(4.dp))
            val line = c.onInk.copy(alpha = .30f)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { data.performAction() }
                    .drawBehind {
                        val w = .5.dp.toPx()
                        drawLine(line, Offset(w / 2, 0f), Offset(w / 2, size.height), strokeWidth = w)
                    }
                    .padding(start = 12.dp, end = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(action, style = Ember.type.subhead.copy(fontWeight = FontWeight.Bold), color = { c.onInk })
            }
        }
    }
}

/** The 20 dp Ember glyph; the check draws itself on (the web's stroke-dashoffset), the others just appear. */
@Composable
private fun ToastGlyph(icon: ToastIcon, check: () -> Float) {
    val c = Ember.colors
    val brush = EmberBrushes.emberIcon(c)
    when (icon) {
        ToastIcon.Check -> Box(
            Modifier
                .size(20.dp)
                .drawWithCache {
                    // The check's path from the icon set, scaled from the 24 grid; stroke 2.8 as on the web.
                    val s = size.width / 24f
                    val full = PathParser().parsePathString(EmberIcons.Check.paths.first().d).toPath()
                    full.transform(Matrix().apply { scale(s, s) })
                    val measure = PathMeasure().apply { setPath(full, false) }
                    val length = measure.length
                    val stroke = Stroke(2.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val drawn = Path()
                    onDrawBehind {
                        val p = check().coerceIn(0f, 1f)
                        if (p <= 0f) return@onDrawBehind
                        drawn.reset()
                        measure.getSegment(0f, length * p, drawn, true)
                        drawPath(drawn, brush, style = stroke)
                    }
                },
        )
        ToastIcon.Info -> EmberIcon(EmberIcons.Info, null, size = 20.dp, brush = brush, strokeWidth = 2.8f)
        ToastIcon.Warning -> EmberIcon(EmberIcons.Warning, null, size = 20.dp, brush = brush, strokeWidth = 2.8f)
    }
}
