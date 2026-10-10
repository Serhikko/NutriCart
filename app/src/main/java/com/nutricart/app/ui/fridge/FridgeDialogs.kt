package com.nutricart.app.ui.fridge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.ui.common.aisleLabel
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberSearchField
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.pressScale
import kotlin.math.roundToLong

/** One tap on − or + in the grams stepper. */
private const val GramsStep = 50.0

/** Typed grams as a number (a comma counts as the decimal point), or null when it is not one. */
private fun parseGrams(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** [text] stepped by [delta] grams, never below 0, written back as plain digits (no separators to trip the parser). */
private fun stepGrams(text: String, delta: Double): String {
    val next = ((parseGrams(text) ?: 0.0) + delta).coerceAtLeast(0.0)
    return if (next % 1.0 == 0.0) next.roundToLong().toString() else next.toString()
}

/**
 * Adding to the fridge is a PICKER, never a text field: every name here has to
 * be one a recipe can recognise, or cooking would deduct nothing and "what can
 * I cook" would ignore the item — a fridge that looks right and does nothing.
 *
 * An Ember sheet: the search capsule filters the ingredient catalogue as you type (about five rows
 * show, the list scrolls); a picked ingredient becomes an ink chip with an × to change it, followed
 * by the grams stepper (±50 g). Add needs an ingredient and more than 0 g.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToFridgeDialog(
    ingredients: List<IngredientEntity>,
    onConfirm: (IngredientEntity, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    // Typed text, not a Double: rotation-proof, like the v0.10 basket.
    var gramsText by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    val selected = ingredients.firstOrNull { it.name == selectedName }
    val grams = parseGrams(gramsText)?.takeIf { it > 0.0 }
    val matches = remember(query, ingredients) {
        if (query.isBlank()) ingredients
        else ingredients.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val title = stringResource(R.string.fridge_add_title)

    EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
        SheetHeader(title, onClose = onDismiss, closeLabel = stringResource(R.string.cancel))
        if (selected == null) {
            EmberSearchField(
                query = query,
                onQueryChange = {
                    query = it
                    selectedName = null
                },
                // The list already follows the text; Search just puts the keyboard away to show it.
                onSearch = { focus.clearFocus() },
                placeholder = stringResource(R.string.fridge_search_hint),
                searchLabel = stringResource(R.string.search_action),
            )
            Pickable(
                matches = matches,
                onPick = { ingredient ->
                    selectedName = ingredient.name
                    query = ingredient.name
                    focus.clearFocus()
                },
            )
        } else {
            PickedChip(selected.name, onClear = { selectedName = null })
            AmountStepper(
                text = gramsText,
                onTextChange = { gramsText = it },
                unit = stringResource(R.string.plan_unit_g),
                onDecrease = { gramsText = stepGrams(gramsText, -GramsStep) },
                onIncrease = { gramsText = stepGrams(gramsText, GramsStep) },
                fieldLabel = stringResource(R.string.fridge_grams_label),
                decreaseLabel = stringResource(R.string.amount_less),
                increaseLabel = stringResource(R.string.amount_more),
                isError = gramsText.isNotBlank() && grams == null,
                keyboardType = KeyboardType.Number,
            )
        }
        EmberButton(
            text = stringResource(R.string.add_action),
            onClick = {
                val ingredient = selected ?: return@EmberButton
                val amount = grams ?: return@EmberButton
                onConfirm(ingredient, amount)
            },
            modifier = Modifier.padding(top = 4.dp),
            variant = ButtonVariant.Ink,
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
            enabled = selected != null && grams != null,
        )
    }
}

/**
 * The catalogue rows matching the search, in a quiet rounded block: about five show and the rest
 * scroll (the sheet itself scrolls too). Each row is the ingredient and its aisle.
 */
@Composable
private fun Pickable(matches: List<IngredientEntity>, onPick: (IngredientEntity) -> Unit) {
    val c = Ember.colors
    if (matches.isEmpty()) {
        ControlText(
            stringResource(R.string.no_results),
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            style = Ember.type.footnote,
            color = c.label2,
            textAlign = TextAlign.Center,
        )
        return
    }
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(EmberShapes.nestedSmall)
            .background(c.surface2),
    ) {
        itemsIndexed(matches, key = { _, it -> it.id }) { index, ingredient ->
            if (index > 0) {
                Row {
                    Spacer(Modifier.width(16.dp))
                    Spacer(Modifier.weight(1f).height(.5.dp).background(c.sep))
                }
            }
            PickRow(ingredient, onClick = { onPick(ingredient) })
        }
    }
}

@Composable
private fun PickRow(ingredient: IngredientEntity, onClick: () -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ControlText(ingredient.name, style = t.callout, color = c.label)
            ControlText(aisleLabel(ingredient.aisle), style = t.footnote, color = c.label2)
        }
        EmberIcon(EmberIcons.Plus, null, size = 20.dp, tint = c.tint)
    }
}

/** The picked ingredient as an ink chip; the × (or the whole chip) puts the list back to choose again. */
@Composable
private fun PickedChip(name: String, onClear: () -> Unit) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp)
                .pressScale(source, .95f)
                .clip(EmberShapes.capsule)
                .background(c.ink)
                .clickable(
                    interactionSource = source,
                    indication = EmberIndication(EmberShapes.capsule, c.focus),
                    role = Role.Button,
                    onClick = onClear,
                )
                .semantics { contentDescription = name }
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlText(name, style = Ember.type.subhead, color = c.onInk)
            EmberIcon(EmberIcons.X, null, size = 16.dp, tint = c.onInk)
        }
    }
}

/**
 * The correction path. An absolute amount, not an addition — and 0 g means the
 * row goes away, so "none left" has exactly one representation.
 *
 * An Ember sheet titled by the item: the grams stepper (±50 g) prefilled with the rounded amount,
 * the hint that 0 g takes it out, Cancel and Save side by side, and Delete under them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFridgeItemDialog(
    item: FridgeItemUi,
    onConfirm: (Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The field shows the ROUNDED amount, so saving it untouched would quietly
    // rewrite the real stock (247 g becomes 245 g). An unchanged field is
    // therefore treated as "I changed my mind" and writes nothing.
    val prefill = remember(item.name) { item.displayGrams.toString() }
    var gramsText by rememberSaveable(item.name) { mutableStateOf(prefill) }
    val grams = parseGrams(gramsText)?.takeIf { it >= 0.0 }

    EmberSheet(onDismissRequest = onDismiss, paneTitle = item.name) {
        SheetHeader(item.name, subtitle = aisleLabel(item.aisle))
        AmountStepper(
            text = gramsText,
            onTextChange = { gramsText = it },
            unit = stringResource(R.string.plan_unit_g),
            onDecrease = { gramsText = stepGrams(gramsText, -GramsStep) },
            onIncrease = { gramsText = stepGrams(gramsText, GramsStep) },
            fieldLabel = stringResource(R.string.fridge_grams_label),
            decreaseLabel = stringResource(R.string.amount_less),
            increaseLabel = stringResource(R.string.amount_more),
            isError = gramsText.isNotBlank() && grams == null,
            keyboardType = KeyboardType.Number,
        )
        ControlText(
            stringResource(R.string.fridge_edit_hint),
            Modifier.fillMaxWidth(),
            style = Ember.type.footnote,
            color = Ember.colors.label2,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmberButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Fill,
                size = ButtonSize.Lg,
            )
            EmberButton(
                text = stringResource(R.string.save),
                onClick = { if (gramsText == prefill) onDismiss() else grams?.let(onConfirm) },
                modifier = Modifier.weight(2f),
                variant = ButtonVariant.Ink,
                size = ButtonSize.Lg,
                enabled = grams != null,
            )
        }
        EmberButton(
            text = stringResource(R.string.delete),
            onClick = onRemove,
            variant = ButtonVariant.Danger,
            size = ButtonSize.Lg,
            icon = EmberIcons.Trash,
        )
    }
}
