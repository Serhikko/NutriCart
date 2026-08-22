package com.nutricart.app.ui.fridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.aisleLabel
import com.nutricart.app.ui.shopping.ShareListAction
import com.nutricart.app.ui.shopping.ShoppingBody

/**
 * Groceries in both of their states: what I HAVE (in stock) and what I still
 * NEED (to buy). One tab, because it is one thing — the shopping list is where
 * the fridge gets filled from, and cooking is where it empties.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit) {
    var showStock by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val bodyState = rememberSaveableStateHolder()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fridge_title)) },
                // The share icon belongs to the list, so it only exists while
                // the list is the thing on screen.
                actions = { if (!showStock) ShareListAction() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = showStock,
                    onClick = { showStock = true },
                    label = { Text(stringResource(R.string.fridge_tab_stock)) },
                )
                FilterChip(
                    selected = !showStock,
                    onClick = { showStock = false },
                    label = { Text(stringResource(R.string.fridge_tab_to_buy)) },
                )
            }

            // One saved slot per half, so switching does not throw away the
            // other one's scroll position.
            Box(modifier = Modifier.weight(1f)) {
                bodyState.SaveableStateProvider(key = showStock) {
                    if (showStock) {
                        StockBody(onOpenRecipe = onOpenRecipe)
                    } else {
                        ShoppingBody(snackbarHostState = snackbarHostState)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockBody(
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: FridgeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pickable by viewModel.pickable.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FridgeItemUi?>(null) }

    if (state.loading) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (state.ideas.isNotEmpty()) {
            item {
                IdeasCard(ideas = state.ideas, onOpenRecipe = onOpenRecipe)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.fridge_add_action))
            }
        }

        if (state.itemCount == 0) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.fridge_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.itemsByAisle.forEach { (aisle, items) ->
            item(key = "aisle-$aisle") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    aisleLabel(aisle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(items, key = { it.name }) { item ->
                FridgeRow(item = item, onClick = { editing = item })
            }
        }
    }

    if (showAdd) {
        AddToFridgeDialog(
            ingredients = pickable,
            onConfirm = { ingredient, grams ->
                viewModel.add(ingredient, grams)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { item ->
        EditFridgeItemDialog(
            item = item,
            onConfirm = { grams ->
                viewModel.setGrams(item, grams)
                editing = null
            },
            onRemove = {
                viewModel.remove(item)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** "You could cook this now" — computed from real stock, never from the plan. */
@Composable
private fun IdeasCard(
    ideas: List<FridgeMath.Match>,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.fridge_ideas_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ideas.forEach { idea ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Portion factor 1.0: the recipe as written, because
                        // nothing has sized a portion for this dish yet.
                        .clickable { onOpenRecipe(idea.recipeId, 1.0) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(idea.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (idea.missingCount == 0) {
                                stringResource(R.string.fridge_idea_ready)
                            } else {
                                stringResource(
                                    R.string.fridge_idea_missing,
                                    idea.missingNames.joinToString(", "),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (idea.missingCount == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FridgeRow(item: FridgeItemUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.grams_value, item.displayGrams),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
