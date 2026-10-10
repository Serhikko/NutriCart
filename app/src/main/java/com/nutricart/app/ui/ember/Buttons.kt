package com.nutricart.app.ui.ember

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Buttons: the web's capsules (ink primary, fill secondary, plain tint text, water, danger), the round
// icon buttons, the sheet's close button, the plan's 36 dp tools and the tint text link. Everything
// presses (scale, never a ripple), is 48 dp to the finger whatever it draws, and shows the focus ring
// only to keyboard and switch access. This file also holds the press-and-focus indication the other
// controls share.

/** Ink = primary capsule; Fill = secondary; Plain = tint text; Water = the water buttons; Danger = danger text on a danger tint (never a red fill). */
enum class ButtonVariant { Ink, Fill, Plain, Water, Danger }

/** Sm 36 dp, Md 44 dp (48 dp touch), Lg 54 dp full width. */
enum class ButtonSize { Sm, Md, Lg }

/** Fill = `fill` disc; Surface = a white (graphite) disc with the card shadow and a hairline rim; Plain = the glyph alone. */
enum class IconButtonStyle { Fill, Surface, Plain }

/** Disabled controls fade to this alpha (the web's opacity .38). */
internal const val DisabledAlpha = .38f

/** Moves to [target] on [spec], or jumps there under "Remove animations" ([reduced]). */
internal suspend fun Animatable<Float, AnimationVector1D>.emberSettle(target: Float, spec: AnimationSpec<Float>, reduced: Boolean) {
    if (reduced) snapTo(target) else animateTo(target, spec)
}

/** The width of the keyboard focus ring on buttons and rows. */
private val FocusRingWidth = 3.dp

/**
 * Ember's replacement for the ripple: an optional [pressed] overlay (rows and cards darken a little
 * while held, then fade back) and the focus ring, drawn inside [shape] in `focus` when the control has
 * keyboard focus. Buttons pass no overlay: they answer a press by shrinking (pressScale).
 */
internal data class EmberIndication(
    val shape: Shape,
    val focus: Color,
    val pressed: Color = Color.Unspecified,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        EmberIndicationNode(interactionSource, shape, focus, pressed)
}

private class EmberIndicationNode(
    private val source: InteractionSource,
    private val shape: Shape,
    private val focus: Color,
    private val pressed: Color,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val press = Animatable(0f)
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    // In at once (the finger is already down), out over the state duration.
                    is PressInteraction.Press -> launch { press.snapTo(1f) }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        press.emberSettle(0f, tween(EmberDurations.State, easing = EmberEasing.Out), currentValueOf(LocalEmberMotion).reduced)
                    }
                    is FocusInteraction.Focus -> {
                        focused = true
                        invalidateDraw()
                    }
                    is FocusInteraction.Unfocus -> {
                        focused = false
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = press.value
        if (p > 0f && pressed.isSpecified) {
            drawOutline(shape.createOutline(size, layoutDirection, this), pressed.copy(alpha = pressed.alpha * p))
        }
        if (focused) {
            // Inset by half the stroke so a parent's clip (an inset group, a card) never cuts it.
            val w = FocusRingWidth.toPx()
            val inner = Size((size.width - w).coerceAtLeast(0f), (size.height - w).coerceAtLeast(0f))
            translate(w / 2f, w / 2f) {
                drawOutline(shape.createOutline(inner, layoutDirection, this), focus, style = Stroke(w))
            }
        }
    }
}

/** The capsule's colours for a variant: background, then text and glyph. */
@Composable
private fun buttonColors(variant: ButtonVariant): Pair<Color, Color> {
    val c = Ember.colors
    return when (variant) {
        ButtonVariant.Ink -> c.ink to c.onInk
        ButtonVariant.Fill -> c.fill to c.label
        ButtonVariant.Plain -> Color.Transparent to c.tint
        ButtonVariant.Water -> c.waterSoft to c.waterInk
        // 8% keeps the red label at 4.66:1 in light; at night the darker stage takes 14%.
        ButtonVariant.Danger -> c.danger.copy(alpha = if (c.isDark) .14f else .08f) to c.danger
    }
}

/**
 * A capsule button. Md is 44 dp drawn and 48 dp to the finger, Sm 36 dp (48 to the finger), Lg 54 dp
 * and full width. Disabled = alpha .38. [loading] swaps the icon for the Ember spinner and takes
 * clicks away without dimming, so the spinner stays readable. Long labels wrap between words
 * (centred, balanced) and the capsule grows; a word too long for a narrow button shrinks the label a
 * little rather than split. It never clips text.
 */
@Composable
fun EmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ink,
    size: ButtonSize = ButtonSize.Md,
    icon: EmberIcons? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = Ember.colors
    val t = Ember.type
    val (bg, fg) = buttonColors(variant)
    val height = when (size) {
        ButtonSize.Sm -> 36.dp
        ButtonSize.Md -> 44.dp
        ButtonSize.Lg -> 54.dp
    }
    // The web: 16 sp SemiBold capsules, 17 sp on Lg, 15 sp on Sm; plain buttons are 17 sp Medium.
    val style = when {
        variant == ButtonVariant.Plain && size == ButtonSize.Sm -> t.subhead.copy(fontWeight = FontWeight.Medium)
        variant == ButtonVariant.Plain -> t.body.copy(fontWeight = FontWeight.Medium)
        size == ButtonSize.Lg -> t.headline
        size == ButtonSize.Sm -> t.subhead
        else -> t.callout.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em)
    }.copy(lineHeight = 1.18.em, lineBreak = LineBreak.Heading)
    val padding = when {
        variant == ButtonVariant.Plain -> PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        size == ButtonSize.Sm -> PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        else -> PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    }
    val active = enabled && !loading
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .then(if (size == ButtonSize.Lg) Modifier.fillMaxWidth() else Modifier)
            .minimumInteractiveComponentSize()
            .pressScale(source, .96f)
            .heightIn(min = height)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(EmberShapes.capsule)
            .background(bg)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> EmberSpinner(20.dp)
            icon != null -> EmberIcon(icon, null, size = 20.dp, tint = fg)
        }
        TightLabel(text, style.copy(color = fg, textAlign = TextAlign.Center), Modifier.weight(1f, fill = false))
    }
}

/** The glyph drawn inside a round button of [size]: 16 in the 30 dp close button, 18 in 36 dp tools, 22 above. */
private fun glyphFor(size: Dp): Dp = when {
    size <= 30.dp -> 16.dp
    size <= 40.dp -> 18.dp
    else -> 22.dp
}

/**
 * The shared round button: [size] drawn, 48 dp to the finger, pressing to [pressTo]. The disc's
 * [background], the glyph's [tint] and its [glyphScale] are read when drawing, so a plan tool's fade
 * and pop redraw the button without recomposing it.
 */
@Composable
private fun RoundButton(
    icon: EmberIcons,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    background: ColorProducer,
    tint: ColorProducer,
    size: Dp,
    enabled: Boolean,
    surface: Boolean = false,
    pressTo: Float = .94f,
    filled: Boolean = false,
    glyphScale: () -> Float = { 1f },
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val glyph = glyphFor(size)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(source, pressTo)
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .then(
                if (surface) {
                    Modifier
                        .emberShadow(Elevation.Card, EmberShapes.circle, c.isDark)
                        .border(.5.dp, c.sep, EmberShapes.circle)
                } else {
                    Modifier
                },
            )
            .clip(EmberShapes.circle)
            .drawBehind {
                val disc = background()
                if (disc.alpha > 0f) drawRect(disc)
            }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.circle, c.focus),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        EmberGlyph(
            icon,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                val s = glyphScale()
                scaleX = s
                scaleY = s
            },
            size = glyph,
            filled = filled,
            // The stepper's 48 dp buttons carry a slightly heavier glyph, as on the web (2.2).
            strokeWidth = if (size >= 48.dp) 2.2f else null,
        )
    }
}

/**
 * A round icon-only button, [size] drawn (44 dp by default) and 48 dp to the finger, named by
 * [contentDescription]. Plain draws the glyph in `label2` (trash, star off); Fill and Surface in `label`.
 */
@Composable
fun EmberIconButton(
    icon: EmberIcons,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Fill,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val c = Ember.colors
    val disc = when (style) {
        IconButtonStyle.Fill -> c.fill
        IconButtonStyle.Surface -> c.surface
        IconButtonStyle.Plain -> Color.Transparent
    }
    val glyphTint = if (style == IconButtonStyle.Plain) c.label2 else c.label
    RoundButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        background = { disc },
        tint = { glyphTint },
        size = size,
        enabled = enabled,
        surface = style == IconButtonStyle.Surface,
    )
}

/** The sheet's 30 dp close button (48 dp touch); named by the old dialog's Cancel string. */
@Composable
fun CloseButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ember.colors
    RoundButton(
        icon = EmberIcons.X,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        background = { c.fill },
        tint = { c.label2 },
        size = 30.dp,
        enabled = true,
    )
}

/**
 * A 36 dp plan tool (lock, swap, cooked, add), 48 dp to the finger. [selected] = the ink disc (a locked
 * lock); becoming selected gives the glyph one small pop.
 */
@Composable
fun ToolButton(
    icon: EmberIcons,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Ember.colors
    // Read in RoundButton's draw, not here: the fade does not recompose the button.
    val bg = animateColorAsState(
        if (selected) c.ink else c.fill, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "toolBg",
    )
    val fg = animateColorAsState(
        if (selected) c.onInk else c.label, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "toolFg",
    )
    // The glint: .9 → 1 on the bouncy spring when the tool turns on (never on first composition).
    val pop = remember { Animatable(1f) }
    var wasSelected by remember { mutableStateOf(selected) }
    val reduced = Ember.motion.reduced
    LaunchedEffect(selected) {
        if (selected && !wasSelected && !reduced) {
            pop.snapTo(.9f)
            pop.animateTo(1f, EmberSprings.bouncy())
        }
        wasSelected = selected
    }
    RoundButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        background = { bg.value },
        tint = { fg.value },
        size = 36.dp,
        enabled = enabled,
        glyphScale = { pop.value },
    )
}

/**
 * A tint text link ("+ Add food", "‹ Today", "Settings"), 17 sp Medium with an optional leading glyph
 * (15 sp inside a CardHead's action), 48 dp to the finger. It dims a little while pressed, like the
 * web's plain buttons.
 */
@Composable
fun PlainLink(
    text: String,
    onClick: () -> Unit,
    icon: EmberIcons? = null,
    color: Color = Ember.colors.tint,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { alpha = if (pressed) .55f else 1f }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.field, c.focus),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val small = LocalInCardHead.current
        // The back chevron is drawn larger than other leading glyphs (the web's 24 px back link).
        if (icon != null) {
            val glyph = when {
                icon == EmberIcons.Left -> 24.dp
                small -> 18.dp
                else -> 20.dp
            }
            EmberIcon(icon, null, size = glyph, tint = color)
        }
        val style = if (small) Ember.type.subhead else Ember.type.body
        ControlText(text, style = style.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

/**
 * A label that may wrap between words but never inside one: at large text sizes or with long
 * Ukrainian words a narrow segment or a one-third button would otherwise print "Tod / ay". It steps
 * the size down (half an sp at a time) until every line ends at a word boundary and nothing
 * overflows, normally to [min] at most. A single word that is still too wide there ("Сьогодні" in a
 * quarter-width segment at twice the text size) keeps stepping down, but never below what [min] is
 * at the normal text size: a smaller word reads better than a split one.
 */
internal data class WholeWordsAutoSize(val max: TextUnit, val min: TextUnit = 10.sp) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        // [min] in sp grows with the font scale; the floor is [min] as a 1.0 reader sees it.
        val floor = kotlin.math.min(min.value, min.value / fontScale)
        var size = max
        while (size.value > floor) {
            val layout = performLayout(constraints, text, size)
            if (!layout.didOverflowWidth && !layout.splitsWord(text)) return size
            size = (size.value - .5f).sp
        }
        return floor.sp
    }

    private fun TextLayoutResult.splitsWord(text: AnnotatedString): Boolean {
        for (line in 0 until lineCount - 1) {
            val end = getLineEnd(line)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) return true
        }
        return false
    }
}

/**
 * Text that wraps between words but shrinks a little rather than split one (the [WholeWordsAutoSize]
 * rule), for labels in narrow places: buttons, segments and list-row titles at large text sizes.
 */
@Composable
internal fun WholeWordsText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text,
        modifier,
        style = style,
        onTextLayout = onTextLayout,
        autoSize = remember(style.fontSize) { WholeWordsAutoSize(style.fontSize) },
    )
}

/**
 * A wrapped label is as wide as its constraint, not as its longest line, so a button's icon would sit
 * at the far edge with the centred lines away from it. This reports the widest line's width instead
 * (and shifts the text so its lines stay centred in it), keeping the icon next to the words. One-line
 * labels are left as they are.
 */
@Composable
private fun TightLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val lastLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    WholeWordsText(
        text,
        style,
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            // Only a layout of this very measurement counts: intrinsic passes (SheetButtonPair asks how
            // wide a label wants to be) measure differently and must get the plain width.
            val result = lastLayout[0]?.takeIf { it.size.width == placeable.width && it.size.height == placeable.height }
            if (result == null || result.lineCount < 2) {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                var left = Float.MAX_VALUE
                var right = 0f
                for (i in 0 until result.lineCount) {
                    left = min(left, result.getLineLeft(i))
                    right = max(right, result.getLineRight(i))
                }
                val width = ceil(right - left).toInt().coerceIn(constraints.minWidth, placeable.width)
                // Centre the lines' extent in the narrower box.
                val shift = (left - (width - (right - left)) / 2f).roundToInt()
                layout(width, placeable.height) { placeable.place(-shift, 0) }
            }
        },
        onTextLayout = { lastLayout[0] = it },
    )
}

/**
 * Plain text in the controls: Compose's BasicText with the colour and alignment folded into the
 * style, so no Material default (LocalContentColor, LocalTextStyle) can leak into an Ember control.
 */
@Composable
internal fun ControlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text,
        modifier,
        style = style.merge(color = color, textAlign = textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}
