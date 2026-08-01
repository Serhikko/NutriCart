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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The shopping list: day chips, aisle groups, checkboxes, share/copy export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose { }
    }

    // Labels and formats are resolved HERE (Compose has the language context)
    // and handed to the export provider as plain functions — the shared text
    // then matches the screen in any language.
    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val amountLabel: (Int, Int?) -> String = { grams, pieces ->
        context.getString(R.string.grams_value, grams) +
            (pieces?.let { context.getString(R.string.piece_hint, it) } ?: "")
    }
    val copiedMessage = stringResource(R.string.copied_toast)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    IconButton(
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
                        enabled = state.hasList,
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.share_action),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                        onToggle = viewModel::toggleDay,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::regenerate,
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
                            onChecked = { checked -> viewModel.setChecked(item, checked) },
                            onToggleHave = { viewModel.toggleAlreadyHave(item) },
                        )
                    }
                }

                if (state.hasList) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(
                                        viewModel.buildShareText(
                                            aisleLabel = { aisleLabels.getValue(it) },
                                            amountLabel = amountLabel,
                                        )
                                    )
                                )
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.copy_action))
                        }
                    }
                }
            }
        }
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
                    (item.pieces?.let { stringResource(R.string.piece_hint, it) } ?: ""),
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

/** Maps each aisle enum value to its translated label. */
@Composable
fun aisleLabel(aisle: Aisle): String = stringResource(
    when (aisle) {
        Aisle.PRODUCE -> R.string.aisle_produce
        Aisle.MEAT_FISH -> R.string.aisle_meat_fish
        Aisle.DAIRY_EGGS -> R.string.aisle_dairy_eggs
        Aisle.BAKERY -> R.string.aisle_bakery
        Aisle.GRAINS_PASTA -> R.string.aisle_grains
        Aisle.CANNED -> R.string.aisle_canned
        Aisle.FROZEN -> R.string.aisle_frozen
        Aisle.SPICES_OILS -> R.string.aisle_spices_oils
        Aisle.SNACKS -> R.string.aisle_snacks
        Aisle.BEVERAGES -> R.string.aisle_beverages
        Aisle.OTHER -> R.string.aisle_other
    }
)
