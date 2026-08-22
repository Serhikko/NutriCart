package com.nutricart.app.ui.fridge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.data.repository.AiResult

/**
 * The optional assistant. Without a key this is a single line of text pointing
 * at Settings — no request, no error, and everything else on the screen works
 * exactly the same.
 *
 * The answer is rendered as it came and is never parsed: nothing the model
 * writes can reach the database or a number the app shows.
 */
@Composable
fun FridgeAiBlock(
    state: AiUiState,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (!state.hasKey) {
        Text(
            stringResource(R.string.ai_no_key_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings_title))
        }
        return
    }

    OutlinedButton(
        onClick = onAsk,
        // Never spend the user's money on an empty fridge, and never twice at
        // the same time.
        enabled = canAsk && !state.loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (state.loading) R.string.ai_asking else R.string.ai_ask_action
            )
        )
    }

    val result = state.result ?: return
    Spacer(modifier = Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (result) {
                is AiResult.Ok -> {
                    Text(result.text, style = MaterialTheme.typography.bodyMedium)
                    if (result.truncated) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.ai_truncated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // The permanent line under every answer: the app cannot
                    // check an AI dish against the allergy list, because
                    // ingredients carry no allergen data.
                    Text(
                        stringResource(R.string.ai_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // What this question just cost, on the user's own key.
                    Text(
                        stringResource(
                            R.string.ai_tokens,
                            result.inputTokens,
                            result.outputTokens,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Text(
                    stringResource(errorMessage(result)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/** Each failure gets its own words; "something went wrong" is the last resort. */
private fun errorMessage(result: AiResult): Int = when (result) {
    AiResult.BadKey -> R.string.ai_error_bad_key
    AiResult.Offline -> R.string.ai_error_offline
    AiResult.TooSlow -> R.string.ai_error_slow
    AiResult.Busy -> R.string.ai_error_busy
    AiResult.Refused -> R.string.ai_error_refused
    AiResult.Empty -> R.string.ai_error_empty
    AiResult.NoKey -> R.string.ai_no_key_hint
    else -> R.string.ai_error_failed
}
