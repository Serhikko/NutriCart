package com.nutricart.app.ui.fridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.IngredientEntity

/**
 * Adding to the fridge is a PICKER, never a text field: every name here has to
 * be one a recipe can recognise, or cooking would deduct nothing and "what can
 * I cook" would ignore the item — a fridge that looks right and does nothing.
 */
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

    val selected = ingredients.firstOrNull { it.name == selectedName }
    val grams = gramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val matches = remember(query, ingredients) {
        if (query.isBlank()) ingredients
        else ingredients.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fridge_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedName = null
                    },
                    label = { Text(stringResource(R.string.fridge_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (selected == null) {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(matches, key = { it.id }) { ingredient ->
                            Text(
                                ingredient.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedName = ingredient.name
                                        query = ingredient.name
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text(stringResource(R.string.fridge_grams_label)) },
                        isError = gramsText.isNotBlank() && grams == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && grams != null,
                onClick = {
                    val ingredient = selected ?: return@TextButton
                    val amount = grams ?: return@TextButton
                    onConfirm(ingredient, amount)
                },
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The correction path. An absolute amount, not an addition — and 0 g means the
 * row goes away, so "none left" has exactly one representation.
 */
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
    val grams = gramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = { Text(stringResource(R.string.fridge_grams_label)) },
                    isError = gramsText.isNotBlank() && grams == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.fridge_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onRemove) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = grams != null,
                onClick = {
                    if (gramsText == prefill) onDismiss() else grams?.let(onConfirm)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
