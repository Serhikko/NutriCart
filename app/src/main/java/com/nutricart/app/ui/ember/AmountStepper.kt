package com.nutricart.app.ui.ember

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The amount stepper of the web's Add sheet, used for grams, portions, minutes, reps and kilograms:
// [−] [ 207  g ⇕ ] [+]. The field stays free text (the parent parses it, comma included); the round
// buttons step it, and holding one repeats after a beat, like the phone's own steppers.

/** Holding − or + starts repeating after this long… */
private const val RepeatAfterMillis = 450L

/** …and then steps this often (the web's numbers). */
private const val RepeatEveryMillis = 85L

/**
 * `[−] [ 207  g ⇕ ] [+]`: 48 dp round `fill` buttons named [decreaseLabel] / [increaseLabel] (holding
 * one repeats), the `fill2` well (r16, 52 dp) with a 26 sp tabular number named [fieldLabel] and its
 * [unit]; the well turns `surface` with a 2 dp focus ring while typing, and shows a 2 dp danger ring
 * when [isError]. With [onSwitchUnit], the unit is a 48 dp button (named [switchUnitLabel], or the unit)
 * that switches grams ↔ portions; the new unit drops in.
 */
@Composable
fun AmountStepper(
    text: String,
    onTextChange: (String) -> Unit,
    unit: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    fieldLabel: String,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    onSwitchUnit: (() -> Unit)? = null,
    switchUnitLabel: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val c = Ember.colors
    val t = Ember.type
    val fieldSource = remember { MutableInteractionSource() }
    val focused by fieldSource.collectIsFocusedAsState()
    val well by animateColorAsState(
        if (focused) c.surface else c.fill2, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "well",
    )
    val ring = when {
        isError -> c.danger
        focused -> c.focus
        else -> c.sep
    }
    val numberStyle = t.stat.copy(fontSize = 26.sp, color = c.label)
    // The number box is exactly as wide as the number (the web's field-sizing: content) plus room for
    // the cursor, so "207 g" stays centred in the well while the field itself spans all of it.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberWidth = remember(text, numberStyle, density) {
        with(density) { measurer.measure(text.ifEmpty { "0" }, numberStyle).size.width.toDp() } + 3.dp
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton(EmberIcons.Minus, decreaseLabel, onDecrease)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            textStyle = numberStyle,
            cursorBrush = SolidColor(c.tint),
            interactionSource = fieldSource,
            // The whole well is the field: a tap anywhere in it starts typing (the web's <label>).
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(EmberShapes.amountWell)
                .background(well)
                .border(if (isError || focused) 2.dp else 1.dp, ring, EmberShapes.amountWell)
                .semantics { contentDescription = fieldLabel },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(numberWidth).weight(1f, fill = false)) { inner() }
                    if (onSwitchUnit != null) {
                        UnitSwitch(unit, switchUnitLabel ?: unit, onSwitchUnit)
                    } else {
                        ControlText(unit, style = t.headline, color = c.label2, maxLines = 1, softWrap = false)
                    }
                }
            },
        )
        StepButton(EmberIcons.Plus, increaseLabel, onIncrease)
    }
}

/** − or +: a 48 dp round `fill` button; holding it repeats [onStep] until the finger lifts. */
@Composable
private fun StepButton(icon: EmberIcons, label: String, onStep: () -> Unit) {
    val c = Ember.colors
    val step by rememberUpdatedState(onStep)
    // Set when a hold has already stepped, so the click that ends the hold is not one more step.
    val repeated = remember { booleanArrayOf(false) }
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .pressScale(source, .94f)
            .size(48.dp)
            .clip(EmberShapes.circle)
            .background(c.fill)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    repeated[0] = false
                    // A gesture timeout, not an animation delay: "Remove animations" must not make it fire at once.
                    val lifted = withTimeoutOrNull(RepeatAfterMillis) { waitForUpOrCancellation() ?: Unit }
                    if (lifted == null) {
                        while (true) {
                            repeated[0] = true
                            step()
                            val up = withTimeoutOrNull(RepeatEveryMillis) { waitForUpOrCancellation() ?: Unit }
                            if (up != null) break
                        }
                    }
                }
            }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.circle, c.focus),
                role = Role.Button,
                onClick = {
                    if (repeated[0]) repeated[0] = false else step()
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(icon, null, size = 22.dp, tint = c.label, strokeWidth = 2.2f)
    }
}

/** The unit as a button ("g ⇕"): 17 sp SemiBold label2 with a small up-down glyph; the new unit drops in. */
@Composable
private fun UnitSwitch(unit: String, label: String, onClick: () -> Unit) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { alpha = if (pressed) .55f else 1f }
            .clip(EmberShapes.capsule)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                role = Role.Button,
                onClick = onClick,
            )
            // One stop named by [label]; the clickable before it keeps the click and the role.
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val reduced = Ember.motion.reduced
        AnimatedContent(
            targetState = unit,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None.togetherWith(ExitTransition.None)
                } else {
                    (fadeIn(tween(EmberDurations.State, easing = EmberEasing.Out)) +
                        slideInVertically(tween(EmberDurations.State, easing = EmberEasing.Out)) { it / 3 })
                        .togetherWith(fadeOut(tween(120, easing = EmberEasing.In)))
                }
            },
            label = "unit",
        ) { u ->
            ControlText(u, style = Ember.type.headline.copy(fontWeight = FontWeight.SemiBold), color = c.label2, maxLines = 1, softWrap = false)
        }
        EmberIcon(EmberIcons.Updown, null, size = 14.dp, tint = c.label2, strokeWidth = 2.6f)
    }
}
