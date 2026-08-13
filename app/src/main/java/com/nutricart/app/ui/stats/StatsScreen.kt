package com.nutricart.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.data.local.dao.FoodContribution
import com.nutricart.app.domain.logic.AdherenceCalculator
import com.nutricart.app.domain.logic.DietInsights
import com.nutricart.app.ui.common.BarChart
import com.nutricart.app.ui.common.BarPoint
import com.nutricart.app.ui.common.LoadingBox
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Statistics tab: kcal-by-day chart, honest averages, days on plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    // Fresh numbers every time the tab comes back to the foreground.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) },
    ) { innerPadding ->
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeChip(R.string.stats_range_week, state.range == StatsRange.WEEK) {
                    viewModel.selectRange(StatsRange.WEEK)
                }
                RangeChip(R.string.stats_range_month, state.range == StatsRange.MONTH) {
                    viewModel.selectRange(StatsRange.MONTH)
                }
                RangeChip(R.string.stats_range_90, state.range == StatsRange.NINETY) {
                    viewModel.selectRange(StatsRange.NINETY)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            KcalChartCard(state)

            Spacer(modifier = Modifier.height(16.dp))
            CalendarCard(
                state = state,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
            )

            Spacer(modifier = Modifier.height(16.dp))
            AveragesCard(state)

            if (state.loggedDays > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                AnalysisCard(state)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Which nutrient the top-sources list currently shows. */
private enum class SourceTab { PROTEIN, FAT, CARBS, SUGARS }

/** "Diet analysis": top-5 foods per nutrient + gaps/excesses of the period. */
@Composable
private fun AnalysisCard(state: StatsUiState) {
    val sources = state.topSources ?: return
    var tab by rememberSaveable { mutableStateOf(SourceTab.PROTEIN) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_analysis_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Gaps and excesses first — the "so what" of the whole card.
            if (state.insights.isEmpty()) {
                Text(
                    stringResource(R.string.insight_all_good),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                state.insights.forEach { insight ->
                    Text(
                        insightText(insight),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.stats_top_sources_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceChip(R.string.summary_protein, tab == SourceTab.PROTEIN) { tab = SourceTab.PROTEIN }
                SourceChip(R.string.summary_fat, tab == SourceTab.FAT) { tab = SourceTab.FAT }
                SourceChip(R.string.summary_carbs, tab == SourceTab.CARBS) { tab = SourceTab.CARBS }
                SourceChip(R.string.nutrient_sugars, tab == SourceTab.SUGARS) { tab = SourceTab.SUGARS }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val list = when (tab) {
                SourceTab.PROTEIN -> sources.protein
                SourceTab.FAT -> sources.fat
                SourceTab.CARBS -> sources.carbs
                SourceTab.SUGARS -> sources.sugars
            }
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                list.forEachIndexed { index, item -> SourceRow(index + 1, item) }
            }
        }
    }
}

@Composable
private fun SourceChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SourceRow(rank: Int, item: FoodContribution) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "$rank. ${item.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            stringResource(R.string.nutrient_grams_value, item.amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun insightText(insight: DietInsights.Insight): String = stringResource(
    when (insight.kind) {
        DietInsights.Kind.PROTEIN_LOW -> R.string.insight_protein_low
        DietInsights.Kind.FIBER_LOW -> R.string.insight_fiber_low
        DietInsights.Kind.SUGAR_HIGH -> R.string.insight_sugar_high
        DietInsights.Kind.SALT_HIGH -> R.string.insight_salt_high
        DietInsights.Kind.SAT_FAT_HIGH -> R.string.insight_sat_fat_high
    },
    insight.avg,
    insight.reference,
)

/** Month grid: green = on plan, red = over, plain = nothing logged. */
@Composable
private fun CalendarCard(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val month = state.calendarMonth
    val today = LocalDate.now().toEpochDay()
    val monthLabel = remember(month) {
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
            .replaceFirstChar { it.uppercase() } + " " + month.year
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_calendar_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.stats_prev_month),
                    )
                }
                Text(monthLabel, style = MaterialTheme.typography.titleSmall)
                IconButton(
                    onClick = onNextMonth,
                    enabled = month < YearMonth.now(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.stats_next_month),
                    )
                }
            }

            // Mon..Sun header, localized narrow names ("П", "В", ...).
            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Leading blanks align day 1 under its weekday (Monday-first).
            val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
            val cells: List<Int?> =
                List(leadingBlanks) { null } + (1..month.lengthOfMonth()).toList()
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayOfMonth ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayOfMonth != null) {
                                DayCell(
                                    epochDay = month.atDay(dayOfMonth).toEpochDay(),
                                    dayOfMonth = dayOfMonth,
                                    state = state.calendarStates,
                                    today = today,
                                )
                            }
                        }
                    }
                    // Pad the last week so its cells keep the same width.
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    epochDay: Long,
    dayOfMonth: Int,
    state: Map<Long, AdherenceCalculator.DayState>,
    today: Long,
) {
    val verdict = state[epochDay]
    val background = when (verdict) {
        AdherenceCalculator.DayState.GOOD -> MaterialTheme.colorScheme.primary
        AdherenceCalculator.DayState.OVER -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val textColor = when (verdict) {
        AdherenceCalculator.DayState.GOOD -> MaterialTheme.colorScheme.onPrimary
        AdherenceCalculator.DayState.OVER -> MaterialTheme.colorScheme.onError
        // Future days fade out; past empty days stay readable.
        else -> if (epochDay > today) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val todayRing = if (epochDay == today) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .then(todayRing),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun RangeChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun KcalChartCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_chart_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (state.loggedDays == 0) {
                Text(
                    stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BarChart(
                    points = state.bars.map {
                        BarPoint(it.eatenKcal.toFloat(), it.state)
                    },
                    targetLine = state.avgTargetKcal.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_avg_target_note, state.avgTargetKcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AveragesCard(state: StatsUiState) {
    val targets = state.targets ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.stats_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SummaryRow(
                labelRes = R.string.stats_avg_kcal,
                value = stringResource(R.string.kcal_value, state.avgEatenKcal),
            )
            SummaryRow(
                labelRes = R.string.summary_protein,
                value = stringResource(R.string.macro_pair, state.avgProteinG, targets.proteinG),
            )
            SummaryRow(
                labelRes = R.string.summary_fat,
                value = stringResource(R.string.macro_pair, state.avgFatG, targets.fatG),
            )
            SummaryRow(
                labelRes = R.string.summary_carbs,
                value = stringResource(R.string.macro_pair, state.avgCarbsG, targets.carbsG),
            )
            SummaryRow(
                labelRes = R.string.water_label,
                value = state.avgWaterMl.toString() + " " + stringResource(R.string.ml_unit),
            )
            HorizontalDivider()
            SummaryRow(
                labelRes = R.string.stats_days_on_plan,
                value = stringResource(R.string.stats_days_value, state.goodDays, state.loggedDays),
                highlight = state.loggedDays > 0 &&
                    state.goodDays.toDouble() / state.loggedDays.coerceAtLeast(1) >= 0.5,
            )
        }
    }
}

@Composable
private fun SummaryRow(labelRes: Int, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
