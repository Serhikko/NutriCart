package com.nutricart.app.ui.shopping

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.aisleLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The "to buy" half of the fridge tab: day chips, aisle groups, checkboxes and
 * the export. It owns no chrome — the fridge screen above it draws the app bar
 * (including the share action) and holds the snackbar.
 */
@Composable
fun ShoppingBody(
    onMessage: (String) -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose { }
    }

    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val amountLabel = amountLabel()
    val copiedMessage = stringResource(R.string.copied_toast)
    val movedMessage = stringResource(R.string.fridge_moved_toast)

    ShoppingContent(
        state = state,
        onToggleDay = viewModel::toggleDay,
        onRegenerate = viewModel::regenerate,
        onSetChecked = viewModel::setChecked,
        onToggleHave = viewModel::toggleAlreadyHave,
        onMoveBought = {
            viewModel.moveBoughtToFridge()
            onMessage(movedMessage)
        },
        onCopy = {
            clipboard.setText(
                AnnotatedString(
                    viewModel.buildShareText(
                        aisleLabel = { aisleLabels.getValue(it) },
                        amountLabel = amountLabel,
                    )
                )
            )
            onMessage(copiedMessage)
        },
    )
}

/** The stateless half of [ShoppingBody]: draws [state], forwards every tap. */
@Composable
fun ShoppingContent(
    state: ShoppingUiState,
    onToggleDay: (epochDay: Long) -> Unit,
    onRegenerate: () -> Unit,
    onSetChecked: (item: ShoppingItemUi, checked: Boolean) -> Unit,
    onToggleHave: (item: ShoppingItemUi) -> Unit,
    onMoveBought: () -> Unit,
    onCopy: () -> Unit,
) {
    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }

    if (state.loading) {
        LoadingBox()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.shopping_days_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DayChips(
                weekDays = state.weekDays,
                selected = state.selectedDays,
                onToggle = onToggleDay,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRegenerate,
                enabled = state.hasPlan && !state.generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.hasList) R.string.shopping_regenerate
                        else R.string.shopping_generate
                    )
                )
            }
            if (!state.hasPlan) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.shopping_no_plan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        state.itemsByAisle.forEach { (aisle, items) ->
            item(key = "aisle-$aisle") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    aisleLabels.getValue(aisle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(items, key = { it.id }) { item ->
                ShoppingRow(
                    item = item,
                    onChecked = { checked -> onSetChecked(item, checked) },
                    onToggleHave = { onToggleHave(item) },
                )
            }
        }

        // Carrying the shopping into the fridge is an explicit action, never a
        // side effect of ticking a row: a silent write into another table is
        // impossible to notice and impossible to take back.
        if (state.movableCount > 0) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onMoveBought,
                    enabled = !state.moving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.fridge_move_bought, state.movableCount))
                }
            }
        }

        if (state.hasList) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.copy_action))
                }
            }
        }
    }
}

/**
 * The share icon for the fridge screen's app bar. It lives here, next to the
 * list it shares, and builds its own labels — Compose has the language context
 * here, so the exported text always matches what the screen shows.
 */
@Composable
fun ShareListAction(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val amountLabel = amountLabel()

    ShareListButton(
        enabled = state.hasList,
        onClick = {
            val text = viewModel.buildShareText(
                aisleLabel = { aisleLabels.getValue(it) },
                amountLabel = amountLabel,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
    )
}

/** The stateless share icon of [ShareListAction]. */
@Composable
fun ShareListButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.Filled.Share,
            contentDescription = stringResource(R.string.share_action),
        )
    }
}

/** "250 g (~2 pcs)" in the phone's language, for both the screen and the export. */
@Composable
private fun amountLabel(): (Int, Int?) -> String {
    val context = LocalContext.current
    return { grams, pieces ->
        context.getString(R.string.grams_value, grams) +
            (pieces?.let { context.getString(R.string.piece_hint, it) } ?: "")
    }
}

@Composable
private fun DayChips(
    weekDays: List<Long>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d") }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(weekDays, key = { it }) { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(LocalDate.ofEpochDay(day).format(formatter)) },
            )
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItemUi,
    onChecked: (Boolean) -> Unit,
    onToggleHave: () -> Unit,
) {
    val struck = item.isChecked || item.alreadyHave
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onChecked,
            enabled = !item.alreadyHave, // nothing to buy -> nothing to tick
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                color = if (item.alreadyHave) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.grams_value, item.displayGrams) +
                    (item.pieces?.let { stringResource(R.string.piece_hint, it) } ?: "") +
                    if (item.movedToFridge) {
                        " · " + stringResource(R.string.fridge_in_stock_note)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToggleHave) {
            Text(
                stringResource(
                    if (item.alreadyHave) R.string.need_it_action
                    else R.string.already_have_action
                )
            )
        }
    }
}
