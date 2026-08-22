package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.model.ProfileOptions

/**
 * One field: today's weight. Saving writes a MANUAL weight entry for today,
 * which beats a watch reading for the same day — the same rule the settings
 * form follows. The value lives as typed text so rotation cannot eat it.
 */
@Composable
fun AddWeightDialog(
    currentWeightKg: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(if (currentWeightKg > 0.0) currentWeightKg.toString() else "")
    }
    val weight = text.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it in ProfileOptions.WEIGHT_KG_RANGE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_card_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.weight_label)) },
                    isError = text.isNotBlank() && weight == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.weight_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = weight != null,
                onClick = { weight?.let(onConfirm) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
