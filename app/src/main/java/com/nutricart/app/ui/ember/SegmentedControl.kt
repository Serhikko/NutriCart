package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// The iOS segmented control the web uses for Today's ranges, the Fridge halves, sex, rate, snacks and
// cooking: a grey track, a raised thumb that slides to the chosen segment on the snappy spring (and
// squeezes a little while the chosen segment is held), hairlines between the segments the thumb is
// not next to. It draws 40 dp but is 48 dp tall to the finger: the track sits 4 dp inside its own box.

/** Tab = switches what the screen shows (Today's ranges, Fridge halves); Radio = a choice (sex, rate…). */
enum class SegmentRole { Radio, Tab }

/** The track is drawn this far inside the control's 48 dp box, top and bottom. */
private val TrackInset = 4.dp

/** The track's padding round the thumb. */
private val TrackPadding = 2.dp

/**
 * The segmented control: `fill` track r12, `thumb` r10 (white with the card shadow in light, grey at
 * night) sliding on snappy. [selectedIndex] = -1 shows no thumb (nothing chosen yet); the first choice
 * fades the thumb in where it lands instead of sliding it from nowhere. The visible text is each
 * segment's accessible name (Role.Tab or Role.RadioButton inside a selectable group); labels wrap
 * (balanced) and every segment takes the height of the tallest. Disabled = alpha .38.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null,
    role: SegmentRole = SegmentRole.Radio,
    enabled: Boolean = true,
) {
    val c = Ember.colors
    val t = Ember.type
    val n = options.size.coerceAtLeast(1)
    val selected = selectedIndex.takeIf { it in options.indices } ?: -1

    // The thumb, in segments from the start edge, and how present it is (0 = no choice yet).
    val position = remember { Animatable(selected.coerceAtLeast(0).toFloat()) }
    val presence = remember { Animatable(if (selected >= 0) 1f else 0f) }
    // "Remove animations": the thumb jumps.
    val reduced = Ember.motion.reduced
    LaunchedEffect(selected) {
        if (selected < 0) {
            presence.emberSettle(0f, tween(EmberDurations.Press, easing = EmberEasing.In), reduced)
        } else if (presence.value == 0f) {
            position.snapTo(selected.toFloat())
            presence.emberSettle(1f, EmberSprings.snappy(), reduced)
        } else {
            launch { presence.emberSettle(1f, EmberSprings.snappy(), reduced) }
            position.emberSettle(selected.toFloat(), EmberSprings.snappy(), reduced)
        }
    }

    val sources = remember(n) { List(n) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    // Holding the chosen segment squeezes the thumb, as on iOS.
    val squeeze by animateFloatAsState(
        if (selected >= 0 && pressed.getOrElse(selected) { false }) .95f else 1f,
        emberSpec(if (selected >= 0 && pressed.getOrElse(selected) { false }) tween(EmberDurations.Press, easing = EmberEasing.Out) else EmberSprings.snappy()),
        label = "squeeze",
    )

    val track = c.fill
    val line = c.sepStrong
    val labelStyle = t.subhead.copy(
        color = c.label, textAlign = TextAlign.Center, lineHeight = 1.15.em, lineBreak = LineBreak.Heading,
    )
    // Four segments at twice the text size are narrow: the labels take the room the padding had.
    val sidePadding = if (LocalDensity.current.fontScale >= 1.5f) 6.dp else 10.dp
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .selectableGroup()
            .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
    ) {
        // Track and hairlines. A hairline fades as the thumb comes next to it, so the line beside
        // the thumb is never drawn and none blinks while it slides.
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = TrackInset)
                .clip(EmberShapes.segmentTrack)
                .background(track)
                .drawBehind {
                    val pad = TrackPadding.toPx()
                    val w = (size.width - 2 * pad) / n
                    val inset = 10.dp.toPx() + pad
                    if (size.height <= 2 * inset) return@drawBehind
                    val pos = position.value
                    val shown = presence.value
                    for (i in 1 until n) {
                        val near = (minOf(abs(i - pos), abs(i - pos - 1f)) * 2f).coerceIn(0f, 1f)
                        val a = 1f - shown * (1f - near)
                        if (a <= 0f) continue
                        val xFromStart = pad + i * w
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - xFromStart else xFromStart
                        drawLine(
                            line.copy(alpha = line.alpha * a),
                            Offset(x, inset), Offset(x, size.height - inset),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
        )
        // The thumb: one segment wide, placed from the animated position (layout phase only).
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = TrackPadding, vertical = TrackInset + TrackPadding)
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth / n
                    val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelativeWithLayer((position.value * w).roundToInt(), 0) {
                            val shown = presence.value
                            alpha = shown
                            val s = squeeze * (.92f + .08f * shown)
                            scaleX = s
                            scaleY = s
                        }
                    }
                }
                .emberShadow(Elevation.Card, EmberShapes.segmentThumb, c.isDark)
                .border(.5.dp, c.sep, EmberShapes.segmentThumb)
                .background(c.thumb, EmberShapes.segmentThumb),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = TrackPadding),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = on,
                            // The list is remembered per option count, so every index has its source.
                            interactionSource = sources.getOrNull(i),
                            indication = EmberIndication(EmberShapes.segmentThumb, c.focus),
                            enabled = enabled,
                            role = if (role == SegmentRole.Tab) Role.Tab else Role.RadioButton,
                            onClick = { onSelect(i) },
                        )
                        .padding(horizontal = sidePadding, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WholeWordsText(label, labelStyle.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium))
                }
            }
        }
    }
}
