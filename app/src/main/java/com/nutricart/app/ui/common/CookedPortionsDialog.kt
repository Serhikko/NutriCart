package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R

/**
 * "How many portions did you cook?" — one number, defaulting to one.
 *
 * Batch cooking plans the SAME pot for two or three days, so a single tap has
 * to be able to say "I cooked all three". Asking beats guessing from the plan:
 * a hidden multiplier is exactly the kind of invisible arithmetic that makes a
 * fridge quietly wrong.
 *
 * Shared between the recipe screen and the plan row so both deduct by the same
 * rule and there is only one dialog to explain.
 */
@Composable
fun CookedPortionsDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var portions by rememberSaveable { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fridge_cooked_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(
                        onClick = { portions-- },
                        enabled = portions > 1,
                    ) { Text("−") }
                    Text(
                        portions.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TextButton(
                        onClick = { portions++ },
                        enabled = portions < MAX_PORTIONS,
                    ) { Text("+") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.fridge_cooked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(portions) }) {
                Text(stringResource(R.string.fridge_cooked_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A week has seven days; nobody cooks more portions of one dish at once. */
private const val MAX_PORTIONS = 7
