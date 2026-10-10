package com.nutricart.app.ui.ember

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Chips: the web's value pickers (meals, allergens, workout types, weekdays, plan days, amounts). They
// wrap onto as many lines as they need and never scroll or clip, so every option is always in sight.
// Drawn 36 dp, 48 dp to the finger (the extra 12 dp is the air between two lines of chips).

/**
 * Chips that wrap onto as many lines as they need (never scroll or clip): 36 dp drawn, 48 dp touch,
 * `fill`, selected = ink + onInk, pressing to .95. Single choice = radio buttons in a selectable
 * group; [multiSelect] = checkboxes. [center] centres each line (inside sheets). A long label wraps
 * inside its chip.
 */
@Composable
fun ChipGroup(
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    groupLabel: String? = null,
    center: Boolean = false,
) {
    val c = Ember.colors
    // The web's chip: 13.5 px SemiBold, slightly tightened.
    val style = Ember.type.subhead.copy(
        fontSize = 13.5.sp, lineHeight = 1.2.em, letterSpacing = (-0.012).em, lineBreak = LineBreak.Heading,
    )
    FlowRow(
        modifier
            .then(if (multiSelect) Modifier else Modifier.selectableGroup())
            .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        options.forEachIndexed { i, label ->
            val on = i in selected
            val source = remember { MutableInteractionSource() }
            // Both colours are read when drawing, so a chip's fade does not recompose it.
            val bg = animateColorAsState(
                if (on) c.ink else c.fill, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "chipBg",
            )
            val fg = animateColorAsState(
                if (on) c.onInk else c.label, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "chipFg",
            )
            val indication = EmberIndication(EmberShapes.chip, c.focus)
            val select = if (multiSelect) {
                Modifier.toggleable(
                    value = on,
                    interactionSource = source,
                    indication = indication,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(i) },
                )
            } else {
                Modifier.selectable(
                    selected = on,
                    interactionSource = source,
                    indication = indication,
                    role = Role.RadioButton,
                    onClick = { onToggle(i) },
                )
            }
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .pressScale(source, .95f)
                    .heightIn(min = 36.dp)
                    .clip(EmberShapes.chip)
                    .drawBehind { drawRect(bg.value) }
                    .then(select)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = style.merge(textAlign = TextAlign.Center), color = { fg.value })
            }
        }
    }
}
