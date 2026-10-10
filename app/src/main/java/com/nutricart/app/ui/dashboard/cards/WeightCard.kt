package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.WeightLineChart

/**
 * Weight: the current weight in large numerals (the card's meta names it, where the activity card
 * used to say "Current weight 78.4 kg"), the kg-per-week trend as a capsule (green when losing), and
 * the last 30 days as a line from water blue to teal, or the hint until two different days are
 * weighed. [first] draws the line on the first open of the day.
 */
@Composable
internal fun WeightCard(state: DashboardUiState, modifier: Modifier = Modifier, first: Boolean = false) {
    val c = Ember.colors
    val t = Ember.type
    EmberCard(modifier) {
        CardHead(
            stringResource(R.string.weight_card_title),
            icon = EmberIcons.Scale,
            metric = Metric.Weight,
            meta = stringResource(R.string.dashboard_current_weight),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ValueText(stringResource(R.string.weight_kg_value, state.weightKg), t.stat.copy(fontSize = 30.sp), unitSize = 15.sp)
            state.weightTrend?.let { trend ->
                val losing = trend.slopeKgPerWeek < 0.0
                // The string signs the slope itself; its minus becomes a real minus sign.
                val text = stringResource(R.string.weight_trend_value, trend.slopeKgPerWeek).replace('-', '−')
                BasicText(
                    text,
                    Modifier
                        .background(if (losing) c.goodSoft else c.fill, EmberShapes.capsule)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    style = t.footnote.copy(fontWeight = FontWeight.SemiBold, color = if (losing) c.good else c.label),
                    maxLines = 1,
                )
            }
        }
        if (state.weightPoints.size >= 2) {
            WeightLineChart(state.weightPoints, Modifier.padding(top = 10.dp), enter = first)
        } else {
            BasicText(
                stringResource(R.string.weight_chart_hint),
                Modifier.padding(top = 8.dp),
                style = t.footnote.copy(color = c.label2),
            )
        }
    }
}
