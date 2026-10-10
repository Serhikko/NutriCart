package com.nutricart.app.ui.diary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.domain.logic.PartnerDigest
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.partner.partnerLabels
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
    val savingSlot by viewModel.savingSlot.collectAsState()
    val mealSaved by viewModel.mealSaved.collectAsState()
    val editingNote by viewModel.editingNote.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep the "today" limit of the day selector correct after midnight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    // One-shot confirmation after saving a meal.
    val savedMessage = stringResource(R.string.saved_meal_saved)
    LaunchedEffect(mealSaved) {
        if (mealSaved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.clearMealSaved()
        }
    }

    DiaryContent(
        state = state,
        savingSlot = savingSlot,
        editingNote = editingNote,
        snackbarHostState = snackbarHostState,
        onAddFood = onAddFood,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onEditNote = viewModel::startEditingNote,
        onDelete = viewModel::delete,
        onSaveAsMeal = viewModel::startSavingMeal,
        onSaveMeal = viewModel::saveMeal,
        onCancelSavingMeal = viewModel::cancelSavingMeal,
        onSaveNote = viewModel::saveNote,
        onCancelEditingNote = viewModel::cancelEditingNote,
    )
}

/**
 * The stateless half of [DiaryScreen]. [savingSlot] non-null shows the
 * "name this meal" dialog; [editingNote] shows the day-note editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryContent(
    state: DiaryUiState,
    savingSlot: MealSlot?,
    editingNote: Boolean,
    snackbarHostState: SnackbarHostState,
    onAddFood: (epochDay: Long, slot: MealSlot) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: (FoodLogEntryEntity) -> Unit,
    onSaveAsMeal: (MealSlot) -> Unit,
    onSaveMeal: (name: String) -> Unit,
    onCancelSavingMeal: () -> Unit,
    onSaveNote: (text: String) -> Unit,
    onCancelEditingNote: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_title)) },
                actions = { ShareDayAction(state) },
            )
        },
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
                onPrevious = onPreviousDay,
                onNext = onNextDay,
            )

            NoteRow(note = state.note, onEdit = onEditNote)

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
                        onDelete = onDelete,
                        onSaveAsMeal = { onSaveAsMeal(slot) },
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

    // "Name this meal" dialog for the section being saved.
    savingSlot?.let {
        SaveMealDialog(
            onConfirm = onSaveMeal,
            onDismiss = onCancelSavingMeal,
        )
    }

    if (editingNote) {
        NoteDialog(
            initialText = state.note ?: "",
            onConfirm = onSaveNote,
            onDismiss = onCancelEditingNote,
        )
    }
}

/**
 * Sends the displayed day as plain text through any messenger — the
 * zero-setup way to show someone what you ate. Same formatter as the
 * Telegram partner messages, so both read identically.
 */
@Composable
private fun ShareDayAction(state: DiaryUiState) {
    val context = LocalContext.current
    val hasEntries = state.entriesBySlot.values.any { it.isNotEmpty() }
    IconButton(
        onClick = {
            val date = LocalDate.ofEpochDay(state.epochDay)
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val items = state.entriesBySlot.values.flatten()
                .sortedBy { it.loggedAtEpochMillis }
                .map { PartnerDigest.Item(it.meal.ordinal, it.name, it.kcal) }
            val text = PartnerDigest.dayMessage(
                title = context.getString(R.string.partner_day_title, date),
                items = items,
                eatenKcal = state.totals.kcal,
                targetKcal = null, // the diary screen does not know the target
                labels = partnerLabels(context),
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
        enabled = hasEntries,
    ) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_day_action))
    }
}

/** The day's note under the date, or a quiet "+ note" affordance. */
@Composable
private fun NoteRow(note: String?, onEdit: () -> Unit) {
    if (note == null) {
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.day_note_add))
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.day_note_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    // The card sits ABOVE the weighted meal list — an uncapped
                    // note would squeeze the meals to zero height (review-caught).
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Free-text editor; saving blank text deletes the note. */
@Composable
private fun NoteDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.day_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                // A note is a margin scribble, not an essay — cap the length.
                onValueChange = { text = it.take(500) },
                label = { Text(stringResource(R.string.day_note_hint)) },
                minLines = 2,
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Asks for a name and hands it back — the ViewModel does the saving. */
@Composable
private fun SaveMealDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_meal_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.saved_meal_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
    onSaveAsMeal: () -> Unit,
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
            // animateContentSize: adding/removing an entry resizes smoothly.
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .animateContentSize(),
            ) {
                entries.forEach { entry ->
                    EntryRow(entry = entry, onDelete = { onDelete(entry) })
                }
                // Freeze this whole section as a one-tap combo for later.
                TextButton(
                    onClick = onSaveAsMeal,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.save_as_meal))
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
