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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.ui.common.DatePickerField
import com.nutricart.app.ui.common.ErrorCard
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.RadioOptionRow
import com.nutricart.app.ui.common.SwitchRow
import com.nutricart.app.ui.common.allergenLabel

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
