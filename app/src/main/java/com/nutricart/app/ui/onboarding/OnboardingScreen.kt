package com.nutricart.app.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nutricart.app.ui.common.RadioOptionRow
import com.nutricart.app.ui.common.SwitchRow
import com.nutricart.app.ui.common.allergenLabel
import java.time.LocalDate

/**
 * Seven-step questionnaire. One Composable per step below; the ViewModel holds
 * all state, this file only draws it and forwards clicks. When finish() saves
 * the profile, AppRoot notices the "onboarding completed" flag and switches to
 * the main app — this screen never navigates by itself.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (state.step + 1) / OnboardingUiState.STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
            ) {
                when (state.step) {
                    OnboardingUiState.STEP_SEX -> SexStep(state, viewModel::selectSex)
                    OnboardingUiState.STEP_BIRTH -> BirthDateStep(state, viewModel::selectBirthDate)
                    OnboardingUiState.STEP_BODY -> BodyStep(state, viewModel::setHeightText, viewModel::setWeightText)
                    OnboardingUiState.STEP_ACTIVITY -> ActivityStep(state, viewModel::selectActivityLevel)
                    OnboardingUiState.STEP_GOAL -> GoalStep(state, viewModel::selectGoal, viewModel::selectRate)
                    OnboardingUiState.STEP_DIET -> DietStep(
                        state,
                        viewModel::toggleVegetarian,
                        viewModel::toggleNoPork,
                        viewModel::toggleAllergen,
                        viewModel::selectSnacksPerDay,
                    )
                    OnboardingUiState.STEP_SUMMARY -> SummaryStep(state)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                if (state.step > OnboardingUiState.STEP_SEX) {
                    OutlinedButton(onClick = viewModel::back) {
                        Text(stringResource(R.string.back))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (state.step < OnboardingUiState.STEP_SUMMARY) {
                    Button(onClick = viewModel::next, enabled = state.canGoNext) {
                        Text(stringResource(R.string.next))
                    }
                } else {
                    Button(onClick = viewModel::finish, enabled = !state.finished) {
                        Text(stringResource(R.string.start))
                    }
                }
            }
        }
    }
}

// ---------- Steps ----------

@Composable
private fun SexStep(state: OnboardingUiState, onSelect: (Sex) -> Unit) {
    StepTitle(R.string.onboarding_title_sex)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = state.sex == Sex.MALE,
            onClick = { onSelect(Sex.MALE) },
            label = { Text(stringResource(R.string.sex_male)) },
        )
        FilterChip(
            selected = state.sex == Sex.FEMALE,
            onClick = { onSelect(Sex.FEMALE) },
            label = { Text(stringResource(R.string.sex_female)) },
        )
    }
}

@Composable
private fun BirthDateStep(state: OnboardingUiState, onDatePicked: (LocalDate) -> Unit) {
    StepTitle(R.string.onboarding_title_birth)
    Text(stringResource(R.string.birth_hint), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(16.dp))

    DatePickerField(date = state.birthDate, onDatePicked = onDatePicked)

    if (state.underageBlocked) {
        Spacer(modifier = Modifier.height(16.dp))
        ErrorCard(stringResource(R.string.underage_error))
    }
}

@Composable
private fun BodyStep(
    state: OnboardingUiState,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
) {
    StepTitle(R.string.onboarding_title_body)

    val heightInvalid = state.heightCmText.isNotEmpty() && state.heightCm == null
    OutlinedTextField(
        value = state.heightCmText,
        onValueChange = onHeightChange,
        label = { Text(stringResource(R.string.height_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = heightInvalid,
        supportingText = { if (heightInvalid) Text(stringResource(R.string.invalid_height)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))

    val weightInvalid = state.weightKgText.isNotEmpty() && state.weightKg == null
    OutlinedTextField(
        value = state.weightKgText,
        onValueChange = onWeightChange,
        label = { Text(stringResource(R.string.weight_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = weightInvalid,
        supportingText = { if (weightInvalid) Text(stringResource(R.string.invalid_weight)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActivityStep(state: OnboardingUiState, onSelect: (ActivityLevel) -> Unit) {
    StepTitle(R.string.onboarding_title_activity)
    val options = listOf(
        Triple(ActivityLevel.SEDENTARY, R.string.activity_sedentary, R.string.activity_sedentary_desc),
        Triple(ActivityLevel.LIGHT, R.string.activity_light, R.string.activity_light_desc),
        Triple(ActivityLevel.MODERATE, R.string.activity_moderate, R.string.activity_moderate_desc),
        Triple(ActivityLevel.ACTIVE, R.string.activity_active, R.string.activity_active_desc),
        Triple(ActivityLevel.VERY_ACTIVE, R.string.activity_very_active, R.string.activity_very_active_desc),
    )
    options.forEach { (level, titleRes, descRes) ->
        RadioOptionRow(
            titleRes = titleRes,
            descRes = descRes,
            selected = state.activityLevel == level,
            onClick = { onSelect(level) },
        )
    }
}

@Composable
private fun GoalStep(
    state: OnboardingUiState,
    onSelectGoal: (Goal) -> Unit,
    onSelectRate: (Double) -> Unit,
) {
    StepTitle(R.string.onboarding_title_goal)
    val options = listOf(
        Goal.LOSE to R.string.goal_lose,
        Goal.MAINTAIN to R.string.goal_maintain,
        Goal.GAIN to R.string.goal_gain,
    )
    options.forEach { (goal, titleRes) ->
        RadioOptionRow(
            titleRes = titleRes,
            descRes = null,
            selected = state.goal == goal,
            onClick = { onSelectGoal(goal) },
        )
    }

    // The pace only makes sense when the goal is to lose or gain.
    if (state.goal != null && state.goal != Goal.MAINTAIN) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.rate_label), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileOptions.RATE_OPTIONS.forEach { rate ->
                FilterChip(
                    selected = state.targetKgPerWeek == rate,
                    onClick = { onSelectRate(rate) },
                    label = { Text(rate.toString()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietStep(
    state: OnboardingUiState,
    onVegetarian: (Boolean) -> Unit,
    onNoPork: (Boolean) -> Unit,
    onToggleAllergen: (Allergen) -> Unit,
    onSnacks: (Int) -> Unit,
) {
    StepTitle(R.string.onboarding_title_diet)

    SwitchRow(R.string.vegetarian_label, state.isVegetarian, onVegetarian)
    SwitchRow(R.string.no_pork_label, state.noPork, onNoPork)

    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.allergies_label), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Allergen.entries.forEach { allergen ->
            FilterChip(
                selected = allergen in state.allergies,
                onClick = { onToggleAllergen(allergen) },
                label = { Text(allergenLabel(allergen)) },
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.snacks_label), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileOptions.SNACK_OPTIONS.forEach { count ->
            FilterChip(
                selected = state.snacksPerDay == count,
                onClick = { onSnacks(count) },
                label = { Text(count.toString()) },
            )
        }
    }
}

@Composable
private fun SummaryStep(state: OnboardingUiState) {
    val preview = state.preview ?: return

    StepTitle(R.string.onboarding_title_summary)
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryRow(
                label = stringResource(R.string.summary_bmr),
                value = stringResource(R.string.kcal_value, preview.bmr),
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.summary_kcal_target),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.kcal_value, preview.targets.kcal),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (preview.raisedToFloor) {
                Text(
                    stringResource(R.string.safety_floor_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            HorizontalDivider()
            SummaryRow(
                label = stringResource(R.string.summary_protein),
                value = stringResource(R.string.grams_value, preview.targets.proteinG),
            )
            SummaryRow(
                label = stringResource(R.string.summary_fat),
                value = stringResource(R.string.grams_value, preview.targets.fatG),
            )
            SummaryRow(
                label = stringResource(R.string.summary_carbs),
                value = stringResource(R.string.grams_value, preview.targets.carbsG),
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        stringResource(R.string.summary_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- Small local pieces ----------

@Composable
private fun StepTitle(titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
