package com.nutricart.app.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.dao.SavedMealSummary
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.FoodMath
import com.nutricart.app.domain.model.ProductSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

    // Arrived from the quick-add sheet's scan row: straight into the camera.
    LaunchedEffect(Unit) { viewModel.scanOnOpenIfAsked() }

    // Entry saved -> back to the diary.
    LaunchedEffect(state.logged) {
        if (state.logged) onDone()
    }

    // One-shot reminder: something was logged but the basket still has items.
    val basketReminderMessage = stringResource(R.string.basket_reminder)
    LaunchedEffect(state.basketReminder) {
        if (state.basketReminder) {
            snackbarHostState.showSnackbar(basketReminderMessage)
            viewModel.clearBasketReminder()
        }
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
                title = {
                    Column {
                        Text(mealSlotLabel(viewModel.mealSlot))
                        // The day is spelled out because this screen is now
                        // reachable from the quick-add sheet, which always
                        // means TODAY even when the diary is showing another
                        // day. Silently logging into the wrong day would be
                        // invisible until the numbers stopped adding up.
                        Text(
                            targetDayLabel(viewModel.epochDay),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.favoritesMode,
                        onClick = viewModel::toggleFavoritesMode,
                        label = { Text(stringResource(R.string.favorites_chip)) },
                    )
                    FilterChip(
                        selected = state.savedMealsMode,
                        onClick = viewModel::toggleSavedMealsMode,
                        label = { Text(stringResource(R.string.saved_meals_chip)) },
                    )
                }
                TextButton(onClick = viewModel::openCreateForm) {
                    Text(stringResource(R.string.create_food))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // The basket bar appears as soon as something is collected.
            if (state.basket.isNotEmpty() && !state.savedMealsMode) {
                FilledTonalButton(
                    onClick = viewModel::openBasket,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.basket_button, state.basket.size))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Search chrome belongs to search results only — saved meals are
            // purely local, an "offline" note above them would be nonsense.
            if (state.searching && !state.savedMealsMode) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.offline && state.searched &&
                !state.favoritesMode && !state.savedMealsMode
            ) {
                Text(
                    stringResource(R.string.offline_results_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // What the list shows: saved meals, favorites, or (with an empty
            // box) the user's frequent products, or the search results.
            val showFrequent = !state.favoritesMode && !state.savedMealsMode &&
                state.query.isBlank()
            val listItems = when {
                state.savedMealsMode -> emptyList()
                state.favoritesMode -> state.favorites
                showFrequent -> state.frequent
                else -> state.results
            }

            if (state.savedMealsMode && state.savedMeals.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.saved_meals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.favoritesMode && state.favorites.isEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    // The list is query-filtered: with text typed, an empty
                    // list means "no matches", NOT "you have no favorites".
                    stringResource(
                        if (state.query.isBlank()) R.string.favorites_empty
                        else R.string.no_results
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showFrequent && state.frequent.isNotEmpty()) {
                Text(
                    stringResource(R.string.frequent_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.favoritesMode && !state.savedMealsMode && !showFrequent &&
                state.searched && !state.searching && state.results.isEmpty()
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.savedMealsMode) {
                LazyColumn {
                    items(state.savedMeals, key = { it.id }) { meal ->
                        SavedMealRow(
                            meal = meal,
                            onClick = { viewModel.logSavedMeal(meal) },
                            onDelete = { viewModel.deleteSavedMeal(meal) },
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                LazyColumn {
                    items(listItems, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            inBasket = state.basket.any { it.product.id == product.id },
                            onClick = { viewModel.select(product) },
                            onQuickAdd = { viewModel.addToBasket(product) },
                            onToggleFavorite = { viewModel.toggleFavorite(product) },
                            onEdit = { viewModel.openEditForm(product) },
                        )
                        HorizontalDivider()
                    }
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

    // Create/edit form for the user's own products.
    state.customForm?.let { form ->
        CustomFoodDialog(
            editing = form.editing,
            onSave = viewModel::saveCustomProduct,
            onDelete = if (form.editing != null) viewModel::deleteCustomProduct else null,
            onDismiss = viewModel::dismissCustomForm,
        )
    }

    // Review the collected products, adjust grams, log them all at once.
    if (state.basketOpen) {
        BasketDialog(
            items = state.basket,
            onGramsChange = viewModel::setBasketGrams,
            onRemove = viewModel::removeFromBasket,
            onConfirm = viewModel::logBasket,
            onDismiss = viewModel::closeBasket,
        )
    }
}

/** The multi-add review: one line per product with editable grams. */
/** "Today", or the actual date when this search targets some other day. */
@Composable
private fun targetDayLabel(epochDay: Long): String {
    val day = LocalDate.ofEpochDay(epochDay)
    if (day == LocalDate.now()) return stringResource(R.string.tab_today)
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return day.format(formatter)
}

@Composable
private fun BasketDialog(
    items: List<BasketItem>,
    onGramsChange: (productId: String, text: String) -> Unit,
    onRemove: (productId: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allValid = items.isNotEmpty() && items.all { basketGrams(it) != null }
    val totalKcal = items.sumOf { item ->
        basketGrams(item)?.let { g -> item.product.kcalPer100g * g / 100.0 } ?: 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.basket_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, style = MaterialTheme.typography.bodyMedium)
                            basketGrams(item)?.let { g ->
                                Text(
                                    stringResource(
                                        R.string.kcal_value,
                                        (item.product.kcalPer100g * g / 100.0).roundToInt(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = item.gramsText,
                            onValueChange = { onGramsChange(item.product.id, it) },
                            label = { Text(stringResource(if (product.isLiquid) R.string.ml_mode else R.string.grams_mode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(96.dp),
                        )
                        IconButton(onClick = { onRemove(item.product.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.basket_total, totalKcal.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = allValid, onClick = onConfirm) {
                Text(stringResource(R.string.basket_add_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One saved meal: tap = log everything into this diary section. */
@Composable
private fun SavedMealRow(
    meal: SavedMealSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        ) {
            Text(meal.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.saved_meal_summary,
                    meal.itemCount,
                    meal.totalKcal.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.saved_meal_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductRow(
    product: FoodProductEntity,
    inBasket: Boolean,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
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
                    stringResource(if (product.isLiquid) R.string.kcal_per_100ml else R.string.kcal_per_100g, product.kcalPer100g.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Quick multi-add: "+" collects into the basket, a check marks "already in".
        IconButton(onClick = onQuickAdd, enabled = !inBasket) {
            Icon(
                if (inBasket) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = stringResource(
                    if (inBasket) R.string.basket_in else R.string.basket_quick_add
                ),
                tint = if (inBasket) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One star icon, two colors: filled-primary = starred, faded = not.
        // (icons-core has no StarBorder — tint does the job with one icon.)
        IconButton(onClick = onToggleFavorite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = stringResource(R.string.favorite_toggle),
                tint = if (product.isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            )
        }
        // Only the user's own products are editable — OFF data is read-only.
        if (product.source == ProductSource.LOCAL) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.custom_food_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Create/edit form for a user-defined product. All numbers are per 100 g,
 * like on any nutrition label. Save stays disabled until the input is sane.
 */
@Composable
private fun CustomFoodDialog(
    editing: FoodProductEntity?,
    onSave: (CustomFoodDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: typed values survive rotation, like the amount dialog.
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var brand by rememberSaveable { mutableStateOf(editing?.brand ?: "") }
    var kcalText by rememberSaveable { mutableStateOf(editing?.kcalPer100g?.roundToInt()?.toString() ?: "") }
    var proteinText by rememberSaveable { mutableStateOf(editing?.proteinPer100g?.toString() ?: "") }
    var fatText by rememberSaveable { mutableStateOf(editing?.fatPer100g?.toString() ?: "") }
    var carbsText by rememberSaveable { mutableStateOf(editing?.carbsPer100g?.toString() ?: "") }
    var servingText by rememberSaveable { mutableStateOf(editing?.servingSizeG?.roundToInt()?.toString() ?: "") }
    var fiberText by rememberSaveable { mutableStateOf(editing?.fiberPer100g?.toString() ?: "") }
    var sugarsText by rememberSaveable { mutableStateOf(editing?.sugarsPer100g?.toString() ?: "") }
    var saltText by rememberSaveable { mutableStateOf(editing?.saltPer100g?.toString() ?: "") }
    var satFatText by rememberSaveable { mutableStateOf(editing?.saturatedFatPer100g?.toString() ?: "") }

    fun parse(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
    // Optional field: blank is fine (null), a typed value must be sane.
    fun optional(text: String): Double? =
        if (text.isBlank()) null else parse(text)?.takeIf { it in 0.0..100.0 }
    fun optionalOk(text: String): Boolean = text.isBlank() || optional(text) != null

    val kcal = parse(kcalText)?.takeIf { it in 0.0..900.0 }
    val protein = parse(proteinText)?.takeIf { it in 0.0..100.0 }
    val fat = parse(fatText)?.takeIf { it in 0.0..100.0 }
    val carbs = parse(carbsText)?.takeIf { it in 0.0..100.0 }
    val serving = if (servingText.isBlank()) null else parse(servingText)?.takeIf { it in 1.0..5000.0 }
    val servingOk = servingText.isBlank() || serving != null
    val valid = name.isNotBlank() && kcal != null &&
        protein != null && fat != null && carbs != null && servingOk &&
        optionalOk(fiberText) && optionalOk(sugarsText) &&
        optionalOk(saltText) && optionalOk(satFatText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editing == null) R.string.custom_food_new_title
                    else R.string.custom_food_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.custom_food_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text(stringResource(R.string.custom_food_brand)) },
                    singleLine = true,
                )
                NumberField(kcalText, { kcalText = it }, R.string.custom_food_kcal)
                NumberField(proteinText, { proteinText = it }, R.string.custom_food_protein)
                NumberField(fatText, { fatText = it }, R.string.custom_food_fat)
                NumberField(carbsText, { carbsText = it }, R.string.custom_food_carbs)
                NumberField(servingText, { servingText = it }, R.string.custom_food_serving)
                Text(
                    stringResource(R.string.custom_food_optional_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberField(fiberText, { fiberText = it }, R.string.custom_food_fiber)
                NumberField(sugarsText, { sugarsText = it }, R.string.custom_food_sugars)
                NumberField(saltText, { saltText = it }, R.string.custom_food_salt)
                NumberField(satFatText, { satFatText = it }, R.string.custom_food_sat_fat)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.custom_food_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (valid) {
                        onSave(
                            CustomFoodDraft(
                                name = name,
                                brand = brand.takeIf { it.isNotBlank() },
                                kcalPer100g = kcal!!,
                                proteinPer100g = protein!!,
                                fatPer100g = fat!!,
                                carbsPer100g = carbs!!,
                                servingSizeG = serving,
                                fiberPer100g = optional(fiberText),
                                sugarsPer100g = optional(sugarsText),
                                saltPer100g = optional(saltText),
                                saturatedFatPer100g = optional(satFatText),
                            )
                        )
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One label row of the per-100g table; null renders as a dash, never 0. */
@Composable
private fun NutrientRow(labelRes: Int, valuePer100g: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            valuePer100g?.let { stringResource(R.string.nutrient_grams_value, it) }
                ?: stringResource(R.string.no_data_dash),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, labelRes: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (product.servingSizeG != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !usePortions,
                            onClick = { usePortions = false; amountText = "100" },
                            label = { Text(stringResource(if (product.isLiquid) R.string.ml_mode else R.string.grams_mode)) },
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
                                if (product.isLiquid) R.string.portion_size_note_ml else R.string.portion_size_note,
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
                                else if (product.isLiquid) R.string.ml_mode
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

                // The full label, per 100 g (or ml for a drink). "—" = the source didn't state it.
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(if (product.isLiquid) R.string.per100ml_header else R.string.per100g_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                NutrientRow(R.string.summary_protein, product.proteinPer100g)
                NutrientRow(R.string.summary_fat, product.fatPer100g)
                NutrientRow(R.string.summary_carbs, product.carbsPer100g)
                NutrientRow(R.string.nutrient_fiber, product.fiberPer100g)
                NutrientRow(R.string.nutrient_sugars, product.sugarsPer100g)
                NutrientRow(R.string.nutrient_salt, product.saltPer100g)
                NutrientRow(R.string.nutrient_sat_fat, product.saturatedFatPer100g)
                product.additivesCsv?.let { csv ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.additives_line, csv.replace(",", ", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
