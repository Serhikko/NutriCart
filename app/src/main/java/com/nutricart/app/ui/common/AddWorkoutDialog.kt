package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutricart.app.R
import com.nutricart.app.domain.logic.WorkoutMath
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.ember.rememberSheetCloser
import kotlin.math.roundToInt

/**
 * Type picker (chips) + one amount. The amount means minutes or repetitions
 * depending on the chosen type, and the kcal preview uses the same WorkoutMath
 * the repository will store — no surprises after saving.
 *
 * A sheet: the 13 types as chips that wrap (never scroll), the amount as a
 * − [ 30 min ] + stepper you can also type into, the estimate in big Ember
 * digits that hand off as the amount changes, the watch hint and Add. The
 * close button is the old Cancel. Adding slides the sheet away first, then
 * writes, so the new row appears on a settled screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddWorkoutDialog(
    weightKg: Double,
    onConfirm: (WorkoutType, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: the picked type and amount survive screen rotation.
    var selected by rememberSaveable { mutableStateOf(WorkoutType.RUNNING) }
    var amountText by rememberSaveable { mutableStateOf("30") }
    val closer = rememberSheetCloser()
    val c = Ember.colors
    val t = Ember.type

    val amount = amountText.toIntOrNull()?.takeIf { it in 1..MAX_AMOUNT }
    val previewKcal = amount?.let {
        when (selected.kind) {
            WorkoutKind.DURATION -> WorkoutMath.kcalForDuration(selected, weightKg, it)
            WorkoutKind.REPS -> WorkoutMath.kcalForReps(selected, weightKg, it)
        }
    }
    // The estimate keeps its place while the amount is being retyped (an empty field): the last
    // valid number stays laid out but hidden, so the hint and the button below do not jump.
    val lastValid = remember { intArrayOf(0) }
    val lastKcal = previewKcal?.roundToInt()?.also { lastValid[0] = it } ?: lastValid[0]

    val title = stringResource(R.string.workout_add)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title, sheetState = closer.state) {
        SheetHeader(
            title,
            // Every workout added here is today's (the Today card and Quick add both write today).
            subtitle = stringResource(R.string.tab_today),
            onClose = { closer.close(onDismiss) },
            closeLabel = stringResource(R.string.cancel),
        )

        Column(Modifier.padding(top = 4.dp)) {
            val typeLabel = stringResource(R.string.workout_type_label)
            SheetFieldLabel(typeLabel)
            val types = WorkoutType.entries
            ChipGroup(
                options = types.map { stringResource(workoutTypeLabel(it)) },
                selected = setOf(types.indexOf(selected)),
                onToggle = { i ->
                    val type = types[i]
                    // Only reset the amount when the input UNIT changes
                    // (minutes <-> repetitions).
                    if (type.kind != selected.kind) {
                        amountText = if (type.kind == WorkoutKind.DURATION) "30" else "20"
                    }
                    selected = type
                },
                groupLabel = typeLabel,
            )
        }

        val duration = selected.kind == WorkoutKind.DURATION
        val fieldLabel = stringResource(if (duration) R.string.workout_minutes_label else R.string.workout_reps_label)
        Column(Modifier.padding(top = 6.dp)) {
            SheetFieldLabel(fieldLabel)
            // Minutes step by 5 and reps by 1, landing on the step's grid (32 → 35 → 40).
            val step = if (duration) 5 else 1
            val base = amount ?: if (duration) 30 else 20
            AmountStepper(
                text = amountText,
                onTextChange = { amountText = it },
                unit = if (duration) {
                    stringResource(R.string.quickadd_unit_min)
                } else {
                    pluralStringResource(R.plurals.quickadd_unit_reps, base)
                },
                onDecrease = { amountText = (((base - 1) / step) * step).coerceIn(1, MAX_AMOUNT).toString() },
                onIncrease = { amountText = ((base / step + 1) * step).coerceIn(1, MAX_AMOUNT).toString() },
                fieldLabel = fieldLabel,
                decreaseLabel = stringResource(R.string.amount_less),
                increaseLabel = stringResource(R.string.amount_more),
                isError = amountText.isNotBlank() && amount == null,
                keyboardType = KeyboardType.Number,
            )
        }

        // "≈ 318 kcal" in Ember digits; TalkBack hears "About 318 kcal".
        val format = rememberIntegerFormat()
        val about = stringResource(R.string.quickadd_kcal_about, format.format(lastKcal))
        val numberStyle = t.display.copy(fontSize = 40.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .graphicsLayer { alpha = if (previewKcal != null) 1f else 0f }
                .clearAndSetSemantics { if (previewKcal != null) contentDescription = about },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(
                stringResource(R.string.quickadd_approx),
                style = numberStyle,
                color = { c.emberText1 },
                modifier = Modifier
                    .alignByBaseline()
                    .padding(end = 8.dp),
            )
            NumberWithUnit(
                lastKcal.toLong(),
                stringResource(R.string.kcal_unit),
                numberStyle,
                Modifier.alignByBaseline(),
                format = format,
                gradient = true,
                unitSize = 17.sp,
            )
        }
        BasicText(
            stringResource(R.string.workout_dialog_hint),
            style = t.footnote.copy(textAlign = TextAlign.Center),
            color = { c.label2 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )

        EmberButton(
            stringResource(R.string.add_action),
            onClick = { amount?.let { a -> closer.close { onConfirm(selected, a) } } },
            modifier = Modifier.padding(top = 4.dp),
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
            enabled = amount != null,
        )
    }
}

/** Three digits of minutes or repetitions: more is a typo, not a workout. */
private const val MAX_AMOUNT = 999
