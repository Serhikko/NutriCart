package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.ui.common.hcExerciseLabel
import com.nutricart.app.ui.common.workoutTypeLabel
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.WorkoutItem

@Composable
internal fun ActivityCard(
    state: DashboardUiState,
    onAddWorkout: () -> Unit,
    onDeleteWorkout: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueRow(
                labelRes = R.string.steps_label,
                value = state.steps?.toString() ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.active_kcal_label,
                value = state.activeKcal
                    ?.let { stringResource(R.string.kcal_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.exercise_label,
                value = state.exerciseMinutes
                    ?.let { stringResource(R.string.minutes_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.sleep_label,
                value = state.sleepMinutes
                    ?.let { stringResource(R.string.sleep_value, it / 60, it % 60) }
                    ?: stringResource(R.string.no_data_dash),
            )
            ValueRow(
                labelRes = R.string.heart_rate_label,
                value = state.avgHeartRateBpm
                    ?.let { stringResource(R.string.bpm_value, it) }
                    ?: stringResource(R.string.no_data_dash),
            )

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.workouts_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onAddWorkout) {
                    Text(stringResource(R.string.workout_add))
                }
            }
            if (state.workouts.isEmpty()) {
                Text(
                    stringResource(R.string.workouts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.workouts.forEach { item ->
                    WorkoutRow(item = item, onDelete = onDeleteWorkout)
                }
            }

            HorizontalDivider()
            ValueRow(
                labelRes = R.string.dashboard_current_weight,
                value = stringResource(R.string.weight_kg_value, state.weightKg),
            )
        }
    }
}

/** One workout line: name + amount/source underneath, kcal and (for manual) delete. */
@Composable
private fun WorkoutRow(item: WorkoutItem, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Manual rows have a catalog type; watch rows use their own title,
            // or a name mapped from the raw HC exercise type when there is none.
            val name = when {
                item.type != null -> stringResource(workoutTypeLabel(item.type))
                !item.title.isNullOrBlank() -> item.title
                else -> stringResource(hcExerciseLabel(item.hcExerciseType))
            }
            Text(name, style = MaterialTheme.typography.bodyLarge)
            val amountText = item.minutes?.let { stringResource(R.string.minutes_value, it) }
                ?: item.reps?.let { stringResource(R.string.workout_reps_value, it) }
            val sourceText = stringResource(
                if (item.isFromWatch) R.string.workout_source_watch
                else R.string.workout_source_manual
            )
            Text(
                listOfNotNull(amountText, sourceText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            item.kcal?.let { stringResource(R.string.kcal_value, it) }
                ?: stringResource(R.string.no_data_dash),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!item.isFromWatch) {
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.workout_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueRow(labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
