package com.nutricart.app.ui.ember

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// The floating sheet and the receding page (§3.4 S). Every dialog of the app is an EmberSheet:
// Material's ModalBottomSheet underneath (its own window, so TalkBack modality, the keyboard and
// predictive back come for free), drawn as a card floating 8 dp off the edges, rising on the Ember
// sheet spring while the page behind it recedes onto black.

/**
 * How far sheets present the page, for the shell's recede (§3.4 S): every open [EmberSheet] reports
 * its openness (0 at rest … 1 fully up, following drags) under its own token.
 */
@Stable
class SheetPresentation {
    private val open = mutableStateMapOf<Any, Float>()

    /** The most presented sheet's openness, 0 when none is open. */
    val fraction: Float get() = open.values.maxOrNull() ?: 0f

    /** True while any sheet is open (the tab bar leaves, status-bar icons turn light). */
    val presenting: Boolean get() = open.isNotEmpty()

    fun report(token: Any, fraction: Float) {
        open[token] = fraction.coerceIn(0f, 1f)
    }

    fun remove(token: Any) {
        open.remove(token)
    }
}

/** Provided by the app shell; null in screenshot tests and previews (the page then does not recede). */
val LocalSheetPresentation = staticCompositionLocalOf<SheetPresentation?> { null }

/**
 * The page that recedes while a sheet presents: with a [LocalSheetPresentation] above it, it scales
 * to .93 from its top edge, drops below the status bar and rounds its corners (26 dp) over the
 * `behind` black, following the sheet's openness (drags included); it returns on the smooth spring
 * when the sheet goes. With "Remove animations" on, or without a presentation, it never moves.
 * The app shell wraps its NavHost in it; screenshot tests use it to show a sheet over a page.
 */
@Composable
fun SheetStage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val presentation = LocalSheetPresentation.current
    val recede = remember { Animatable(0f) }
    LaunchedEffect(presentation, reduced) {
        if (presentation == null || reduced) {
            recede.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow { presentation.presenting to presentation.fraction }.collectLatest { (presenting, fraction) ->
            // Follow an open sheet exactly; a sheet that simply goes away hands back on a spring.
            if (presenting) recede.snapTo(fraction) else recede.animateTo(0f, EmberSprings.smooth())
        }
    }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    Box(modifier.fillMaxSize().drawBehind { if (recede.value > 0f) drawRect(c.behind) }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val f = recede.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(.5f, 0f)
                    val s = 1f - .07f * f
                    scaleX = s
                    scaleY = s
                    translationY = (statusTop + 10.dp.toPx()) * f
                    shape = RoundedCornerShape((26f * f).dp)
                    clip = f > 0f
                }
                .drawWithContent {
                    drawContent()
                    // Dark theme: a hairline keeps the receded page apart from the black behind it.
                    val f = recede.value.coerceIn(0f, 1f)
                    if (c.isDark && f > 0f) {
                        val r = (26f * f).dp.toPx()
                        drawRoundRect(
                            c.stageEdge.copy(alpha = c.stageEdge.alpha * f),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(.5.dp.toPx()),
                        )
                    }
                }
                .background(c.bg),
        ) { content() }
    }
}

/** Transparent room above the card, so the spring's 2.5% overshoot never meets the sheet's clip. */
private val SheetSkirt = 24.dp

/**
 * The Ember sheet: Material's ModalBottomSheet (own window: TalkBack modality, IME and predictive back
 * for free) drawn as a floating inset card, radius 38, a 38 × 5 dp grabber, scrollable content
 * (children 12 dp apart). It rises on the Ember sheet spring and reports its openness to
 * [LocalSheetPresentation]. [paneTitle] = the old dialog's title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val presentation = LocalSheetPresentation.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Material 3 1.4 keeps its motion scheme internal, so the sheet's own show animation is Material's
    // quick standard spring. The card draws itself on the Ember sheet spring instead (0.76 / 182, a
    // 2.5% overshoot): while [rise] runs, the card is offset from where Material has put the sheet.
    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rise.value < 1f) rise.animateTo(1f, EmberMotionScheme.defaultSpatialSpec())
    }
    val geometry = remember { SheetGeometry() }
    val offset = { runCatching { sheetState.requireOffset() }.getOrDefault(Float.NaN) }
    val rising = { rise.isRunning || rise.value < 1f }

    if (presentation != null) {
        val token = remember { Any() }
        LaunchedEffect(presentation, sheetState) {
            snapshotFlow { geometry.openness(offset(), rise.value, rising()) }
                .collect { presentation.report(token, it) }
        }
        DisposableEffect(presentation, token) { onDispose { presentation.remove(token) } }
    }

    // While the page recedes onto black, the status and navigation bars show light icons.
    val darkIcons = !c.isDark && (presentation == null || reduced)
    val properties = remember(darkIcons) {
        ModalBottomSheetProperties(isAppearanceLightStatusBars = darkIcons, isAppearanceLightNavigationBars = darkIcons)
    }
    // The insets objects only: their values are read where they are used (placement, layout), so the
    // keyboard's animation re-places the sheet each frame instead of recomposing it.
    val statusBars = WindowInsets.statusBars
    val ime = WindowInsets.ime

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = c.label,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = properties,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onPlaced { coords ->
                    geometry.height = coords.size.height.toFloat()
                    geometry.full = (coords.findRootCoordinates().size.height - ime.getBottom(density)).toFloat()
                },
        ) {
            Spacer(Modifier.height(SheetSkirt))
            Column(
                Modifier
                    .graphicsLayer { translationY = geometry.translation(offset(), rise.value, rising()) }
                    // A touch during the rise hands the card straight to Material's drag.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (rising()) scope.launch { rise.snapTo(1f) }
                        }
                    }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = EmberSpace.SheetInset, end = EmberSpace.SheetInset, bottom = EmberSpace.SheetInset)
                    .layout { measurable, constraints ->
                        // Never taller than the screen minus the status bar and 48 dp.
                        val statusTop = statusBars.getTop(this)
                        val limit = constraints.maxHeight + SheetSkirt.roundToPx() - statusTop - EmberSpace.SheetTopGap.roundToPx()
                        val max = min(constraints.maxHeight, limit.coerceAtLeast(constraints.minHeight))
                        val placeable = measurable.measure(constraints.copy(maxHeight = max))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .then(modifier)
                    .emberShadow(Elevation.Sheet, EmberShapes.sheet, c.isDark)
                    .clip(EmberShapes.sheet)
                    .background(c.sheet)
                    .semantics {
                        if (paneTitle != null) this.paneTitle = paneTitle
                        dismiss {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
                            true
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 5.dp)
                        .background(c.label4, EmberShapes.capsule),
                )
                content()
            }
        }
    }
}

/** Where Material has the sheet, and where the Ember spring wants the card. Pixels, window coordinates. */
@Stable
private class SheetGeometry {
    /** The sheet window's height above the keyboard: Material's hidden position. */
    var full by mutableFloatStateOf(0f)
    /** The sheet's own height (skirt included): how far it travels. */
    var height by mutableFloatStateOf(0f)

    /** The card's extra offset while the Ember rise runs: spring position minus Material's position. */
    fun translation(offset: Float, rise: Float, rising: Boolean): Float {
        if (!rising || offset.isNaN() || height <= 0f || full <= 0f) return 0f
        return (full - height * rise) - offset
    }

    /** 0 hidden … 1 at rest, from where the card is actually drawn. */
    fun openness(offset: Float, rise: Float, rising: Boolean): Float {
        if (offset.isNaN() || height <= 0f || full <= 0f) return 0f
        val top = offset + translation(offset, rise, rising)
        return ((full - top) / height).coerceIn(0f, 1f)
    }
}

/** A sheet's head: an optional [glyph] tile, the [title] (a heading), [subtitle], a close button. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: EmberIcons? = null,
    onClose: (() -> Unit)? = null,
    closeLabel: String? = null,
) {
    val c = Ember.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (glyph != null) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(c.fill2, EmberShapes.glyphLarge),
                contentAlignment = Alignment.Center,
            ) {
                EmberIcon(glyph, null, tint = c.label)
            }
        }
        // A product head (with its glyph) is the web's 19 sp name; a plain sheet title is title 2.
        val titleStyle = if (glyph != null) Ember.type.title2.copy(fontSize = 19.sp, lineHeight = 1.22.em) else Ember.type.title2
        Column(Modifier.weight(1f).padding(top = if (glyph != null) 2.dp else 0.dp)) {
            BasicText(title, style = titleStyle, color = { c.label }, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    style = Ember.type.footnote,
                    color = { c.label2 },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // The close button's 48 dp touch area centres on the title's first line.
        if (onClose != null) CloseButton(closeLabel ?: "", onClose, Modifier.offset(y = (-10).dp))
    }
}

/**
 * A confirmation as a sheet: a 52 dp icon tile, the centred [title] and [text], then the confirm
 * button (Ink, or Danger when [destructive]; never a red fill) above Cancel ([dismissLabel], Fill).
 * On screens 600 dp and wider it is a centred dialog instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberConfirmSheet(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    icon: EmberIcons? = null,
    destructive: Boolean = false,
) {
    val c = Ember.colors
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .emberShadow(Elevation.Sheet, EmberShapes.dialog, c.isDark)
                    .clip(EmberShapes.dialog)
                    .background(c.sheet)
                    .semantics { paneTitle = title }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConfirmBody(title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, icon, destructive)
            }
        }
    } else {
        EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
            ConfirmBody(title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, icon, destructive)
        }
    }
}

@Composable
private fun ConfirmBody(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    icon: EmberIcons?,
    destructive: Boolean,
) {
    val c = Ember.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(if (destructive) c.danger.copy(alpha = .12f) else c.fill2, EmberShapes.nestedSmall),
                contentAlignment = Alignment.Center,
            ) {
                if (destructive) {
                    EmberIcon(icon, null, size = 28.dp, tint = c.danger)
                } else {
                    EmberIcon(icon, null, size = 28.dp, brush = EmberBrushes.emberIcon(c))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        BasicText(
            title,
            style = Ember.type.title2.copy(textAlign = TextAlign.Center),
            color = { c.label },
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            text,
            style = Ember.type.footnote.copy(textAlign = TextAlign.Center),
            color = { c.label2 },
            modifier = Modifier.widthIn(max = 340.dp),
        )
    }
    EmberButton(
        confirmLabel, onConfirm, Modifier.fillMaxWidth(),
        variant = if (destructive) ButtonVariant.Danger else ButtonVariant.Ink,
        size = ButtonSize.Lg,
        icon = if (destructive) icon else null,
    )
    EmberButton(dismissLabel, onDismiss, Modifier.fillMaxWidth(), variant = ButtonVariant.Fill, size = ButtonSize.Lg)
}

/**
 * "Close first, then act" for an [EmberSheet]: [close] slides the sheet away and only then runs the
 * action (navigate, write, dismiss). Dropping a sheet from the composition straight away makes it
 * vanish in a frame, and navigating while it is still up leaves it floating over the next screen.
 *
 * The first call wins: a second tap while the sheet is leaving (Add twice, Log food and then Scan)
 * does nothing, so nothing is written or opened twice. Pass [state] to the sheet it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class SheetCloser(val state: SheetState, private val scope: CoroutineScope) {
    private var closing = false

    fun close(action: () -> Unit) {
        if (closing) return
        closing = true
        // invokeOnCompletion also runs when the slide is cut short (the sheet leaves the composition,
        // or a finger catches it): the action is what the user asked for, so it still happens.
        scope.launch { state.hide() }.invokeOnCompletion { action() }
    }
}

/** A [SheetCloser] with its own fully expanding sheet state (no half-height stop, as every sheet here). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetCloser(): SheetCloser {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    return remember(state, scope) { SheetCloser(state, scope) }
}

/**
 * A sheet's two buttons, [cancel] beside [confirm]: 1 : 2 as on the web while both labels fit their
 * share at full size; otherwise Cancel takes its own width and the confirm the rest; when even that
 * would squeeze a label (long Ukrainian words, large text), they stack, the confirm on top. The
 * slots hold full-width (Lg) buttons; this layout decides how wide each one is.
 */
@Composable
fun SheetButtonPair(
    cancel: @Composable () -> Unit,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            cancel()
            confirm()
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val (c, k) = measurables
        val width = constraints.maxWidth
        val gap = 10.dp.roundToPx()
        val stackGap = 12.dp.roundToPx()
        // What each label needs on one line at its full size (buttons draw their label centred).
        val cancelNeed = c.maxIntrinsicWidth(Constraints.Infinity)
        val confirmNeed = k.maxIntrinsicWidth(Constraints.Infinity)
        val third = (width - gap) / 3
        val widths = when {
            cancelNeed <= third && confirmNeed <= width - gap - third -> third to width - gap - third
            cancelNeed + gap + confirmNeed <= width -> cancelNeed to width - gap - cancelNeed
            else -> null
        }
        if (widths != null) {
            val pc = c.measure(Constraints.fixedWidth(widths.first))
            val pk = k.measure(Constraints.fixedWidth(widths.second))
            val h = maxOf(pc.height, pk.height)
            layout(width, h) {
                pc.placeRelative(0, (h - pc.height) / 2)
                pk.placeRelative(widths.first + gap, (h - pk.height) / 2)
            }
        } else {
            val pc = c.measure(Constraints.fixedWidth(width))
            val pk = k.measure(Constraints.fixedWidth(width))
            layout(width, pk.height + stackGap + pc.height) {
                pk.placeRelative(0, 0)
                pc.placeRelative(0, pk.height + stackGap)
            }
        }
    }
}
