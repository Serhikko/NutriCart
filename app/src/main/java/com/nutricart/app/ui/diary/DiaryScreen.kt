package com.nutricart.app.ui.diary

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.domain.model.MealSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** The food diary: one day at a time, four meal sections, add/delete entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onAddFood: (epochDay: Long, slot: MealSlot) -> Unit,
    viewModel: DiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Keep the "today" limit of the day selector correct after midnight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.diary_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            DaySelector(
                epochDay = state.epochDay,
                canGoForward = !state.isToday,
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                MealSlot.entries.forEach { slot ->
                    MealSection(
                        slot = slot,
                        entries = state.entriesBySlot[slot].orEmpty(),
                        onAdd = { onAddFood(state.epochDay, slot) },
                        onDelete = viewModel::delete,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.day_total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.kcal_value, state.totals.kcal.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DaySelector(
    epochDay: Long,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_day),
            )
        }
        Text(
            LocalDate.ofEpochDay(epochDay).format(formatter),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_day),
            )
        }
    }
}

@Composable
private fun MealSection(
    slot: MealSlot,
    entries: List<FoodLogEntryEntity>,
    onAdd: () -> Unit,
    onDelete: (FoodLogEntryEntity) -> Unit,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mealSlotLabel(slot), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val slotKcal = entries.sumOf { it.kcal }.roundToInt()
            if (slotKcal > 0) {
                Text(
                    stringResource(R.string.kcal_value, slotKcal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_food),
                )
            }
        }
    }

    if (entries.isNotEmpty()) {
        Card {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                entries.forEach { entry ->
                    EntryRow(entry = entry, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: FoodLogEntryEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            val amount = when {
                // Logged in portions: show them, grams stay the math source.
                entry.servings != null ->
                    stringResource(R.string.portions_amount, entry.servings)
                entry.grams != null ->
                    stringResource(R.string.grams_value, entry.grams.roundToInt())
                else -> null // quick-add entry (no product)
            }
            if (amount != null) {
                Text(
                    amount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.kcal_value, entry.kcal.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps each meal slot to its translated label. */
@Composable
fun mealSlotLabel(slot: MealSlot): String = stringResource(
    when (slot) {
        MealSlot.BREAKFAST -> R.string.meal_breakfast
        MealSlot.LUNCH -> R.string.meal_lunch
        MealSlot.DINNER -> R.string.meal_dinner
        MealSlot.SNACK -> R.string.meal_snack
    }
)
