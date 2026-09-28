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
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

    if (state.showCloudNameDialog) {
        CloudNameDialog(
            initialName = state.cloudNameText,
            onConfirm = viewModel::enableCloudSync,
            onDismiss = viewModel::dismissCloudNameDialog,
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
    val context = LocalContext.current
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

        // --- AI assistant (optional) ---
        SectionSpace()
        SectionTitle(R.string.ai_section)
        AiKeySection(state = state, viewModel = viewModel)

        // --- Partner sharing (optional) ---
        SectionSpace()
        SectionTitle(R.string.partner_section)
        PartnerSection(state = state, viewModel = viewModel)

        // --- Cloud sync and the website (optional) ---
        SectionSpace()
        SectionTitle(R.string.cloud_section)
        CloudSection(state = state, viewModel = viewModel)

        // --- Health Connect ---
        // Diagnostics, not daily numbers: they used to sit at the bottom of the
        // dashboard. The whole section is hidden when Health Connect is not
        // installed, because its settings intent would resolve nowhere.
        if (state.hcAvailable) {
            SectionSpace()
            SectionTitle(R.string.hc_section)
            Text(
                stringResource(
                    R.string.last_synced,
                    state.lastSyncEpochMillis?.let { formattedDateTime(it) }
                        ?: stringResource(R.string.no_data_dash),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    )
                },
            ) {
                Text(stringResource(R.string.hc_open_settings))
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

/**
 * The user's own API key for the fridge assistant. Its own composable because
 * SettingsScreen is long enough already.
 *
 * The warning under the field is not decoration: the key really is stored in
 * plain text, and that is an accepted decision — so the screen says it, and
 * there is always a way to delete it.
 */
@Composable
private fun AiKeySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var revealed by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = state.aiKeyText,
        onValueChange = viewModel::setAiKeyText,
        label = { Text(stringResource(R.string.ai_key_label)) },
        isError = state.aiKeyText.isNotBlank() && !state.aiKeyValid,
        supportingText = {
            if (state.aiKeyText.isNotBlank() && !state.aiKeyValid) {
                Text(stringResource(R.string.ai_key_invalid))
            }
        },
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        // A password field is not only about the dots: it is what stops the
        // keyboard from learning the key and later suggesting it inside other
        // apps, and what stops autocorrect from quietly mangling it.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { revealed = !revealed }) {
            Text(
                stringResource(
                    if (revealed) R.string.ai_key_hide else R.string.ai_key_show
                )
            )
        }
        TextButton(
            onClick = viewModel::saveAiKey,
            enabled = state.aiKeyValid,
        ) { Text(stringResource(R.string.save)) }
        if (state.aiKeyStored) {
            TextButton(onClick = viewModel::deleteAiKey) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (state.aiKeySavedNotice) {
        Text(
            stringResource(R.string.ai_key_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        stringResource(R.string.ai_key_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * "Share with a partner": a Telegram bot the user creates, a partner who
 * writes /start to it, and three switches. Set-up order on screen is the
 * order things happen: token -> connect -> what to share.
 *
 * The token field copies the AI key field on purpose (masked, no autocorrect,
 * Save/Delete, plain-text warning): both are the user's own secrets, and two
 * different treatments would make one of them look less serious.
 */
@Composable
private fun PartnerSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val greeting = stringResource(R.string.partner_greeting)
    val testText = stringResource(R.string.partner_test_text)
    // Incoming nudges are notifications, so the same runtime permission the
    // meal reminders need (Android 13+).
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* denied = messages are still fetched, just not shown; nothing to do */ }

    Text(
        stringResource(R.string.partner_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = state.botTokenText,
        onValueChange = viewModel::setBotTokenText,
        label = { Text(stringResource(R.string.partner_token_label)) },
        isError = state.botTokenText.isNotBlank() && !state.botTokenValid,
        supportingText = {
            if (state.botTokenText.isNotBlank() && !state.botTokenValid) {
                Text(stringResource(R.string.partner_token_invalid))
            }
        },
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        singleLine = true,
        enabled = !state.partnerBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { revealed = !revealed }) {
            Text(stringResource(if (revealed) R.string.ai_key_hide else R.string.ai_key_show))
        }
        TextButton(
            onClick = viewModel::saveBotToken,
            enabled = state.botTokenValid && !state.partnerBusy,
        ) { Text(stringResource(R.string.save)) }
        if (state.botTokenStored) {
            TextButton(onClick = viewModel::deleteBotToken, enabled = !state.partnerBusy) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Step 2: the partner. Only reachable once the bot is verified.
    if (state.botTokenStored) {
        Spacer(modifier = Modifier.height(8.dp))
        val partnerName = state.partnerName
        if (partnerName == null) {
            state.botUsername?.let { username ->
                Text(
                    stringResource(R.string.partner_connect_hint, username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { viewModel.connectPartner(greeting) },
                enabled = !state.partnerBusy,
            ) {
                Text(stringResource(R.string.partner_connect_action))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.partner_linked_label, partnerName),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = viewModel::unlinkPartner, enabled = !state.partnerBusy) {
                    Text(stringResource(R.string.partner_unlink_action))
                }
            }
            // Step 3: what flows in each direction.
            SwitchRow(R.string.partner_share_meals, state.partnerShareMeals, viewModel::setPartnerShareMeals)
            SwitchRow(R.string.partner_notify_missed, state.partnerNotifyMissed, viewModel::setPartnerNotifyMissed)
            SwitchRow(R.string.partner_inbox, state.partnerInboxEnabled) { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.setPartnerInboxEnabled(enabled)
            }
            TextButton(
                onClick = { viewModel.sendPartnerTest(testText) },
                enabled = !state.partnerBusy,
            ) {
                Text(stringResource(R.string.partner_test_action))
            }
        }
    }

    state.partnerNotice?.let { notice ->
        val isGood = notice == PartnerNotice.BOT_SAVED || notice == PartnerNotice.LINKED ||
            notice == PartnerNotice.TEST_SENT
        Text(
            partnerNoticeText(notice, state),
            style = MaterialTheme.typography.bodySmall,
            color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    Text(
        stringResource(R.string.partner_privacy_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun partnerNoticeText(notice: PartnerNotice, state: SettingsUiState): String = when (notice) {
    PartnerNotice.BOT_SAVED -> stringResource(R.string.partner_bot_saved, state.botUsername ?: "")
    PartnerNotice.LINKED -> stringResource(R.string.partner_notice_linked, state.partnerName ?: "")
    PartnerNotice.TEST_SENT -> stringResource(R.string.partner_notice_test_sent)
    PartnerNotice.NO_MESSAGE_YET -> stringResource(R.string.partner_notice_no_message)
    PartnerNotice.BAD_TOKEN -> stringResource(R.string.partner_notice_bad_token)
    PartnerNotice.OFFLINE -> stringResource(R.string.partner_notice_offline)
    PartnerNotice.BUSY -> stringResource(R.string.partner_notice_busy)
    PartnerNotice.BLOCKED -> stringResource(R.string.partner_notice_blocked)
    PartnerNotice.FAILED -> stringResource(R.string.partner_notice_failed)
}

/**
 * "Cloud sync & website": one switch, a name, a pairing code, the people who
 * can read the account. Everything the website needs from the phone is set
 * up here; the schema and policies are in supabase/.
 */
@Composable
private fun CloudSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (!state.cloudConfigured) {
        // A build without keys (see supabase/README.md): say so, offer nothing.
        Text(
            stringResource(R.string.cloud_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        stringResource(R.string.cloud_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    SwitchRow(R.string.cloud_switch, state.cloudEnabled, viewModel::toggleCloudSync)

    if (state.cloudEnabled) {
        // Name
        OutlinedTextField(
            value = state.cloudNameText,
            onValueChange = viewModel::setCloudNameText,
            label = { Text(stringResource(R.string.cloud_name_label)) },
            singleLine = true,
            isError = state.cloudNameText.isNotEmpty() && !state.cloudNameValid,
            enabled = !state.cloudBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.cloudNameText.trim() != state.cloudNameStored.orEmpty()) {
            TextButton(
                onClick = viewModel::saveCloudName,
                enabled = state.cloudNameValid && !state.cloudBusy,
            ) { Text(stringResource(R.string.save)) }
        }

        // Account email: the way into this account from the website and from a new phone.
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cloud_email_title), style = MaterialTheme.typography.titleMedium)
        val account = state.cloudAccount
        if (account?.email != null) {
            Text(
                stringResource(R.string.cloud_email_linked, account.email),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                stringResource(R.string.cloud_email_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.cloudEmailText,
                onValueChange = viewModel::setCloudEmailText,
                label = { Text(stringResource(R.string.cloud_email_label)) },
                singleLine = true,
                isError = state.cloudEmailText.isNotEmpty() && !state.cloudEmailValid,
                enabled = !state.cloudBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            account?.pendingEmail?.let { pending ->
                Text(
                    stringResource(R.string.cloud_email_pending, pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = viewModel::linkCloudEmail,
                enabled = state.cloudEmailValid && !state.cloudBusy,
            ) { Text(stringResource(R.string.cloud_email_link)) }
        }

        // Pairing code
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cloud_code_title), style = MaterialTheme.typography.titleMedium)
        val code = state.cloudPairingCode
        val now = System.currentTimeMillis()
        if (code != null && code.second > now) {
            Text(
                code.first,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.cloud_code_hint, ((code.second - now) / 60_000L + 1).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (code != null) {
            Text(
                stringResource(R.string.cloud_code_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = viewModel::newPairingCode, enabled = !state.cloudBusy) {
            Text(stringResource(R.string.cloud_code_new))
        }

        // Partners
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cloud_partners_title), style = MaterialTheme.typography.titleMedium)
        if (state.cloudPartners.isEmpty()) {
            Text(
                stringResource(R.string.cloud_partners_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.cloudPartners.forEach { partner ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(partner.name, style = MaterialTheme.typography.bodyLarge)
                    TextButton(
                        onClick = { viewModel.unlinkCloudPartner(partner.linkId) },
                        enabled = !state.cloudBusy,
                    ) {
                        Text(stringResource(R.string.cloud_unlink), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Status
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.cloud_last_sync,
                state.cloudLastSyncEpochMillis?.let { formattedDateTime(it) }
                    ?: stringResource(R.string.no_data_dash),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.cloudPendingCount > 0) {
            Text(
                stringResource(R.string.cloud_pending, state.cloudPendingCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.cloudLastError?.let { error ->
            Text(
                cloudErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    state.cloudNotice?.let { notice ->
        val isGood = notice == CloudNotice.ENABLED || notice == CloudNotice.NAME_SAVED ||
            notice == CloudNotice.CODE_READY || notice == CloudNotice.UNLINKED || notice == CloudNotice.EMAIL_SENT
        Text(
            stringResource(
                when (notice) {
                    CloudNotice.ENABLED -> R.string.cloud_notice_enabled
                    CloudNotice.NAME_SAVED -> R.string.cloud_notice_name_saved
                    CloudNotice.CODE_READY -> R.string.cloud_notice_code_ready
                    CloudNotice.UNLINKED -> R.string.cloud_notice_unlinked
                    CloudNotice.EMAIL_SENT -> R.string.cloud_notice_email_sent
                    CloudNotice.NOT_CONFIGURED -> R.string.cloud_not_configured
                    CloudNotice.OFFLINE -> R.string.cloud_notice_offline
                    CloudNotice.AUTH -> R.string.cloud_notice_auth
                    CloudNotice.FAILED -> R.string.cloud_notice_failed
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    Text(
        stringResource(R.string.cloud_privacy_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The error codes CloudSyncWorker records, in the user's words. */
@Composable
private fun cloudErrorText(code: String): String = stringResource(
    when (code) {
        "offline" -> R.string.cloud_error_offline
        "auth" -> R.string.cloud_error_auth
        "server" -> R.string.cloud_error_server
        "rejected" -> R.string.cloud_error_rejected
        else -> R.string.cloud_error_failed
    }
)

/** Asked once, when the switch is turned on without a stored name. */
@Composable
private fun CloudNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val valid = name.trim().length in 1..40
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_name_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.cloud_name_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.cloud_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) {
                Text(stringResource(R.string.cloud_switch_on))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A timestamp in the phone's own short date + time format. */
@Composable
private fun formattedDateTime(epochMillis: Long): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
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
