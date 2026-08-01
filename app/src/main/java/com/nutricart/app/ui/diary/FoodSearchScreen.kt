package com.nutricart.app.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.FoodMath
import kotlin.math.roundToInt

/** Search Open Food Facts (or the offline cache) and log the picked product. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    onDone: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Entry saved -> back to the diary.
    LaunchedEffect(state.logged) {
        if (state.logged) onDone()
    }

    // One-shot barcode messages -> snackbar.
    val notFoundMessage = stringResource(R.string.barcode_not_found)
    val offlineMessage = stringResource(R.string.barcode_offline)
    val scanFailedMessage = stringResource(R.string.barcode_scan_failed)
    LaunchedEffect(state.scanMessage) {
        val message = state.scanMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (message) {
                ScanMessage.PRODUCT_NOT_FOUND -> notFoundMessage
                ScanMessage.OFFLINE -> offlineMessage
                ScanMessage.SCANNER_FAILED -> scanFailedMessage
            }
        )
        viewModel.clearScanMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(mealSlotLabel(viewModel.mealSlot)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_action),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                supportingText = {
                    if (state.queryTooShort) Text(stringResource(R.string.search_min_chars))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::scanBarcode,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scan_barcode))
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.offline && state.searched) {
                Text(
                    stringResource(R.string.offline_results_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (state.searched && !state.searching && state.results.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn {
                items(state.results, key = { it.id }) { product ->
                    ProductRow(product = product, onClick = { viewModel.select(product) })
                    HorizontalDivider()
                }
            }
        }
    }

    // Amount dialog for the tapped product.
    state.selected?.let { product ->
        AmountDialog(
            product = product,
            onConfirm = { grams, servings -> viewModel.log(product, grams, servings) },
            onDismiss = { viewModel.select(null) },
        )
    }
}

@Composable
private fun ProductRow(product: FoodProductEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(product.name, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                product.brand ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.kcal_per_100g, product.kcalPer100g.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Asks how much was eaten: always in grams, or in portions when the product
 * states a portion size. Shows a live kcal preview for the typed amount.
 */
@Composable
private fun AmountDialog(
    product: FoodProductEntity,
    onConfirm: (grams: Double, servings: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: the typed amount must survive screen rotation.
    var usePortions by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("100") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    // The grams behind the typed amount (portions get converted).
    val grams = when {
        amount == null || amount <= 0.0 -> null
        usePortions && product.servingSizeG != null ->
            FoodMath.servingsToGrams(amount, product.servingSizeG)
        else -> amount
    }?.takeIf { it <= 5000.0 } // sanity limit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column {
                if (product.servingSizeG != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !usePortions,
                            onClick = { usePortions = false; amountText = "100" },
                            label = { Text(stringResource(R.string.grams_mode)) },
                        )
                        FilterChip(
                            selected = usePortions,
                            onClick = { usePortions = true; amountText = "1" },
                            label = { Text(stringResource(R.string.portions_mode)) },
                        )
                    }
                    if (usePortions) {
                        Text(
                            stringResource(
                                R.string.portion_size_note,
                                product.servingSizeG.roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            stringResource(
                                if (usePortions) R.string.portions_mode
                                else R.string.grams_mode
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )

                if (grams != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val kcal = FoodMath.forGrams(
                        product.kcalPer100g, product.proteinPer100g,
                        product.fatPer100g, product.carbsPer100g, grams,
                    ).kcal
                    Text(
                        stringResource(R.string.kcal_value, kcal.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = grams != null,
                onClick = {
                    if (grams != null) {
                        onConfirm(grams, if (usePortions) amount else null)
                    }
                },
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
