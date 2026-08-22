package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.logic.WorkoutMath
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.common.workoutTypeLabel
import kotlin.math.roundToInt

/**
 * Type picker (chips) + one number field. The field means minutes or
 * repetitions depending on the chosen type, and the kcal preview uses the
 * same WorkoutMath the repository will store — no surprises after saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddWorkoutDialog(
    weightKg: Double,
    onConfirm: (WorkoutType, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: the picked type and amount survive screen rotation.
    var selected by rememberSaveable { mutableStateOf(WorkoutType.RUNNING) }
    var amountText by rememberSaveable { mutableStateOf("30") }

    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    val previewKcal = amount?.let {
        when (selected.kind) {
            WorkoutKind.DURATION -> WorkoutMath.kcalForDuration(selected, weightKg, it)
            WorkoutKind.REPS -> WorkoutMath.kcalForReps(selected, weightKg, it)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutType.entries.forEach { type ->
                        FilterChip(
                            selected = type == selected,
                            onClick = {
                                // Only reset the amount when the input UNIT
                                // changes (minutes <-> repetitions).
                                if (type.kind != selected.kind) {
                                    amountText =
                                        if (type.kind == WorkoutKind.DURATION) "30" else "20"
                                }
                                selected = type
                            },
                            label = { Text(stringResource(workoutTypeLabel(type))) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            stringResource(
                                if (selected.kind == WorkoutKind.DURATION) R.string.workout_minutes_label
                                else R.string.workout_reps_label
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (previewKcal != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.kcal_value, previewKcal.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.workout_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { amount?.let { onConfirm(selected, it) } },
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
