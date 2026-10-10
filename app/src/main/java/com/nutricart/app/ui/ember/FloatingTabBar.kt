package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// The floating capsule tab bar (§3.4 P): four content-weighted tabs on one opaque capsule, a pill
// that stretches toward the tab you pick and catches up, and the separate round + button. Both
// float over the page (they take no layout space) above the scroll-edge fade.

@Immutable
data class TabItem(val route: String, val label: String, val icon: EmberIcons)

/**
 * The floating capsule tab bar (62 dp, 16 dp from the sides, above the navigation bar) with one pill
 * behind the selected tab, and the separate 62 dp + button ([addLabel], no visible label).
 * [visible] = false slides it away (stacked screens, a sheet presenting); it comes back on a spring.
 */
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced

    // First composition of the session: the bar rises 24 dp into place.
    val intro = rememberFirstOpen("tabbar")
    val rise = remember { Animatable(if (intro && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (rise.value < 1f) rise.animateTo(1f, tween(700, 120, EmberEasing.Snappy))
    }

    // 0 = in place, 1 = gone below the screen edge. Leaves on a short ease-in, returns on a spring.
    val hidden = remember { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) {
        if (visible) hidden.animateTo(0f, EmberSprings.snappy()) else hidden.animateTo(1f, tween(300, easing = EmberEasing.In))
    }
    val gone by remember { derivedStateOf { hidden.value >= 1f } }
    // Fully away: nothing left to tap or to read.
    if (!visible && gone) return

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .widthIn(max = EmberSpace.ContentMaxWidth + EmberSpace.TabBarSide * 2)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = EmberSpace.TabBarSide, end = EmberSpace.TabBarSide, bottom = EmberSpace.TabBarBottom)
                .graphicsLayer {
                    val h = hidden.value
                    val r = rise.value
                    translationY = size.height * 1.6f * h + 24.dp.toPx() * (1f - r)
                    alpha = ((1f - h) * r).coerceIn(0f, 1f)
                    // Fade each draw, not an offscreen copy: that would clip the shadow to the bar.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                // Read after the page's content (the bar overlays the page but belongs at the end).
                .then(
                    if (visible) Modifier.semantics { isTraversalGroup = true; traversalIndex = 1f }
                    else Modifier.clearAndSetSemantics {},
                ),
            horizontalArrangement = Arrangement.spacedBy(EmberSpace.TabBarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabCapsule(items, selectedRoute, onSelect, Modifier.weight(1f))
            AddButton(onAdd, addLabel)
        }
    }
}

/** The scroll-edge fade under the tab bar: the page colour rising from transparent (nav bar + 150 dp). */
@Composable
fun EdgeFade(modifier: Modifier = Modifier, visible: Boolean = true) {
    val bg = Ember.colors.bg
    val shown by animateFloatAsState(
        if (visible) 1f else 0f,
        if (visible) EmberSprings.snappy() else tween(300, easing = EmberEasing.In),
        label = "edgeFade",
    )
    if (shown <= 0f) return
    val density = LocalDensity.current
    val nav = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    // The web's stops: clear at the top, 70% at 26%, 94% at 44%, solid from 60% down.
    val brush = remember(bg) {
        Brush.verticalGradient(
            0f to bg.copy(alpha = 0f), .26f to bg.copy(alpha = .70f), .44f to bg.copy(alpha = .94f), .60f to bg,
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(EmberSpace.EdgeFadeHeight + nav)
            .graphicsLayer { alpha = shown.coerceIn(0f, 1f) }
            .background(brush)
            .clearAndSetSemantics {},
    )
}

// ---------------------------------------------------------------------------------------------
// The tabs and their pill
// ---------------------------------------------------------------------------------------------

@Composable
private fun TabCapsule(items: List<TabItem>, selectedRoute: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val pill = remember { PillState() }
    var bounds by remember { mutableStateOf(FloatArray(0)) }
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    LaunchedEffect(selectedIndex, bounds, reduced) { pill.moveTo(selectedIndex, bounds, animate = !reduced) }

    // A tab label never grows past 15 dp, whatever the font scale: the bar keeps its height.
    val density = LocalDensity.current
    val capStyle = remember(density) {
        val capSp = with(density) { min(11.sp.toPx(), 15.dp.toPx()).toSp() }
        EmberTypeDefault.tab.copy(fontSize = capSp, lineHeight = 1.15.em)
    }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val labels = items.map { it.label }

    BoxWithConstraints(
        modifier
            .height(EmberSpace.TabBarHeight)
            .emberShadow(Elevation.Float, EmberShapes.capsule, c.isDark)
            .clip(EmberShapes.capsule)
            .background(c.matBar)
            .materialEdge(EmberShapes.capsule)
            .padding(4.dp),
    ) {
        // Long words at a large text size ("Холодильник" at 1.3×) would not fit four tabs side by side:
        // every label then steps down to one shared size that fits, rather than any of them being cut.
        val labelStyle = remember(labels, capStyle, constraints.maxWidth, density) {
            fittedTabStyle(labels, capStyle, constraints.maxWidth, measurer, density)
        }
        Layout(
            content = {
                items.forEach { item ->
                    Tab(item, item.route == selectedRoute, labelStyle) { onSelect(item.route) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .drawBehind {
                    if (!pill.shown) return@drawBehind
                    val l = pill.left.value
                    val r = pill.right.value
                    val h = size.height * pill.squash.value
                    drawRoundRect(
                        color = c.fill,
                        topLeft = Offset(min(l, r), (size.height - h) / 2f),
                        size = Size(abs(r - l), h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                },
        ) { measurables, constraints ->
            // Content-weighted: every tab gets its natural width plus an equal share of what is
            // left. No tab is ever narrower than 48 dp: if the labels still do not fit, the room
            // comes out of the wider tabs only.
            val total = constraints.maxWidth
            val height = constraints.maxHeight
            val minTab = MinTab.roundToPx()
            val natural = measurables.map { max(it.maxIntrinsicWidth(height), minTab) }
            val sum = natural.sum()
            val widths = if (sum <= total) {
                val extra = (total - sum) / max(1, natural.size)
                natural.mapIndexed { i, w -> w + extra + if (i == natural.lastIndex) (total - sum) - extra * natural.size else 0 }
            } else {
                shrinkAboveFloor(natural, total, minTab)
            }
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
            val edges = FloatArray(placeables.size * 2)
            var x = 0
            placeables.forEachIndexed { i, p ->
                val left = if (layoutDirection == LayoutDirection.Rtl) total - x - p.width else x
                edges[2 * i] = left.toFloat()
                edges[2 * i + 1] = (left + p.width).toFloat()
                x += p.width
            }
            if (!edges.contentEquals(bounds)) bounds = edges
            layout(total, height) {
                var px = 0
                placeables.forEach { p ->
                    p.placeRelative(px, 0)
                    px += p.width
                }
            }
        }
    }
}

private val MinTab = 48.dp
private val TabSidePadding = 6.dp
private val TabIconSize = 25.dp
/** The smallest a tab label gets to fit (the cap is 11 dp at normal text size); below it, it ellipsizes. */
private val TabLabelFloor = 9.dp

/**
 * The tab labels' style: [cap] when the four tabs fit in [width] at it, otherwise the largest smaller
 * size (not under [TabLabelFloor]) at which they do, the same for every tab so the row stays even.
 * A tab is its label or its 25 dp icon, whichever is wider, plus 6 dp each side, and never under 48 dp.
 */
private fun fittedTabStyle(
    labels: List<String>,
    cap: TextStyle,
    width: Int,
    measurer: TextMeasurer,
    density: Density,
): TextStyle {
    if (labels.isEmpty() || width <= 0 || width == Constraints.Infinity) return cap
    val pad = with(density) { (TabSidePadding * 2).roundToPx() }
    val icon = with(density) { TabIconSize.roundToPx() }
    val minTab = with(density) { MinTab.roundToPx() }
    val natural = labels.map { measurer.measure(it, cap, softWrap = false, maxLines = 1).size.width }
    // Text widths scale with the size; a pixel of slack per tab absorbs rounding.
    fun fits(k: Float) = natural.sumOf { max(minTab, max(icon, ceil(it * k).toInt() + 1) + pad) } <= width
    if (fits(1f)) return cap
    val capPx = with(density) { cap.fontSize.toPx() }
    var lo = (with(density) { TabLabelFloor.toPx() } / capPx).coerceAtMost(1f)
    if (!fits(lo)) return cap.copy(fontSize = cap.fontSize * lo)
    var hi = 1f
    repeat(12) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return cap.copy(fontSize = cap.fontSize * lo)
}

/**
 * Widths for tabs whose [natural] widths add up to more than [total]: each keeps [floor] and gives up
 * a share of what it has above it, in proportion, so a short label's tab is never squeezed under 48 dp.
 */
private fun shrinkAboveFloor(natural: List<Int>, total: Int, floor: Int): List<Int> {
    val n = natural.size
    if (n == 0) return natural
    if (total <= floor * n) {
        // Not even the floors fit (a tiny window): share it out evenly.
        val each = total / n
        return List(n) { i -> if (i == n - 1) total - each * (n - 1) else each }
    }
    val above = natural.sumOf { it - floor }
    val room = total - floor * n
    val widths = natural.map { floor + ((it - floor).toLong() * room / above).toInt() }.toMutableList()
    widths[n - 1] += total - widths.sum()
    return widths
}

@Composable
private fun Tab(item: TabItem, selected: Boolean, labelStyle: TextStyle, onClick: () -> Unit) {
    val c = Ember.colors
    val interaction = remember { MutableInteractionSource() }
    val on: State<Float> = animateFloatAsState(if (selected) 1f else 0f, tween(200, easing = EmberEasing.Out), label = "tab")
    Column(
        Modifier
            .fillMaxHeight()
            .pressScale(interaction, .94f)
            .chromeFocusRing(interaction, EmberShapes.capsule)
            .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = TabSidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(TabIconSize)) {
            // The two looks cross-fade: grey line art, and the Ember gradient on the active tab.
            EmberIcon(item.icon, null, Modifier.graphicsLayer { alpha = 1f - on.value }, size = TabIconSize, tint = c.label2)
            EmberIcon(
                item.icon, null, Modifier.graphicsLayer { alpha = on.value },
                size = TabIconSize, brush = EmberBrushes.emberIcon(c),
            )
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            item.label,
            style = labelStyle.copy(textAlign = TextAlign.Center),
            color = { lerp(c.label2, c.label, on.value) },
            maxLines = 1,
            softWrap = false,
            // Only below the 9 dp floor (fittedTabStyle), which no shipped translation reaches.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit, addLabel: String) {
    val c = Ember.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(EmberSpace.TabBarHeight)
            .pressScale(interaction, .90f, EmberSprings.bouncy())
            .emberShadow(Elevation.Float, EmberShapes.circle, c.isDark)
            .clip(EmberShapes.circle)
            .background(c.matBar)
            .materialEdge(EmberShapes.circle)
            .chromeFocusRing(interaction, EmberShapes.circle)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = addLabel },
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(EmberIcons.Plus, null, size = 30.dp, brush = EmberBrushes.emberIcon(c))
    }
}

/** The pill's two edges and its squash, moved as one: the leading edge runs ahead, the other catches up. */
@Stable
private class PillState {
    val left = Animatable(0f)
    val right = Animatable(0f)
    val squash = Animatable(1f)
    var shown by mutableStateOf(false)
        private set
    private var index = -1
    private var bounds = FloatArray(0)

    suspend fun moveTo(target: Int, edges: FloatArray, animate: Boolean) = coroutineScope {
        if (target < 0 || edges.size < 2 * (target + 1)) {
            shown = false
            index = -1
            return@coroutineScope
        }
        val l = edges[2 * target]
        val r = edges[2 * target + 1]
        val from = index
        val sameLayout = bounds.contentEquals(edges)
        index = target
        bounds = edges
        if (!shown || from < 0 || from == target || !sameLayout || !animate) {
            // First placement, a re-layout (rotation, font size) or reduced motion: no travel.
            left.snapTo(l)
            right.snapTo(r)
            squash.snapTo(1f)
            shown = true
            return@coroutineScope
        }
        // Direction of travel on screen (positions, not indices, so right-to-left layouts work too).
        val forward = (l + r) / 2f >= (left.value + right.value) / 2f
        val lead = if (forward) right else left
        val trail = if (forward) left else right
        launch { lead.animateTo(if (forward) r else l, spring(0.72f, 520f)) }
        launch { trail.animateTo(if (forward) l else r, spring(0.86f, 260f)) }
        launch { squash.animateTo(1f, keyframes { durationMillis = 640; .92f at 256 using EmberEasing.Out }) }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared chrome details
// ---------------------------------------------------------------------------------------------

/**
 * Floating chrome's rim (the web's inset shadows): a 1 dp `matEdge` highlight along the top, drawn
 * over the material and under the content, and a 0.5 dp `matLine` outline.
 */
@Composable
internal fun Modifier.materialEdge(shape: Shape): Modifier {
    val c = Ember.colors
    return this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val outer = Path().apply { addOutline(outline) }
            val shifted = Path().apply {
                addOutline(outline)
                translate(Offset(0f, 1.dp.toPx()))
            }
            val crescent = Path.combine(PathOperation.Difference, outer, shifted)
            onDrawBehind { drawPath(crescent, c.matEdge) }
        }
        .border(0.5.dp, c.matLine, shape)
}

/** A 3 dp `focus` ring while a keyboard or switch access has focus on the control. */
@Composable
internal fun Modifier.chromeFocusRing(interaction: InteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return if (focused) this.border(3.dp, Ember.colors.focus, shape) else this
}
