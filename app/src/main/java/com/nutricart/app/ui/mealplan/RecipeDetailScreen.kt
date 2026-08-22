package com.nutricart.app.ui.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.ui.common.LoadingBox
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Ingredients (scaled to the chosen portion) and cooking steps of a recipe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showCooked by rememberSaveable { mutableStateOf(false) }

    if (showCooked) {
        CookedPortionsDialog(
            onConfirm = { portions ->
                viewModel.cook(portions)
                showCooked = false
            },
            onDismiss = { showCooked = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.details?.nutrition?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val details = state.details
        if (state.loading || details == null) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
        } else {
            val factor = state.portionFactor
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (factor != 1.0) {
                            Text(
                                stringResource(R.string.per_portion_note, factor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.kcal_value,
                                (details.nutrition.kcal * factor).roundToInt(),
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(
                                R.string.recipe_macros_line,
                                (details.nutrition.proteinG * factor).roundToInt(),
                                (details.nutrition.fatG * factor).roundToInt(),
                                (details.nutrition.carbsG * factor).roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.cook_time_value, details.nutrition.cookTimeMin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.ingredients_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                details.ingredients.forEach { row ->
                    // Scale to the portion, then round: big amounts to 5 g
                    // ("87.3 g" is silly), small ones to whole grams — 2 g of
                    // salt must never display as "0 g".
                    val scaled = row.grams * factor
                    val grams =
                        if (scaled < 10.0) scaled.roundToInt().coerceAtLeast(1)
                        else roundTo5(scaled)
                    val pieceHint = row.gramsPerPiece
                        ?.let { ceil(row.grams * factor / it).toInt() }
                        ?.let { stringResource(R.string.piece_hint, it) }
                        ?: ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.grams_value, grams) + pieceHint,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Right under the ingredients, because that is the list the
                // fridge is about to lose.
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCooked = true },
                    enabled = !state.cooked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (state.cooked) R.string.fridge_cooked_toast
                            else R.string.fridge_cooked_action
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.steps_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                details.steps.forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * "How many portions did you cook?" — one number, defaulting to one.
 *
 * Batch cooking plans the SAME pot for two or three days, so a single tap has
 * to be able to say "I cooked all three". Asking beats guessing from the plan:
 * a hidden multiplier is exactly the kind of invisible arithmetic that makes a
 * fridge quietly wrong.
 */
@Composable
private fun CookedPortionsDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var portions by rememberSaveable { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fridge_cooked_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(
                        onClick = { portions-- },
                        enabled = portions > 1,
                    ) { Text("−") }
                    Text(
                        portions.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TextButton(
                        onClick = { portions++ },
                        enabled = portions < MAX_PORTIONS,
                    ) { Text("+") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.fridge_cooked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(portions) }) {
                Text(stringResource(R.string.fridge_cooked_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A week has seven days; nobody cooks more portions of one dish at once. */
private const val MAX_PORTIONS = 7

private fun roundTo5(value: Double): Int = ((value / 5.0).roundToInt() * 5)
