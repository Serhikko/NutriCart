package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.model.Allergen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Small form widgets shared by the onboarding and settings screens,
 * so both screens look and behave exactly the same.
 */

@Composable
fun SwitchRow(labelRes: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/** One radio-button choice; descRes is optional smaller text under the title. */
@Composable
fun RadioOptionRow(titleRes: Int, descRes: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            if (descRes != null) {
                Text(
                    stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ErrorCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Button that shows the chosen date and opens a Material date picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(date: LocalDate?, onDatePicked: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }

    OutlinedButton(onClick = { showPicker = true }) {
        Text(date?.format(dateFormatter) ?: stringResource(R.string.choose_date))
    }

    if (showPicker) {
        // Seed the dialog with the already-chosen date, so reopening it
        // continues from the previous choice instead of starting empty.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = 1920..LocalDate.now().year,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            // The Material date picker works in UTC, so convert in UTC too —
                            // otherwise the date can shift by one day in some time zones.
                            onDatePicked(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showPicker = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Maps each allergen enum value to its translated label. */
@Composable
fun allergenLabel(allergen: Allergen): String = stringResource(
    when (allergen) {
        Allergen.GLUTEN -> R.string.allergen_gluten
        Allergen.DAIRY -> R.string.allergen_dairy
        Allergen.EGGS -> R.string.allergen_eggs
        Allergen.NUTS -> R.string.allergen_nuts
        Allergen.PEANUTS -> R.string.allergen_peanuts
        Allergen.FISH -> R.string.allergen_fish
        Allergen.SHELLFISH -> R.string.allergen_shellfish
        Allergen.SOY -> R.string.allergen_soy
    }
)
