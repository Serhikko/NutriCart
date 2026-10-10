package com.nutricart.app.ui.ember

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// iOS-style grouped lists for Settings, Onboarding, Recipe, Fridge and To buy: the inset group (one
// white card with hairlines between its rows), the rows that go in it (plain, switch, option), the
// head and foot round a group, and the small pieces some rows carry (the switch graphic, the shopping
// tick, the reminder's time pill). The row is always the control: a switch row toggles from anywhere
// on it and is named by its title.

/**
 * Rows of one [InsetGroup]; each [row] after the first gets a 0.5 dp hairline over its top edge,
 * starting [dividerStart] in from the start. The builder is composable, so rows can read resources,
 * loop and branch like any other content.
 */
@LayoutScopeMarker
@Immutable
class InsetGroupScope internal constructor() {
    @Composable
    fun row(dividerStart: Dp = 16.dp, content: @Composable () -> Unit) {
        Hairline(dividerStart)
        Box(Modifier.layoutId(RowTag)) { content() }
    }
}

private val GroupScope = InsetGroupScope()

/** Tags the children of an InsetGroup: the hairline before each row, and the row itself. */
private object HairlineTag
private object RowTag

/** The line drawn over a row's top edge, from [start] to the end (mirrored in RTL). */
@Composable
private fun Hairline(start: Dp) {
    val color = Ember.colors.sep
    Box(
        Modifier
            .layoutId(HairlineTag)
            .drawBehind {
                val s = start.toPx().coerceAtMost(size.width)
                val left = if (layoutDirection == LayoutDirection.Rtl) 0f else s
                val right = if (layoutDirection == LayoutDirection.Rtl) size.width - s else size.width
                drawRect(color, topLeft = Offset(left, 0f), size = Size(right - left, size.height))
            },
    )
}

/**
 * An iOS-style inset grouped list: one `surface` card (radius 24, the card shadow in light) holding
 * rows, with 0.5 dp `sep` hairlines between them, each inset from the start by its row's
 * `dividerStart` (16 by default; 54 under option rows, 58 under rows with a 30 dp icon tile). The
 * hairlines lie over the rows' top edges, as on the web, so they add no height.
 */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable InsetGroupScope.() -> Unit) {
    val c = Ember.colors
    Layout(
        content = { GroupScope.content() },
        modifier = modifier
            .fillMaxWidth()
            .emberShadow(Elevation.Card, EmberShapes.card, c.isDark)
            .clip(EmberShapes.card)
            .background(c.surface),
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val rowConstraints = Constraints(minWidth = width, maxWidth = width)
        val line = Constraints.fixed(width, .5.dp.roundToPx().coerceAtLeast(1))
        // (hairline or null, row) pairs; the first row has no line above it.
        val rows = ArrayList<Pair<Placeable?, Placeable>>()
        var pendingLine: Measurable? = null
        for (m in measurables) {
            if (m.layoutId == HairlineTag) {
                pendingLine = m
            } else {
                val hairline = if (rows.isNotEmpty()) pendingLine?.measure(line) else null
                rows += hairline to m.measure(rowConstraints)
                pendingLine = null
            }
        }
        val height = rows.sumOf { it.second.height }
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            for ((hairline, row) in rows) {
                row.placeRelative(0, y)
                hairline?.placeRelative(0, y)
                y += row.height
            }
        }
    }
}

/** The pressed fill and focus ring of a whole row (the inset group's clip rounds the end rows). */
@Composable
private fun rowIndication() = EmberIndication(RectangleShape, Ember.colors.focus, pressed = Ember.colors.fill)

/**
 * A list row, 54 dp min: [leading] (an icon tile, a MealTile), the [title] (16 sp, in [titleColor]:
 * `tint` for "+ Add…" rows, `danger` for Reset) and [subtitle] (13 sp label2), [trailing] (a value,
 * a button, a pill) and an optional chevron. With [onClick] the whole row is one button that darkens
 * while pressed; without, its texts still read as one TalkBack stop (a trailing button stays its own).
 * [titleDecoration] strikes a bought shopping item through.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    chevron: Boolean = false,
    minHeight: Dp = 54.dp,
    titleColor: Color = Ember.colors.label,
    titleDecoration: TextDecoration? = null,
) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = rowIndication(),
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            WholeWordsText(title, t.callout.copy(color = titleColor, textDecoration = titleDecoration))
            if (subtitle != null) ControlText(subtitle, style = t.footnote, color = c.label2)
        }
        trailing?.invoke()
        if (chevron) EmberIcon(EmberIcons.Right, null, Modifier.offset(x = 4.dp), size = 20.dp, tint = c.label3)
    }
}

/**
 * A row that is a switch: the whole row toggles (Role.Switch, named by [title] and [subtitle]), so a
 * long title wraps beside the switch instead of pushing it off the edge.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = rowIndication(),
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            WholeWordsText(title, t.callout.copy(color = c.label))
            if (subtitle != null) ControlText(subtitle, style = t.footnote, color = c.label2)
        }
        EmberSwitch(checked, Modifier.wrapContentWidth())
    }
}

/**
 * One choice of a radio list (activity level, goal): a 24 dp mark on the leading side (an empty ring;
 * chosen = an ink disc with a white check that pops in), the [title] (17 sp SemiBold) and an optional
 * [detail]. Put it in an InsetGroup with `dividerStart = 54.dp`.
 */
@Composable
fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    // 0 = empty ring … 1 = ink disc; the disc arrives on the bouncy spring, leaves quickly.
    val p by animateFloatAsState(
        if (selected) 1f else 0f,
        emberSpec(if (selected) EmberSprings.bouncy() else tween(EmberDurations.Press, easing = EmberEasing.In)),
        label = "option",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = rowIndication(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            // The empty ring is a quiet hairline (the web's sep-strong), not the chevron grey: the
            // row's title names the choice, and the chosen one is the high-contrast ink disc.
            val ring = c.sepStrong
            val ink = c.ink
            Canvas(Modifier.size(24.dp)) {
                val r = size.minDimension / 2f
                val w = 1.5.dp.toPx()
                val q = p.coerceIn(0f, 1f)
                if (q < 1f) drawCircle(ring.copy(alpha = ring.alpha * (1f - q)), r - w / 2f, style = Stroke(w))
                if (p > 0f) drawCircle(ink, r * p.coerceAtMost(1.06f))
            }
            EmberIcon(
                EmberIcons.Check, null,
                Modifier.graphicsLayer {
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = .6f + .4f * p
                    scaleY = .6f + .4f * p
                },
                size = 14.dp, tint = c.onInk, strokeWidth = 3.2f,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            WholeWordsText(title, t.headline.copy(color = c.label))
            if (detail != null) ControlText(detail, style = t.footnote, color = c.label2)
        }
    }
}

/** The head of an inset group: 13 sp SemiBold label2, 22 dp above, 7 below, 16 in from the edge (a heading). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    ControlText(
        text,
        style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em),
        color = Ember.colors.label2,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 7.dp)
            .semantics { heading() },
    )
}

/** Neutral = label2 explanations; Good / Error = result lines (`good` / `danger`). */
enum class FooterTone { Neutral, Good, Error }

/**
 * The note under an inset group, 16 in from the edge. [live] makes it a polite live region and fades
 * a new text up as it arrives (Settings result lines, "Key saved").
 */
@Composable
fun SectionFooter(
    text: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    tone: FooterTone = FooterTone.Neutral,
) {
    val c = Ember.colors
    // Fades up only when the text changes after the first composition: never on screen entry.
    val p = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(text) }
    val reduced = Ember.motion.reduced
    LaunchedEffect(text) {
        if (text != shown) {
            shown = text
            if (!reduced) p.snapTo(0f)
            p.emberSettle(1f, tween(EmberDurations.Revisit, easing = EmberEasing.Out), reduced)
        }
    }
    ControlText(
        text,
        style = Ember.type.footnote.copy(lineHeight = 18.sp),
        color = when (tone) {
            FooterTone.Neutral -> c.label2
            FooterTone.Good -> c.good
            FooterTone.Error -> c.danger
        },
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 7.dp)
            .graphicsLayer {
                alpha = p.value
                translationY = 6.dp.toPx() * (1f - p.value)
            }
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    )
}

/**
 * The switch graphic, 51 × 31: `fill3` track, `good` when on, a white knob with a soft shadow that
 * springs across (snappy). Decorative: the row around it is the toggle.
 */
@Composable
fun EmberSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val x by animateFloatAsState(if (checked) 1f else 0f, emberSpec(EmberSprings.snappy()), label = "knob")
    // Read in draw (as the knob's offset is read in layout): the fade does not recompose the switch.
    val track = animateColorAsState(
        if (checked) c.good else c.fill3, emberSpec(tween(200, easing = EmberEasing.Out)), label = "track",
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(EmberShapes.capsule)
            .drawBehind { drawRect(track.value) }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset((2f + 20f * x).dp.roundToPx(), 0) }
                .size(27.dp)
                // The web knob: 0 3px 8px rgba(0,0,0,.15), 0 1px 1px rgba(0,0,0,.08).
                .dropShadow(EmberShapes.circle, Shadow(radius = 8.dp, color = Color.Black.copy(.15f), offset = DpOffset(0.dp, 3.dp)))
                .dropShadow(EmberShapes.circle, Shadow(radius = 1.dp, color = Color.Black.copy(.08f), offset = DpOffset(0.dp, 1.dp)))
                .background(c.knob, EmberShapes.circle),
        )
    }
}

/** Off = an empty ring; On = the Ember disc with a white check (bought); Have = already at home. */
enum class CheckState { Off, On, Have }

/** The check glyph's path on the 24 grid (EmberIcons.Check), drawn on progressively. */
private val CheckPath: Path = PathParser().parsePathString("M5 12.8 9.4 17 19 7.2").toPath()

/**
 * The shopping tick, 26 dp: Off = an empty ring; On = the Ember gradient fills the disc from the
 * centre while a white check draws itself (snappy); Have = a `fill2` disc with a fridge glyph (not
 * tickable). Decorative: the row is the checkbox and says its state.
 */
@Composable
fun CheckDisc(state: CheckState, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val on = state == CheckState.On
    val fillIn = remember { Animatable(if (on) 1f else 0f) }
    val draw = remember { Animatable(if (on) 1f else 0f) }
    val reduced = Ember.motion.reduced
    LaunchedEffect(on) {
        if (on) {
            launch { fillIn.emberSettle(1f, EmberSprings.snappy(), reduced) }
            draw.emberSettle(1f, tween(320, 60, EmberEasing.Out), reduced)
        } else {
            draw.snapTo(0f)
            fillIn.emberSettle(0f, tween(EmberDurations.Press, easing = EmberEasing.In), reduced)
        }
    }
    val brush = EmberBrushes.emberIcon(c)
    // Off is the same quiet hairline as an empty option ring (the row names the item and its state).
    val ring = if (state == CheckState.Have) c.label4 else c.sepStrong
    val have = c.fill2
    val tick = c.onAccent
    Box(modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val r = size.minDimension / 2f
            val w = 1.6.dp.toPx()
            if (state == CheckState.Have) drawCircle(have, r)
            // The ring gives way as the Ember disc grows over it, so no grey edge shows round the fill.
            val f = fillIn.value.coerceIn(0f, 1f)
            if (f < 1f) drawCircle(ring.copy(alpha = ring.alpha * (1f - f)), r - w / 2f, style = Stroke(w))
            if (fillIn.value > 0f) drawCircle(brush, r * fillIn.value.coerceAtMost(1.02f))
            val d = draw.value
            if (d > 0f) {
                // The 24-grid check scaled into a 15 dp box in the middle of the disc.
                val box = 15.dp.toPx()
                val s = box / 24f
                val measure = PathMeasure().apply { setPath(CheckPath, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * d, part, true)
                translate((size.width - box) / 2f, (size.height - box) / 2f) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawPath(part, tick, style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        if (state == CheckState.Have) EmberIcon(EmberIcons.Fridge, null, size = 14.dp, tint = c.label2)
    }
}

/**
 * A reminder's time ("08:30"): a 34 dp capsule, its own button (48 dp to the finger) named by
 * [contentDescription] ("Breakfast reminder time, 08:30"). On = `label` on `fill`; off = `label2` on
 * `fill2` (4.61:1).
 */
@Composable
fun TimePill(text: String, onClick: () -> Unit, contentDescription: String, on: Boolean, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    // Both colours are read when drawing, so the fade does not recompose the pill.
    val bg = animateColorAsState(if (on) c.fill else c.fill2, emberSpec(tween(EmberDurations.State)), label = "pillBg")
    val fg = animateColorAsState(if (on) c.label else c.label2, emberSpec(tween(EmberDurations.State)), label = "pillFg")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(source, .95f)
            .heightIn(min = 34.dp)
            .clip(EmberShapes.capsule)
            .drawBehind { drawRect(bg.value) }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            Modifier.clearAndSetSemantics {},
            style = Ember.type.rowNumber.copy(fontSize = 15.sp),
            color = { fg.value },
        )
    }
}
