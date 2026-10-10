package com.nutricart.app.ui.ember

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

// The Ember page (§3.4 T): an iOS-style large title that scrolls away with the content, a compact bar
// that fades in over it with its own live text, and the one place that owns the window insets
// (status bar on top; tab bar, navigation bar or a docked button at the bottom; the keyboard).

/** What the list keeps clear at the bottom: the floating tab bar, just the navigation bar, or a docked CTA. */
enum class BottomClearance { TabBar, Stacked, DockedCta }

/** The tint "‹ Previous" link above a stacked screen's large title. */
@Immutable
data class BackLink(val label: String, val onClick: () -> Unit)

private const val TitleKey = "ember-large-title"

/**
 * The Ember page: a LazyColumn whose item 0 is the large-title block (back link, tint [eyebrow], 34 sp
 * [title] as a heading, [actions], an optional row [below]); a compact bar carrying [compactTitle]
 * fades in as it scrolls away. The scaffold owns the insets: status bar on top, the tab bar /
 * navigation bar / [dockedCta] at the bottom, the keyboard. [onRefresh] adds pull to refresh.
 * Items are 12 dp apart (the card stack's gap); content is at most 640 dp wide, centred.
 *
 * [dockedCta] floats 16 dp from the sides (centred; it may fill the width), 12 dp above the
 * navigation bar on stacked screens or 90 dp up over the tab bar with [BottomClearance.TabBar], and
 * above the keyboard. It draws its own floating shadow (the web's per-button shadow), e.g.
 * `EmberButton(…, Modifier.fillMaxWidth().emberShadow(Elevation.Float, EmberShapes.capsule, dark))`,
 * so a pair of buttons gets two shadows, not one round both. Toasts stay above it.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    back: BackLink? = null,
    actions: @Composable RowScope.() -> Unit = {},
    below: (@Composable ColumnScope.() -> Unit)? = null,
    compactTitle: String = title,
    toastHostState: SnackbarHostState? = null,
    bottom: BottomClearance = BottomClearance.TabBar,
    dockedCta: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val c = Ember.colors
    val density = LocalDensity.current
    val gutter = rememberGutter()
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    // The docked button floats over the tab bar on tab screens, over the navigation bar otherwise,
    // and rides above the keyboard when one is open.
    val ctaBase = if (bottom == BottomClearance.TabBar) EmberSpace.DockedCtaBottomTab else EmberSpace.DockedCtaBottomStacked
    val ctaBottom = max(navBottom + ctaBase, imeBottom + EmberSpace.DockedCtaBottomStacked)
    var ctaHeight by remember { mutableIntStateOf(0) }
    val ctaDp = with(density) { ctaHeight.toDp() }
    val clearance = when (bottom) {
        // A button docked above the tab bar ("Bought 3") needs the list to end above it too.
        BottomClearance.TabBar ->
            if (dockedCta != null && ctaHeight > 0) max(EmberSpace.ListBottomTab, ctaBase + ctaDp + EmberSpace.s3)
            else EmberSpace.ListBottomTab
        BottomClearance.Stacked -> EmberSpace.ListBottomStacked
        BottomClearance.DockedCta -> EmberSpace.ListBottomDocked
    }
    val presenting = LocalSheetPresentation.current?.presenting == true

    val first = rememberFirstOpen("title/$title")
    val entrances = rememberEntranceState()

    BoxWithConstraints(modifier.fillMaxSize().background(c.bg)) {
        // Tablets, foldables and landscape: the column stays 640 dp wide, centred.
        val side = max(gutter, (maxWidth - EmberSpace.ContentMaxWidth) / 2)
        val list: @Composable BoxScope.() -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(
                    start = side,
                    end = side,
                    top = statusTop + EmberSpace.LargeTitleTop,
                    bottom = navBottom + clearance,
                ),
                verticalArrangement = Arrangement.spacedBy(EmberSpace.StackGap),
            ) {
                item(key = TitleKey, contentType = TitleKey) {
                    TitleBlock(title, eyebrow, back, actions, below, first, entrances)
                }
                content()
            }
        }
        if (onRefresh != null) {
            EmberPullToRefresh(refreshing, onRefresh, Modifier.fillMaxSize(), content = list)
        } else {
            list()
        }

        // Tab screens: the page fades out under the floating tab bar. Drawn here, under the docked
        // button and the toast, so neither is ever faded by it (the bar itself floats above the page).
        if (bottom == BottomClearance.TabBar) EdgeFade(Modifier.align(Alignment.BottomCenter), visible = !presenting)

        CompactBar(listState, compactTitle, back, statusTop)

        if (dockedCta != null) {
            if (bottom != BottomClearance.TabBar) DockFade(Modifier.align(Alignment.BottomCenter))
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = EmberSpace.ContentMaxWidth + EmberSpace.s8)
                    .fillMaxWidth()
                    .padding(start = EmberSpace.TabBarSide, end = EmberSpace.TabBarSide, bottom = ctaBottom)
                    .onSizeChanged { ctaHeight = it.height },
                contentAlignment = Alignment.BottomCenter,
            ) { dockedCta() }
        }

        if (toastHostState != null) {
            // Never over the docked button or the tab bar.
            val toastBottom = when {
                dockedCta != null && ctaHeight > 0 -> ctaBase + ctaDp + EmberSpace.s3
                bottom == BottomClearance.TabBar -> EmberSpace.ToastBottomTab
                else -> EmberSpace.ToastBottomStacked
            }
            EmberToastHost(
                toastHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)),
                bottomPadding = toastBottom,
            )
        }
    }
}

@Composable
private fun TitleBlock(
    title: String,
    eyebrow: String?,
    back: BackLink?,
    actions: @Composable RowScope.() -> Unit,
    below: (@Composable ColumnScope.() -> Unit)?,
    first: Boolean,
    entrances: EntranceState,
) {
    val c = Ember.colors
    // 18 dp of air below the block: the list's own 12 dp gap plus 6.
    Column(Modifier.fillMaxWidth().padding(bottom = EmberSpace.LargeTitleBottom - EmberSpace.StackGap)) {
        if (back != null) {
            BackLinkRow(back, Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "back"))
        }
        if (eyebrow != null) {
            BasicText(
                eyebrow,
                style = Ember.type.subhead,
                color = { c.tint },
                modifier = Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "eyebrow"),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                title,
                style = Ember.type.largeTitle,
                color = { c.label },
                modifier = Modifier
                    .weight(1f)
                    // The title rises out of its own line: the clip is 4 dp taller than the line on
                    // both sides so accents and descenders are never cut, without moving the layout.
                    .layout { measurable, constraints ->
                        val pad = 4.dp.roundToPx()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height - 2 * pad).coerceAtLeast(0)) { placeable.place(0, -pad) }
                    }
                    .emberEntrance(0, EntranceKind.Title, first, entrances, "title")
                    .padding(vertical = 4.dp)
                    // The page's name: a heading, read first.
                    .semantics {
                        heading()
                        traversalIndex = -1f
                    },
            )
            Row(
                Modifier.emberEntrance(1, EntranceKind.FadeUp, first, entrances, "actions"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        if (below != null) {
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .emberEntrance(1, EntranceKind.FadeUp, first, entrances, "below"),
                content = below,
            )
        }
    }
}

/** "‹ Meal plan": tint, 17 sp medium, a 48 dp target pulled 8 dp up and left so the chevron lines up. */
@Composable
private fun BackLinkRow(back: BackLink, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .layout { measurable, constraints ->
                val pull = 8.dp.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) { placeable.placeRelative(-pull, -pull) }
            }
            .heightIn(min = EmberSpace.TouchTarget)
            .pressScale(interaction, .96f)
            .clip(EmberShapes.capsule)
            .chromeFocusRing(interaction, EmberShapes.capsule)
            .clickable(interaction, indication = null, role = Role.Button, onClick = back.onClick)
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EmberIcon(EmberIcons.Left, null, tint = c.tint)
        BasicText(back.label, style = Ember.type.body.copy(fontWeight = FontWeight.Medium), color = { c.tint }, maxLines = 1)
    }
}

/**
 * The 50 dp bar under the status bar that fades in while the title scrolls away (scroll 24 → 72 dp,
 * no tween: it follows the finger). Its text duplicates the title, so TalkBack skips it; the back
 * chevron stays a real button. It hides while a sheet presents.
 */
@Composable
private fun CompactBar(listState: LazyListState, compactTitle: String, back: BackLink?, statusTop: Dp) {
    val c = Ember.colors
    val density = LocalDensity.current
    val from = with(density) { 24.dp.toPx() }
    val span = with(density) { 48.dp.toPx() }
    val shown = remember(listState, from, span) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - from) / span).coerceIn(0f, 1f)
        }
    }
    val visible by remember(shown) { derivedStateOf { shown.value > 0f } }
    if (!visible || LocalSheetPresentation.current?.presenting == true) return
    val text = remember(compactTitle, c) { compactText(compactTitle, c) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusTop + EmberSpace.CompactBarHeight)
            .graphicsLayer { alpha = shown.value }
            .background(c.matTop)
            .drawBehind {
                val w = .5.dp.toPx()
                drawLine(c.matLine, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(EmberSpace.CompactBarHeight),
        ) {
            BasicText(
                text,
                style = Ember.type.headline.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .graphicsLayer { translationY = 6.dp.toPx() * (1f - shown.value) }
                    .semantics { hideFromAccessibility() },
            )
            if (back != null) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(EmberSpace.TouchTarget)
                        .pressScale(interaction, .90f)
                        .clip(EmberShapes.circle)
                        .chromeFocusRing(interaction, EmberShapes.circle)
                        .clickable(interaction, indication = null, role = Role.Button, onClick = back.onClick)
                        .semantics { contentDescription = back.label },
                    contentAlignment = Alignment.Center,
                ) {
                    EmberIcon(EmberIcons.Left, null, tint = c.tint)
                }
            }
        }
    }
}

/** "Today · 855 left": the part after the first " · " is quieter (label 2, medium), as on the web. */
private fun compactText(text: String, c: EmberColors): AnnotatedString {
    val cut = text.indexOf(" · ")
    return buildAnnotatedString {
        if (cut < 0) {
            withStyle(SpanStyle(color = c.label)) { append(text) }
        } else {
            withStyle(SpanStyle(color = c.label)) { append(text.substring(0, cut)) }
            withStyle(SpanStyle(color = c.label2, fontWeight = FontWeight.Medium)) { append(text.substring(cut)) }
        }
    }
}

/** The page colour rising behind a docked button on stacked screens (the tab screens have the edge fade). */
@Composable
private fun DockFade(modifier: Modifier) {
    val c = Ember.colors
    val density = LocalDensity.current
    val nav = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val brush = remember(c.bg) {
        Brush.verticalGradient(0f to c.bg.copy(alpha = 0f), .40f to c.bg.copy(alpha = .85f), .70f to c.bg)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(110.dp + nav)
            .background(brush)
            .clearAndSetSemantics {},
    )
}

/** ‹ day › navigation (Diary): 48 dp arrow buttons named [previousLabel] / [nextLabel], the day [label]. */
@Composable
fun DayNav(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    // A 44 dp capsule on the outside, 48 dp targets inside: the capsule is drawn 2 dp in from the
    // row's top and bottom.
    Box(modifier.heightIn(min = EmberSpace.TouchTarget)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = 2.dp)
                .emberShadow(Elevation.Card, EmberShapes.capsule, c.isDark)
                .clip(EmberShapes.capsule)
                .background(c.surface)
                .border(.5.dp, c.sep, EmberShapes.capsule),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayNavArrow(EmberIcons.Left, previousLabel, onPrevious, enabled = true)
            BasicText(
                label,
                style = Ember.type.subhead.copy(textAlign = TextAlign.Center),
                color = { c.label },
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .padding(horizontal = 2.dp),
            )
            DayNavArrow(EmberIcons.Right, nextLabel, onNext, enabled = nextEnabled)
        }
    }
}

@Composable
private fun DayNavArrow(icon: EmberIcons, label: String, onClick: () -> Unit, enabled: Boolean) {
    val c = Ember.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(EmberSpace.TouchTarget)
            .pressScale(interaction, .90f)
            .clip(EmberShapes.circle)
            .chromeFocusRing(interaction, EmberShapes.circle)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(icon, null, size = 20.dp, tint = if (enabled) c.label else c.label4)
    }
}

/** Pull to refresh with the Ember indicator (a 28 dp ring that fills with the pull, then spins). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { RefreshIndicator(state, refreshing, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val shown by remember(state) { derivedStateOf { state.distanceFraction > 0f } }
    if (!shown && !refreshing) return
    // Spins only while refreshing (and not at all with "Remove animations"): no frames when idle.
    val turn = if (refreshing && !reduced) {
        rememberInfiniteTransition(label = "refresh").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "turn",
        )
    } else {
        null
    }
    Box(
        modifier
            .padding(top = statusTop)
            .graphicsLayer {
                val f = state.distanceFraction
                // From tucked under the top edge to 16 dp below it at the threshold, then a little more.
                translationY = (-48).dp.toPx() + 64.dp.toPx() * f.coerceAtMost(1.4f)
                alpha = (f * 2f).coerceIn(0f, 1f)
                val s = .6f + .4f * f.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
            }
            .size(40.dp)
            .emberShadow(Elevation.Float, EmberShapes.circle, c.isDark)
            .clip(EmberShapes.circle)
            .background(c.surface)
            .semantics { if (refreshing) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .drawWithCache {
                    val stroke = 4.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val brush = EmberBrushes.emberIcon(c)
                    onDrawBehind {
                        drawArc(c.emberTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        // Pulling fills the ring; refreshing turns a 110° arc round it.
                        val sweep = if (refreshing) 110f else 360f * state.distanceFraction.coerceIn(0f, 1f)
                        rotate(turn?.value ?: 0f) {
                            drawArc(
                                brush, -90f, sweep, false, Offset(inset, inset), arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                },
        )
    }
}
