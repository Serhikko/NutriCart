package com.nutricart.app.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.logic.MealSlotGuess
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.AddWeightDialog
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.common.AnimatedNumber
import com.nutricart.app.ui.diary.mealSlotLabel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The "+" sheet: log anything from any tab without losing your place.
 *
 * Food and the scanner NAVIGATE (they need a whole screen); water, a workout
 * and the weight are written from here. Everything here targets TODAY — the
 * date under the title says so out loud, because the diary can be sitting on
 * another day while this sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onLogFood: (MealSlot) -> Unit,
    onScanFood: (MealSlot) -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val weightKg by viewModel.weightKg.collectAsState()
    val today = remember { LocalDate.now().toEpochDay() }
    // remember: a fresh Flow on every recomposition would restart the query.
    // initial = null so the row can stay blank for the frame before the real
    // total arrives, instead of showing 0 ml and animating up to it.
    val waterFlow = remember(today) { viewModel.observeWater(today) }
    val waterMl by waterFlow.collectAsState(initial = null)

    QuickAddSheetContent(
        today = today,
        waterMl = waterMl,
        weightKg = weightKg,
        onDismiss = onDismiss,
        onLogFood = onLogFood,
        onScanFood = onScanFood,
        onAddWater = viewModel::addWater,
        onUndoWater = viewModel::undoWater,
        onAddWorkout = viewModel::addWorkout,
        onLogWeight = viewModel::logWeight,
    )
}

/**
 * The stateless half of [QuickAddSheet]. [today] is the day everything is
 * written to; [waterMl] null = today's total has not arrived yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheetContent(
    today: Long,
    waterMl: Int?,
    weightKg: Double,
    onDismiss: () -> Unit,
    onLogFood: (MealSlot) -> Unit,
    onScanFood: (MealSlot) -> Unit,
    onAddWater: (ml: Int) -> Unit,
    onUndoWater: () -> Unit,
    onAddWorkout: (type: WorkoutType, amount: Int) -> Unit,
    onLogWeight: (kg: Double) -> Unit,
) {
    // skipPartiallyExpanded: with the half-height anchor the last two rows
    // open below the screen edge, and the sheet's own scroll cannot reach
    // them because the gesture drags the sheet instead.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Runs the sheet's hide animation and only then lets the caller drop it
    // from the composition — otherwise every action makes it vanish in a frame.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    // The clock only PRESELECTS the meal; the chips sit right above the two
    // food rows, so a night-shift dinner is one tap away from correct.
    var slot by rememberSaveable { mutableStateOf(MealSlotGuess.forTime(LocalTime.now())) }
    var showWorkout by rememberSaveable { mutableStateOf(false) }
    var showWeight by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.quick_add_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                todayLabel(today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { option ->
                    FilterChip(
                        selected = option == slot,
                        onClick = { slot = option },
                        label = { Text(mealSlotLabel(option)) },
                    )
                }
            }

            ActionRow(R.string.quick_add_food) { closeThen { onLogFood(slot) } }
            ActionRow(R.string.scan_barcode) { closeThen { onScanFood(slot) } }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.water_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    val ml = waterMl
                    if (ml == null) {
                        Text(
                            stringResource(R.string.no_data_dash),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AnimatedNumber(
                            value = ml,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        " " + stringResource(R.string.ml_unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAddWater(250) }) {
                    Text(stringResource(R.string.water_add_250))
                }
                FilledTonalButton(onClick = { onAddWater(500) }) {
                    Text(stringResource(R.string.water_add_500))
                }
                TextButton(
                    onClick = onUndoWater,
                    enabled = (waterMl ?: 0) > 0,
                ) {
                    Text(stringResource(R.string.water_undo))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            ActionRow(R.string.workout_add) { showWorkout = true }
            ActionRow(R.string.weight_card_title) { showWeight = true }
        }
    }

    // Declared OUTSIDE the sheet: a dialog nested in the sheet's content would
    // vanish together with it.
    if (showWorkout) {
        AddWorkoutDialog(
            weightKg = weightKg,
            onConfirm = { type, amount ->
                onAddWorkout(type, amount)
                showWorkout = false
                closeThen(onDismiss)
            },
            onDismiss = { showWorkout = false },
        )
    }
    if (showWeight) {
        AddWeightDialog(
            currentWeightKg = weightKg,
            onConfirm = { kg ->
                onLogWeight(kg)
                showWeight = false
                closeThen(onDismiss)
            },
            onDismiss = { showWeight = false },
        )
    }
}

/** The day everything in this sheet is written to. */
@Composable
private fun todayLabel(epochDay: Long): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return LocalDate.ofEpochDay(epochDay).format(formatter)
}

/** One tappable line of the sheet. */
@Composable
private fun ActionRow(labelRes: Int, onClick: () -> Unit) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
