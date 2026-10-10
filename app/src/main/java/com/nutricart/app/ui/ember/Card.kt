package com.nutricart.app.ui.ember

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.domain.model.MealSlot
import kotlin.math.min
import kotlin.math.roundToInt

// Cards: the solid white (graphite at night) blocks every screen stacks, their Health-style head,
// the small stat tile and the meal squircle. Cards in light carry the web's soft two-layer shadow; at
// night they separate from the black stage by tone alone.

/**
 * The Ember card: `surface`, radius 24, the card shadow in light (tone alone in dark). [onClick] makes
 * the whole card one button (it darkens a little and settles back while pressed); [mergeDescendants]
 * makes a read-only card one TalkBack node. A card with several actions merges nothing, so each button
 * stays its own stop.
 */
@Composable
fun EmberCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    mergeDescendants: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    var m = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.pressScale(source, .985f) else Modifier)
        .emberShadow(Elevation.Card, EmberShapes.card, c.isDark)
        .clip(EmberShapes.card)
        .background(c.surface)
    if (onClick != null) {
        // clickable merges its descendants itself: the card reads as one button.
        m = m.clickable(
            interactionSource = source,
            indication = EmberIndication(EmberShapes.card, c.focus, pressed = c.fill),
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
    } else if (mergeDescendants) {
        m = m.semantics(mergeDescendants = true) {}
    }
    Column(m.padding(padding), content = content)
}

/** True inside a card head's action slot: a PlainLink there is 15 sp, as the web's `.card-head .btn-plain`. */
internal val LocalInCardHead = staticCompositionLocalOf { false }

/** The head's own height: the web's 22 px row, whatever a 48 dp action inside it needs to the finger. */
private val HeadHeight = 22.dp

/**
 * Lets a 48 dp control sit in a 22 dp head without making it taller: the control keeps its full size
 * (and its full touch area), the head only reserves [height] and centres it, overhanging above and
 * below (the web's negative margins on `.card-head .btn-plain`).
 */
private fun Modifier.overhang(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val h = min(placeable.height, height.roundToPx().coerceAtLeast(constraints.minHeight))
    layout(placeable.width, h) { placeable.place(0, (h - placeable.height) / 2) }
}

/**
 * A card's head: the [label] (a heading) with a 19 dp [icon], both in the [metric]'s text-safe ink
 * (the Calories metric draws its glyph in the Ember gradient), an optional [meta] footnote on the
 * right and an optional [action] (usually a [PlainLink]). Without a metric the label and glyph are
 * `label`. [iconBrush] paints the glyph alone (the assistant's Ember sparkle over a `label` title).
 * It brings its own 10 dp of air below, as the web's head does.
 */
@Composable
fun CardHead(
    label: String,
    modifier: Modifier = Modifier,
    icon: EmberIcons? = null,
    metric: Metric? = null,
    meta: String? = null,
    action: (@Composable () -> Unit)? = null,
    iconBrush: Brush? = null,
) = Head(label, modifier, icon, metric, meta, action, below = 10.dp, isHeading = true, iconBrush = iconBrush)

@Composable
private fun Head(
    label: String,
    modifier: Modifier,
    icon: EmberIcons?,
    metric: Metric?,
    meta: String?,
    action: (@Composable () -> Unit)?,
    below: Dp,
    isHeading: Boolean,
    iconBrush: Brush? = null,
) {
    val c = Ember.colors
    val t = Ember.type
    val ink = metric?.let { c.inkOf(it) } ?: c.label
    val labelStyle = t.subhead.copy(lineHeight = 18.sp, color = ink)
    // With a meta beside it, the head needs the label's longest word to fit on the line too: a long
    // Ukrainian word at a large text size would otherwise be split ("Макроелемент / и").
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val longestWord = if (meta == null) {
        0
    } else {
        remember(label, labelStyle, measurer) {
            label.split(' ', '\u00A0').filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width } ?: 0
        }
    }
    Layout(
        content = {
            Row(
                if (isHeading) Modifier.semantics(mergeDescendants = true) { heading() } else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeadIconGap),
            ) {
                if (icon != null) {
                    if (iconBrush != null) {
                        EmberIcon(icon, null, size = HeadIcon, brush = iconBrush)
                    } else if (metric == Metric.Kcal) {
                        EmberIcon(icon, null, size = HeadIcon, brush = EmberBrushes.emberIcon(c))
                    } else {
                        EmberIcon(icon, null, size = HeadIcon, tint = ink)
                    }
                }
                // Next to an action at large text sizes the label wraps between words, never inside one.
                WholeWordsText(label, labelStyle)
            }
            if (meta != null) {
                ControlText(meta, style = t.footnote, color = c.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (action != null) {
                Box(Modifier.overhang(HeadHeight)) {
                    CompositionLocalProvider(LocalInCardHead provides true, content = action)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = below)
            .heightIn(min = HeadHeight),
    ) { measurables, constraints ->
        // One line: the label takes what the meta and the action leave, all centred on the line. When
        // the label's longest word would not fit beside the meta, the meta moves to its own line under
        // the label (the action stays on the first line).
        val width = constraints.maxWidth
        val gap = HeadGap.roundToPx()
        val labelM = measurables[0]
        val metaM = if (meta != null) measurables[1] else null
        val actionM = if (action != null) measurables.last() else null
        val actionP = actionM?.measure(Constraints(maxWidth = width))
        val actionW = actionP?.let { it.width + gap } ?: 0
        val iconW = if (icon != null) (HeadIcon + HeadIconGap).roundToPx() else 0
        val metaNatural = metaM?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val inline = metaM == null || iconW + longestWord + gap + metaNatural + actionW <= width
        if (inline) {
            val metaP = metaM?.measure(Constraints(maxWidth = (width - actionW).coerceAtLeast(0)))
            val metaW = metaP?.let { it.width + gap } ?: 0
            val labelP = labelM.measure(Constraints.fixedWidth((width - metaW - actionW).coerceAtLeast(0)))
            val h = maxOf(constraints.minHeight, labelP.height, metaP?.height ?: 0, actionP?.height ?: 0)
            layout(width, h) {
                labelP.placeRelative(0, centred(labelP.height, h))
                metaP?.placeRelative(width - actionW - metaP.width, centred(metaP.height, h))
                actionP?.placeRelative(width - actionP.width, centred(actionP.height, h))
            }
        } else {
            val labelP = labelM.measure(Constraints.fixedWidth((width - actionW).coerceAtLeast(0)))
            val metaP = metaM.measure(Constraints(maxWidth = (width - iconW).coerceAtLeast(0)))
            val line = maxOf(constraints.minHeight, labelP.height, actionP?.height ?: 0)
            val lineGap = MetaLineGap.roundToPx()
            layout(width, line + lineGap + metaP.height) {
                labelP.placeRelative(0, centred(labelP.height, line))
                actionP?.placeRelative(width - actionP.width, centred(actionP.height, line))
                // Under the label's text, past its glyph.
                metaP.placeRelative(iconW, line + lineGap)
            }
        }
    }
}

/** Centred the way Row's CenterVertically rounds it, so a head sits exactly where it always did. */
private fun centred(size: Int, space: Int): Int = ((space - size) / 2f).roundToInt()

private val HeadIcon = 19.dp
private val HeadIconGap = 6.dp
private val HeadGap = 8.dp
private val MetaLineGap = 2.dp

/**
 * A small stat tile (Steps, Active calories, Exercise…): the label with its icon in the metric's
 * ink, the [value] slot (usually a NumberWithUnit in `stat`), and a 12 sp [footnote]. One TalkBack
 * node. Tiles in a row share their height when the row asks for it (`Row(Modifier.height(IntrinsicSize.Min))`
 * and `Modifier.fillMaxHeight()` on each tile); their values then sit on one line at the bottom.
 */
@Composable
fun StatTile(
    label: String,
    icon: EmberIcons,
    metric: Metric,
    value: @Composable () -> Unit,
    footnote: String?,
    modifier: Modifier = Modifier,
) {
    EmberCard(
        modifier,
        padding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 13.dp),
        mergeDescendants = true,
    ) {
        // Not a heading: the tile is one node ("Steps, 9,184"), not a section.
        Head(label, Modifier, icon, metric, meta = null, action = null, below = 6.dp, isHeading = false)
        // When tiles in a row share the tallest one's height (a label that wraps in one of them), the
        // slack goes above the value, so the numbers line up along the bottom.
        Spacer(Modifier.weight(1f))
        value()
        if (footnote != null) {
            ControlText(
                footnote,
                Modifier.padding(top = 4.dp),
                style = Ember.type.footnote.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = Ember.colors.label2,
            )
        }
    }
}

/**
 * The meal squircle: the slot's gradient, a white glyph, a 1 dp inner highlight along the top edge.
 * 34 dp by default (radius 10), 30 dp in settings rows (radius 9). Decorative: the row names the meal.
 */
@Composable
fun MealTile(slot: MealSlot, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val shape = remember(size) { RoundedCornerShape(size * (10f / 34f)) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MealTiles.brush(slot))
            .topHighlight(shape),
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(MealTiles.icon(slot), null, size = size * (20f / 34f), tint = Ember.colors.onAccent)
    }
}

/**
 * The web's `inset 0 1px 0 rgba(255,255,255,.35)`: the tile's outline minus the same outline moved
 * down 1 dp leaves a thin crescent along the top edge that follows the corners.
 */
private fun Modifier.topHighlight(shape: Shape): Modifier = drawWithCache {
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val lowered = Path().apply {
        addPath(outline, Offset(0f, 1.dp.toPx()))
    }
    val crescent = Path().apply { op(outline, lowered, PathOperation.Difference) }
    onDrawWithContent {
        drawContent()
        drawPath(crescent, MealTiles.Highlight)
    }
}
