package com.nutricart.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.ProfileOptions
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.ui.common.allergenLabel
import com.nutricart.app.ui.common.cookingSessionsLabel
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberTextField
import com.nutricart.app.ui.ember.InsetGroupScope
import com.nutricart.app.ui.ember.OptionRow
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl

// The profile questions as Ember controls. Onboarding asks them one step at a time and Settings
// shows them all as one form; both draw them from here, so the two screens can never drift apart.

/** The five activity levels with their title and description, in display order. */
private val ActivityOptions = listOf(
    Triple(ActivityLevel.SEDENTARY, R.string.activity_sedentary, R.string.activity_sedentary_desc),
    Triple(ActivityLevel.LIGHT, R.string.activity_light, R.string.activity_light_desc),
    Triple(ActivityLevel.MODERATE, R.string.activity_moderate, R.string.activity_moderate_desc),
    Triple(ActivityLevel.ACTIVE, R.string.activity_active, R.string.activity_active_desc),
    Triple(ActivityLevel.VERY_ACTIVE, R.string.activity_very_active, R.string.activity_very_active_desc),
)

private val GoalOptions = listOf(
    Goal.LOSE to R.string.goal_lose,
    Goal.MAINTAIN to R.string.goal_maintain,
    Goal.GAIN to R.string.goal_gain,
)

/** Option rows carry their mark on the leading side, so their hairlines start after it. */
private val OptionDivider = 54.dp

/** At this text size two fields side by side would squeeze their labels: they stack instead. */
internal const val StackFontScale = 1.5f

/**
 * The small label over a control inside a card (the web's `.f-l`): 13 sp SemiBold, label 2. Only
 * drawn: the control under it carries the same words as its own name, so TalkBack reads them once.
 */
@Composable
internal fun BlockLabel(text: String, modifier: Modifier = Modifier) {
    val c = Ember.colors
    BasicText(
        text,
        style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = { c.label2 },
        modifier = modifier
            .clearAndSetSemantics {}
            .padding(bottom = 4.dp),
    )
}

/** Male | Female as a segmented control; nothing is chosen (no thumb) until [sex] is set. */
@Composable
internal fun SexControl(sex: Sex?, onSelect: (Sex) -> Unit, groupLabel: String, modifier: Modifier = Modifier) {
    SegmentedControl(
        options = listOf(stringResource(R.string.sex_male), stringResource(R.string.sex_female)),
        selectedIndex = when (sex) {
            Sex.MALE -> 0
            Sex.FEMALE -> 1
            null -> -1
        },
        onSelect = { onSelect(if (it == 0) Sex.MALE else Sex.FEMALE) },
        modifier = modifier,
        groupLabel = groupLabel,
        role = SegmentRole.Radio,
    )
}

/**
 * Height and weight side by side, each with its unit in the well and its own error line; they stack
 * at large text sizes. The weight takes a comma as well as a point (the ViewModel parses both).
 */
@Composable
internal fun MeasurementFields(
    heightText: String,
    weightText: String,
    heightInvalid: Boolean,
    weightInvalid: Boolean,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val height: @Composable (Modifier) -> Unit = { m ->
        EmberTextField(
            value = heightText,
            onValueChange = onHeightChange,
            label = stringResource(R.string.height_label),
            modifier = m,
            suffix = stringResource(R.string.form_unit_cm),
            isError = heightInvalid,
            errorText = stringResource(R.string.invalid_height),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
    }
    val weight: @Composable (Modifier) -> Unit = { m ->
        EmberTextField(
            value = weightText,
            onValueChange = onWeightChange,
            label = stringResource(R.string.weight_label),
            modifier = m,
            suffix = stringResource(R.string.form_unit_kg),
            isError = weightInvalid,
            errorText = stringResource(R.string.invalid_weight),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
    }
    if (LocalDensity.current.fontScale >= StackFontScale) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            height(Modifier.fillMaxWidth())
            weight(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            height(Modifier.weight(1f))
            weight(Modifier.weight(1f))
        }
    }
}

/** The five activity levels as option rows of an inset group. */
@Composable
internal fun InsetGroupScope.ActivityRows(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    ActivityOptions.forEach { (level, titleRes, descRes) ->
        row(dividerStart = OptionDivider) {
            OptionRow(
                title = stringResource(titleRes),
                selected = selected == level,
                onClick = { onSelect(level) },
                detail = stringResource(descRes),
            )
        }
    }
}

/** Lose / maintain / gain as option rows of an inset group. */
@Composable
internal fun InsetGroupScope.GoalRows(selected: Goal?, onSelect: (Goal) -> Unit) {
    GoalOptions.forEach { (goal, titleRes) ->
        row(dividerStart = OptionDivider) {
            OptionRow(title = stringResource(titleRes), selected = selected == goal, onClick = { onSelect(goal) })
        }
    }
}

/**
 * The pace of a lose or gain goal, for the last row of the goal group. The labels are the numbers
 * exactly as the chips always printed them ("0.25" … "1.0").
 */
@Composable
internal fun RateControl(rate: Double, onSelect: (Double) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.rate_label)
    Column(modifier.fillMaxWidth()) {
        BlockLabel(label)
        SegmentedControl(
            options = ProfileOptions.RATE_OPTIONS.map { it.toString() },
            selectedIndex = ProfileOptions.RATE_OPTIONS.indexOf(rate),
            onSelect = { onSelect(ProfileOptions.RATE_OPTIONS[it]) },
            groupLabel = label,
        )
    }
}

/** The eight allergens as chips that wrap; any number can be on. */
@Composable
internal fun AllergyChips(selected: Set<Allergen>, onToggle: (Allergen) -> Unit, modifier: Modifier = Modifier) {
    val all = Allergen.entries
    ChipGroup(
        options = all.map { allergenLabel(it) },
        selected = all.indices.filter { all[it] in selected }.toSet(),
        onToggle = { onToggle(all[it]) },
        modifier = modifier,
        multiSelect = true,
        groupLabel = stringResource(R.string.allergies_label),
    )
}

/** Snacks per day, 0 / 1 / 2. */
@Composable
internal fun SnacksControl(count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = ProfileOptions.SNACK_OPTIONS
    SegmentedControl(
        options = options.map { it.toString() },
        selectedIndex = options.indexOf(count),
        onSelect = { onSelect(options[it]) },
        modifier = modifier,
        groupLabel = stringResource(R.string.snacks_label),
    )
}

/** How often the user cooks: "2–3 times" / "Every other day" / "Every day" (labels may take two lines). */
@Composable
internal fun CookingControl(sessions: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = ProfileOptions.COOKING_OPTIONS
    SegmentedControl(
        options = options.map { cookingSessionsLabel(it) },
        selectedIndex = options.indexOf(sessions),
        onSelect = { onSelect(options[it]) },
        modifier = modifier,
        groupLabel = stringResource(R.string.cooking_label),
    )
}
