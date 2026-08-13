package com.nutricart.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.diary.mealSlotLabel
import com.nutricart.app.ui.common.DatePickerField
import com.nutricart.app.ui.common.ErrorCard
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.RadioOptionRow
import com.nutricart.app.ui.common.SwitchRow
import com.nutricart.app.ui.common.allergenLabel
import com.nutricart.app.ui.common.cookingSessionsLabel
import com.nutricart.app.ui.common.workoutTypeLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * One scrollable form with the same fields as onboarding, pre-filled from the
 * saved profile. Saving updates the profile and logs today's weight; the
 * dashboard recomputes automatically because it observes the database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Leave the screen once saving is done.
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
        } else {
            SettingsForm(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    if (state.showResetDialog) {
        ResetConfirmDialog(
            onConfirm = viewModel::confirmReset,
            onDismiss = { viewModel.setShowResetDialog(false) },
        )
    }

    if (state.showRecurringDialog) {
        RecurringDialog(
            onConfirm = viewModel::addRecurring,
            onDismiss = { viewModel.setShowRecurringDialog(false) },
        )
    }

    state.editingReminderSlot?.let { slot ->
        val minutes = state.reminders.firstOrNull { it.slot == slot }?.minutesOfDay ?: 0
        ReminderTimeDialog(
            initialMinutes = minutes,
            onConfirm = viewModel::setReminderTime,
            onDismiss = viewModel::cancelEditingReminder,
        )
    }
}

/** One reminder: meal name, its time (tap to change) and the on/off switch. */
@Composable
private fun ReminderRow(
    reminder: ReminderUi,
    onToggle: (Boolean) -> Unit,
    onEditTime: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            mealSlotLabel(reminder.slot),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEditTime) {
            Text("%02d:%02d".format(reminder.minutesOfDay / 60, reminder.minutesOfDay % 60))
        }
        Switch(checked = reminder.enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_title)) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour * 60 + timeState.minute) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One standing rule: "Treadmill walk · 30 min · Mon Tue Wed" + delete. */
@Composable
private fun RecurringRow(rule: RecurringWorkoutEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(workoutTypeLabel(rule.type)),
                style = MaterialTheme.typography.bodyLarge,
            )
            val amount = rule.minutes?.let { stringResource(R.string.minutes_value, it) }
                ?: rule.reps?.let { stringResource(R.string.workout_reps_value, it) }
            val days = rule.days.joinToString(" ") {
                it.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
            }
            Text(
                listOfNotNull(amount, days).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.recurring_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pick a workout, an amount and the weekdays it repeats on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(
    onConfirm: (WorkoutType, Int, Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(WorkoutType.TREADMILL_WALK) }
    var amountText by rememberSaveable { mutableStateOf("30") }
    var days by rememberSaveable { mutableStateOf(setOf<DayOfWeek>()) }

    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    val valid = amount != null && days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutType.entries.forEach { type ->
                        FilterChip(
                            selected = type == selected,
                            onClick = {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.recurring_days_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = {
                                days = if (day in days) days - day else days + day
                            },
                            label = {
                                Text(
                                    day.getDisplayName(
                                        TextStyle.SHORT_STANDALONE, Locale.getDefault()
                                    )
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { amount?.let { onConfirm(selected, it, days) } },
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsForm(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // --- About you ---
        SectionTitle(R.string.onboarding_title_sex)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.sex == Sex.MALE,
                onClick = { viewModel.selectSex(Sex.MALE) },
                label = { Text(stringResource(R.string.sex_male)) },
            )
            FilterChip(
                selected = state.sex == Sex.FEMALE,
                onClick = { viewModel.selectSex(Sex.FEMALE) },
                label = { Text(stringResource(R.string.sex_female)) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        DatePickerField(date = state.birthDate, onDatePicked = viewModel::selectBirthDate)
        if (state.underageBlocked) {
            Spacer(modifier = Modifier.height(12.dp))
            ErrorCard(stringResource(R.string.underage_error))
        }

        // --- Body ---
        SectionSpace()
        SectionTitle(R.string.onboarding_title_body)
        val heightInvalid = state.heightCmText.isNotEmpty() && state.heightCm == null
        OutlinedTextField(
            value = state.heightCmText,
            onValueChange = viewModel::setHeightText,
            label = { Text(stringResource(R.string.height_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = heightInvalid,
            supportingText = { if (heightInvalid) Text(stringResource(R.string.invalid_height)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        val weightInvalid = state.weightKgText.isNotEmpty() && state.weightKg == null
        OutlinedTextField(
            value = state.weightKgText,
            onValueChange = viewModel::setWeightText,
            label = { Text(stringResource(R.string.weight_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = weightInvalid,
            supportingText = { if (weightInvalid) Text(stringResource(R.string.invalid_weight)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.weight_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Activity ---
        SectionSpace()
        SectionTitle(R.string.onboarding_title_activity)
        val activityOptions = listOf(
            Triple(ActivityLevel.SEDENTARY, R.string.activity_sedentary, R.string.activity_sedentary_desc),
            Triple(ActivityLevel.LIGHT, R.string.activity_light, R.string.activity_light_desc),
            Triple(ActivityLevel.MODERATE, R.string.activity_moderate, R.string.activity_moderate_desc),
            Triple(ActivityLevel.ACTIVE, R.string.activity_active, R.string.activity_active_desc),
            Triple(ActivityLevel.VERY_ACTIVE, R.string.activity_very_active, R.string.activity_very_active_desc),
        )
        activityOptions.forEach { (level, titleRes, descRes) ->
            RadioOptionRow(
                titleRes = titleRes,
                descRes = descRes,
                selected = state.activityLevel == level,
                onClick = { viewModel.selectActivityLevel(level) },
            )
        }

        // --- Goal ---
        SectionSpace()
        SectionTitle(R.string.onboarding_title_goal)
        val goalOptions = listOf(
            Goal.LOSE to R.string.goal_lose,
            Goal.MAINTAIN to R.string.goal_maintain,
            Goal.GAIN to R.string.goal_gain,
        )
        goalOptions.forEach { (goal, titleRes) ->
            RadioOptionRow(
                titleRes = titleRes,
                descRes = null,
                selected = state.goal == goal,
                onClick = { viewModel.selectGoal(goal) },
            )
        }
        if (state.goal != Goal.MAINTAIN) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.rate_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileOptions.RATE_OPTIONS.forEach { rate ->
                    FilterChip(
                        selected = state.targetKgPerWeek == rate,
                        onClick = { viewModel.selectRate(rate) },
                        label = { Text(rate.toString()) },
                    )
                }
            }
        }

        // --- Daily targets (manual override) ---
        SectionSpace()
        SectionTitle(R.string.targets_section)
        SwitchRow(R.string.targets_manual_switch, state.manualTargets, viewModel::toggleManualTargets)
        if (state.manualTargets) {
            Text(
                stringResource(R.string.targets_manual_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TargetField(
                value = state.customKcalText,
                onChange = viewModel::setCustomKcalText,
                labelRes = R.string.target_kcal_label,
                invalid = state.customKcalText.isNotEmpty() && state.customKcal == null,
            )
            TargetField(
                value = state.customProteinText,
                onChange = viewModel::setCustomProteinText,
                labelRes = R.string.target_protein_label,
                invalid = state.customProteinText.isNotEmpty() && state.customProtein == null,
            )
            TargetField(
                value = state.customFatText,
                onChange = viewModel::setCustomFatText,
                labelRes = R.string.target_fat_label,
                invalid = state.customFatText.isNotEmpty() && state.customFat == null,
            )
            TargetField(
                value = state.customCarbsText,
                onChange = viewModel::setCustomCarbsText,
                labelRes = R.string.target_carbs_label,
                invalid = state.customCarbsText.isNotEmpty() && state.customCarbs == null,
            )
        }

        // --- Regular activities ---
        SectionSpace()
        SectionTitle(R.string.recurring_section)
        if (state.recurring.isEmpty()) {
            Text(
                stringResource(R.string.recurring_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.recurring.forEach { rule ->
                RecurringRow(rule = rule, onDelete = { viewModel.deleteRecurring(rule) })
            }
        }
        TextButton(onClick = { viewModel.setShowRecurringDialog(true) }) {
            Text(stringResource(R.string.recurring_add))
        }

        // --- Meal reminders ---
        SectionSpace()
        SectionTitle(R.string.reminders_section)
        // Android 13+ needs the runtime permission before notify() works.
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* denied = reminders stay scheduled but silent; nothing to do */ }
        state.reminders.forEach { reminder ->
            ReminderRow(
                reminder = reminder,
                onToggle = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.toggleReminder(reminder.slot, enabled)
                },
                onEditTime = { viewModel.startEditingReminder(reminder.slot) },
            )
        }
        Text(
            stringResource(R.string.reminders_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Diet ---
        SectionSpace()
        SectionTitle(R.string.onboarding_title_diet)
        SwitchRow(R.string.vegetarian_label, state.isVegetarian, viewModel::toggleVegetarian)
        SwitchRow(R.string.no_pork_label, state.noPork, viewModel::toggleNoPork)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.allergies_label), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Allergen.entries.forEach { allergen ->
                FilterChip(
                    selected = allergen in state.allergies,
                    onClick = { viewModel.toggleAllergen(allergen) },
                    label = { Text(allergenLabel(allergen)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.snacks_label), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileOptions.SNACK_OPTIONS.forEach { count ->
                FilterChip(
                    selected = state.snacksPerDay == count,
                    onClick = { viewModel.selectSnacksPerDay(count) },
                    label = { Text(count.toString()) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cooking_label), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileOptions.COOKING_OPTIONS.forEach { sessions ->
                FilterChip(
                    selected = state.cookingSessionsPerWeek == sessions,
                    onClick = { viewModel.selectCookingSessions(sessions) },
                    label = { Text(cookingSessionsLabel(sessions)) },
                )
            }
        }

        // --- Save ---
        SectionSpace()
        Button(
            onClick = viewModel::save,
            enabled = state.canSave && !state.saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }

        // --- Reset ---
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = { viewModel.setShowResetDialog(true) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reset_button))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_confirm_title)) },
        text = { Text(stringResource(R.string.reset_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SectionSpace() {
    Spacer(modifier = Modifier.height(28.dp))
}

/** One manual-target number field with the shared error presentation. */
@Composable
private fun TargetField(
    value: String,
    onChange: (String) -> Unit,
    labelRes: Int,
    invalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = invalid,
        supportingText = { if (invalid) Text(stringResource(R.string.target_invalid)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
}
