package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.ui.common.AnimatedNumber
import com.nutricart.app.ui.common.CalorieRing
import com.nutricart.app.ui.dashboard.DashboardUiState

/** The centerpiece: animated ring + rolling "remaining" counter + key stats. */
@Composable
internal fun HeroRing(state: DashboardUiState) {
    val targets = state.targets ?: return
    val overTarget = state.remainingKcal < 0

    Box(contentAlignment = Alignment.Center) {
        CalorieRing(
            progress = if (targets.kcal > 0) state.eatenKcal / targets.kcal.toFloat() else 0f,
            modifier = Modifier.size(240.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedNumber(
                    value = state.remainingKcal,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (overTarget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.kcal_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.remaining_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HeroStat(stringResource(R.string.eaten_label), state.eatenKcal)
        HeroStat(stringResource(R.string.dashboard_target), targets.kcal)
        HeroStat(stringResource(R.string.calories_out_label), state.caloriesOut)
    }

    // One note, most useful first: "+N kcal" covers watch AND manual workouts;
    // the old watch note stays for the rare measured-zero day (bonus == 0 but
    // the formula did switch to watch mode).
    if (state.activityBonusKcal > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.target_activity_bonus, state.activityBonusKcal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    } else if (state.adjustedByActivity) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.target_adjusted_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun HeroStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value = value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
