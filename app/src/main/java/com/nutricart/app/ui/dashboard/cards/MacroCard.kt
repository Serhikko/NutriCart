package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.logic.NutrientTargets
import com.nutricart.app.ui.common.MacroBar
import com.nutricart.app.ui.dashboard.DashboardUiState
import kotlin.math.roundToInt

@Composable
internal fun MacroCard(state: DashboardUiState) {
    val targets = state.targets ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // "Green numbers": the pair turns green inside the 90-110% band.
            MacroBar(
                stringResource(R.string.summary_protein), state.eatenProteinG, targets.proteinG,
                valueColor = targetStateColor(state.eatenProteinG.toDouble(), targets.proteinG),
            )
            MacroBar(
                stringResource(R.string.summary_fat), state.eatenFatG, targets.fatG,
                valueColor = targetStateColor(state.eatenFatG.toDouble(), targets.fatG),
            )
            MacroBar(
                stringResource(R.string.summary_carbs), state.eatenCarbsG, targets.carbsG,
                valueColor = targetStateColor(state.eatenCarbsG.toDouble(), targets.carbsG),
            )
            MacroBar(
                stringResource(R.string.nutrient_fiber), state.eatenFiberG.roundToInt(), state.fiberTargetG,
                valueColor = targetStateColor(state.eatenFiberG, state.fiberTargetG),
            )

            HorizontalDivider()
            // Limits work the other way around: green means UNDER the number.
            LimitRow(R.string.nutrient_sugars, state.eatenSugarsG, state.sugarLimitG)
            LimitRow(R.string.nutrient_salt, state.eatenSaltG, state.saltLimitG)
            LimitRow(R.string.nutrient_sat_fat, state.eatenSatFatG, state.satFatLimitG)
        }
    }
}

/** Color for a "reach the target" nutrient: neutral -> green (in band) -> red. */
@Composable
private fun targetStateColor(eatenG: Double, targetG: Int): Color =
    when (NutrientTargets.targetState(eatenG, targetG)) {
        NutrientTargets.State.GOOD -> MaterialTheme.colorScheme.primary
        NutrientTargets.State.OVER -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** One "stay under the limit" line: green under, amber near, red over. */
@Composable
private fun LimitRow(labelRes: Int, eatenG: Double, limitG: Int) {
    val color = when (NutrientTargets.limitState(eatenG, limitG.toDouble())) {
        NutrientTargets.State.GOOD -> MaterialTheme.colorScheme.primary
        NutrientTargets.State.WARN -> MaterialTheme.colorScheme.tertiary
        NutrientTargets.State.OVER -> MaterialTheme.colorScheme.error
        NutrientTargets.State.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.nutrient_vs_limit, eatenG, limitG),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
