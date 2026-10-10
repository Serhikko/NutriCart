package com.nutricart.app.ui.diary

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.dao.SavedMealSummary
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.logic.BarcodeOrigin
import com.nutricart.app.domain.logic.FoodMath
import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ProductSource
import com.nutricart.app.ui.common.DayBudget
import com.nutricart.app.ui.common.DayBudgetViewModel
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.BackLink
import com.nutricart.app.ui.ember.BottomClearance
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.CompositionBar
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Digits
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberDurations
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberSearchField
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSheet
import com.nutricart.app.ui.ember.EmberSpace
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmberTextField
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.SheetHeader
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.SwitchRow
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.WholeWordsText
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.ember.emberSpec
import com.nutricart.app.ui.ember.emberSharedBounds
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.ember.pressScale
import com.nutricart.app.ui.ember.rememberDecimalFormat
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.navigation.LocalBackLabel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Search Open Food Facts (or the offline cache) and log the picked product. */
@Composable
fun FoodSearchScreen(
    onDone: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel(),
    budgetViewModel: DayBudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val budget by budgetViewModel.budget.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // The day this search logs into: its budget feeds the chip and the amount sheet.
    LaunchedEffect(viewModel.epochDay) { budgetViewModel.show(viewModel.epochDay) }

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
            snackbarHostState.showSnackbar(EmberToastVisuals(basketReminderMessage, ToastIcon.Info))
            viewModel.clearBasketReminder()
        }
    }

    // One-shot barcode messages -> toast.
    val notFoundMessage = stringResource(R.string.barcode_not_found)
    // Resolved here: stringResource cannot be called inside the effect below.
    val noticeMessage = (state.scanMessage as? ScanMessage.ProductNotFound)
        ?.let { stringResource(noticeText(it.notice)) }
    val addActionLabel = stringResource(R.string.add_action)
    val offlineMessage = stringResource(R.string.barcode_offline)
    val scanFailedMessage = stringResource(R.string.barcode_scan_failed)
    LaunchedEffect(state.scanMessage) {
        val message = state.scanMessage ?: return@LaunchedEffect
        when (message) {
            is ScanMessage.ProductNotFound -> {
                if (message.barcode.isBlank() || noticeMessage == null) {
                    snackbarHostState.showSnackbar(EmberToastVisuals(notFoundMessage, ToastIcon.Warning))
                } else {
                    // Not a dead end: "Add" opens the form tied to this barcode,
                    // and the next scan of the pack finds the user's entry.
                    val result = snackbarHostState.showSnackbar(
                        EmberToastVisuals(
                            message = noticeMessage,
                            icon = ToastIcon.Info,
                            actionLabel = addActionLabel,
                            duration = SnackbarDuration.Long,
                        ),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.openBarcodeForm(message.barcode)
                    }
                }
            }
            ScanMessage.Offline -> snackbarHostState.showSnackbar(EmberToastVisuals(offlineMessage, ToastIcon.Warning))
            ScanMessage.ScannerFailed -> snackbarHostState.showSnackbar(EmberToastVisuals(scanFailedMessage, ToastIcon.Warning))
        }
        viewModel.clearScanMessage()
    }

    FoodSearchContent(
        state = state,
        mealSlot = viewModel.mealSlot,
        epochDay = viewModel.epochDay,
        snackbarHostState = snackbarHostState,
        onDone = onDone,
        actions = viewModel,
        budget = budget,
    )
}

/**
 * Everything the food search screen can ask for. [FoodSearchViewModel]
 * implements it; screenshot tests pass a no-op implementation.
 */
interface FoodSearchActions {
    fun setQuery(text: String)
    fun search()
    fun scanBarcode()
    fun toggleFavoritesMode()
    fun toggleSavedMealsMode()
    fun openCreateForm()
    fun openBasket()
    fun closeBasket()
    fun logBasket()
    fun logSavedMeal(meal: SavedMealSummary)
    fun deleteSavedMeal(meal: SavedMealSummary)
    fun select(product: FoodProductEntity?)
    fun addToBasket(product: FoodProductEntity)
    fun removeFromBasket(productId: String)
    fun setBasketGrams(productId: String, text: String)
    fun toggleFavorite(product: FoodProductEntity)
    fun openEditForm(product: FoodProductEntity)
    fun dismissCustomForm()
    fun saveCustomProduct(draft: CustomFoodDraft)
    fun deleteCustomProduct()
    fun log(product: FoodProductEntity, grams: Double, servings: Double?)
}

/**
 * The stateless half of [FoodSearchScreen]: the search page plus the sheets that [state] asks for
 * (amount, custom food form, basket). [budget] (the day's target, when known) adds the budget chip
 * and the amount sheet's preview ring and "left after this". [backLabel] names the screen below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchContent(
    state: FoodSearchUiState,
    mealSlot: MealSlot,
    epochDay: Long,
    snackbarHostState: SnackbarHostState,
    onDone: () -> Unit,
    actions: FoodSearchActions,
    budget: DayBudget? = null,
    backLabel: String = LocalBackLabel.current ?: stringResource(R.string.back),
) {
    // The day is spelled out because this screen is also reachable from the quick-add sheet, which
    // always means TODAY even when the diary is showing another day. Silently logging into the wrong
    // day would be invisible until the numbers stopped adding up.
    val target = "${mealSlotLabel(mealSlot)} · ${targetDayLabel(epochDay)}"
    val basketShown = state.basket.isNotEmpty() && !state.savedMealsMode

    // What the list shows: saved meals, favorites, or (with an empty box) the user's frequent
    // products, or the search results.
    val showFrequent = !state.favoritesMode && !state.savedMealsMode && state.query.isBlank()
    val listItems = when {
        state.savedMealsMode -> emptyList()
        state.favoritesMode -> state.favorites
        showFrequent -> state.frequent
        else -> state.results
    }
    // Built once per list, not per keystroke: which products are in the basket, and the cascade of
    // each new list (a star or a basket tap changes the list but not its ids, so it does not replay).
    val inBasket = remember(state.basket) { state.basket.mapTo(HashSet()) { it.product.id } }
    val productIds = remember(listItems) { listItems.map { it.id } }
    val productCascade = remember(productIds) { CascadeWindow() }
    val mealIds = remember(state.savedMeals) { state.savedMeals.map { it.id } }
    val mealCascade = remember(mealIds) { CascadeWindow() }
    CascadeClock(productCascade)
    CascadeClock(mealCascade)

    LargeTitleScaffold(
        title = stringResource(R.string.add_food),
        eyebrow = target,
        back = BackLink(backLabel, onDone),
        below = budget?.let { b -> { BudgetChip(b) } },
        toastHostState = snackbarHostState,
        bottom = if (basketShown) BottomClearance.DockedCta else BottomClearance.Stacked,
        // The basket bar appears as soon as something is collected.
        dockedCta = if (basketShown) {
            { BasketBar(count = state.basket.size, onClick = actions::openBasket) }
        } else {
            null
        },
    ) {
        item(key = "search") { SearchRow(state, actions) }
        item(key = "filters") { Filters(state, actions) }

        // Search chrome belongs to search results only — saved meals are purely local, an
        // "offline" note above them would be nonsense.
        if (state.offline && state.searched && !state.favoritesMode && !state.savedMealsMode) {
            item(key = "offline") {
                Notice(NoticeTone.Info, stringResource(R.string.offline_results_note), icon = EmberIcons.Offline)
            }
        }

        when {
            state.savedMealsMode -> if (state.savedMeals.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.saved_meals_empty), icon = EmberIcons.Bookmark) }
            } else {
                savedMealRows(state.savedMeals, mealCascade, actions)
            }
            state.searching -> item(key = "searching") {
                InsetGroup { row { SkeletonRows(5) } }
            }
            state.favoritesMode && state.favorites.isEmpty() -> item(key = "empty") {
                // The list is query-filtered: with text typed, an empty list means "no matches",
                // NOT "you have no favorites".
                EmptyState(
                    stringResource(if (state.query.isBlank()) R.string.favorites_empty else R.string.no_results),
                    icon = EmberIcons.Star,
                )
            }
            showFrequent && state.frequent.isEmpty() -> item(key = "empty") {
                EmptyState(stringResource(R.string.add_empty_hint), icon = EmberIcons.Search)
            }
            !state.favoritesMode && !showFrequent && state.searched && state.results.isEmpty() -> item(key = "empty") {
                EmptyState(stringResource(R.string.no_results), icon = EmberIcons.Search)
            }
            listItems.isNotEmpty() -> {
                if (showFrequent) item(key = "frequent") { ListHead(stringResource(R.string.frequent_header)) }
                productRows(listItems, inBasket, productCascade, actions)
            }
        }
    }

    // Amount sheet for the tapped product.
    state.selected?.let { product ->
        AmountSheet(
            product = product,
            budget = budget?.takeIf { it.epochDay == epochDay },
            onConfirm = { grams, servings -> actions.log(product, grams, servings) },
            onDismiss = { actions.select(null) },
        )
    }

    // Create/edit form for the user's own products.
    state.customForm?.let { form ->
        CustomFoodSheet(
            target = form,
            onSave = actions::saveCustomProduct,
            onDelete = if (form.editing != null) actions::deleteCustomProduct else null,
            onDismiss = actions::dismissCustomForm,
        )
    }

    // Review the collected products, adjust grams, log them all at once.
    if (state.basketOpen) {
        BasketSheet(
            items = state.basket,
            subtitle = target,
            onGramsChange = actions::setBasketGrams,
            onRemove = actions::removeFromBasket,
            onConfirm = actions::logBasket,
            onDismiss = actions::closeBasket,
        )
    }
}

/** "Today", or the actual date when this search targets some other day. */
@Composable
private fun targetDayLabel(epochDay: Long): String {
    val day = LocalDate.ofEpochDay(epochDay)
    if (day == LocalDate.now()) return stringResource(R.string.tab_today)
    val locale = LocalConfiguration.current.locales[0]
    return remember(epochDay, locale) { day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
}

// ---------------------------------------------------------------------------------------------
// The page
// ---------------------------------------------------------------------------------------------

/**
 * The day's budget under the title: the 28 dp day ring (where "back from Add" starts: it flies home
 * into the Diary's or Today's ring) and "855 kcal left". TalkBack reads the day line.
 */
@Composable
private fun BudgetChip(budget: DayBudget) {
    val c = Ember.colors
    val format = rememberIntegerFormat()
    val left = budget.remainingKcal
    val described = if (left >= 0) {
        stringResource(R.string.ring_day_line, format.format(budget.eatenKcal), format.format(budget.targetKcal), format.format(left))
    } else {
        stringResource(R.string.ring_day_line_over, format.format(budget.eatenKcal), format.format(budget.targetKcal), format.format(-left))
    }
    Row(
        Modifier
            .emberShadow(Elevation.Card, EmberShapes.capsule, c.isDark)
            .clip(EmberShapes.capsule)
            .background(c.surface)
            .border(.5.dp, c.sep, EmberShapes.capsule)
            .clearAndSetSemantics { contentDescription = described }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ring(
            value = budget.eatenKcal.toFloat(),
            target = budget.targetKcal.toFloat(),
            size = RingSizes.Mini.size,
            stroke = RingSizes.Mini.stroke,
            modifier = Modifier.emberSharedBounds("day-ring/${budget.epochDay}"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Digits(abs(left).toLong(), Ember.type.rowNumber.copy(fontSize = 15.sp), color = c.label)
            ControlText(
                stringResource(if (left >= 0) R.string.day_kcal_left else R.string.day_kcal_over),
                style = Ember.type.subhead.copy(fontWeight = FontWeight.Medium),
                color = c.label2,
                maxLines = 1,
            )
        }
    }
}

/** The search capsule (runs on submit only) and the 48 dp scan button; under 2 characters, a warning line. */
@Composable
private fun SearchRow(state: FoodSearchUiState, actions: FoodSearchActions) {
    val c = Ember.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmberSearchField(
                query = state.query,
                onQueryChange = actions::setQuery,
                onSearch = actions::search,
                placeholder = stringResource(R.string.search_hint),
                searchLabel = stringResource(R.string.search_action),
                modifier = Modifier.weight(1f),
            )
            EmberIconButton(
                icon = EmberIcons.Scan,
                contentDescription = stringResource(R.string.scan_barcode),
                onClick = actions::scanBarcode,
                style = IconButtonStyle.Surface,
                size = 48.dp,
            )
        }
        if (state.queryTooShort) {
            Row(
                Modifier
                    .padding(start = 8.dp, top = 8.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EmberIcon(EmberIcons.Warning, null, size = 16.dp, tint = c.fatInk)
                ControlText(stringResource(R.string.search_min_chars), style = Ember.type.footnote, color = c.label2)
            }
        }
    }
}

/** Favorites | My meals (one at a time, or neither) and "+ Create your own food", wrapping as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(state: FoodSearchUiState, actions: FoodSearchActions) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ChipGroup(
            options = listOf(stringResource(R.string.favorites_chip), stringResource(R.string.saved_meals_chip)),
            selected = buildSet {
                if (state.favoritesMode) add(0)
                if (state.savedMealsMode) add(1)
            },
            onToggle = { i -> if (i == 0) actions.toggleFavoritesMode() else actions.toggleSavedMealsMode() },
        )
        PlainLink(text = stringResource(R.string.create_food), onClick = actions::openCreateForm)
    }
}

/** A quiet head over a list ("Frequently logged"): 13 sp SemiBold label 2, 16 dp in. */
@Composable
private fun ListHead(text: String) {
    ControlText(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = Ember.colors.label2,
    )
}

/**
 * The products as lazy rows of one inset card (only the rows on screen are composed; each row's
 * state belongs to its product, by key). A new list cascades in, 40 ms apart (S1).
 */
private fun LazyListScope.productRows(
    products: List<FoodProductEntity>,
    inBasket: Set<String>,
    cascade: CascadeWindow,
    actions: FoodSearchActions,
) {
    itemsIndexed(products, key = { _, p -> "product/${p.id}" }, contentType = { _, _ -> "product" }) { i, product ->
        GroupSlice(first = i == 0, last = i == products.lastIndex) {
            Cascade(i, cascade) {
                ProductRow(
                    product = product,
                    inBasket = product.id in inBasket,
                    onClick = { actions.select(product) },
                    onQuickAdd = { actions.addToBasket(product) },
                    onToggleFavorite = { actions.toggleFavorite(product) },
                    onEdit = { actions.openEditForm(product) },
                )
            }
        }
    }
}

/** The saved meals as lazy rows of one inset card, like [productRows]. */
private fun LazyListScope.savedMealRows(meals: List<SavedMealSummary>, cascade: CascadeWindow, actions: FoodSearchActions) {
    itemsIndexed(meals, key = { _, m -> "meal/${m.id}" }, contentType = { _, _ -> "meal" }) { i, meal ->
        GroupSlice(first = i == 0, last = i == meals.lastIndex) {
            Cascade(i, cascade) {
                SavedMealRow(
                    meal = meal,
                    onClick = { actions.logSavedMeal(meal) },
                    onDelete = { actions.deleteSavedMeal(meal) },
                )
            }
        }
    }
}

private val SliceRadius = 24.dp
private val SliceFirst = RoundedCornerShape(topStart = SliceRadius, topEnd = SliceRadius)
private val SliceLast = RoundedCornerShape(bottomStart = SliceRadius, bottomEnd = SliceRadius)
private val SliceOnly = RoundedCornerShape(SliceRadius)

/** How far a slice's shadow box runs on into its neighbours: well beyond the card shadow's 14 dp blur and 4 dp drop. */
private val SliceShadowOverrun = 40.dp

/**
 * One row of an inset group (the InsetGroup look: surface card, radius 24, the card shadow in light,
 * a hairline 66 dp in over every row but the first) drawn as its own lazy item, so the slices stack
 * into one card. The page's list puts 12 dp between items: a slice with a row after it gives that
 * gap back (it reports 12 dp less height and the next slice starts where it ends).
 */
@Composable
private fun GroupSlice(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val c = Ember.colors
    val shape = when {
        first && last -> SliceOnly
        first -> SliceFirst
        last -> SliceLast
        else -> RectangleShape
    }
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val giveBack = if (last) 0 else EmberSpace.StackGap.roundToPx()
                layout(p.width, (p.height - giveBack).coerceAtLeast(0)) { p.place(0, 0) }
            },
    ) {
        SliceShadow(first, last)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .then(
                    if (first) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            drawContent()
                            // The divider lies over the row's top edge, as in InsetGroup (mirrored in RTL).
                            val s = 66.dp.toPx().coerceAtMost(size.width)
                            val left = if (layoutDirection == LayoutDirection.Rtl) 0f else s
                            val right = if (layoutDirection == LayoutDirection.Rtl) size.width - s else size.width
                            drawRect(c.sep, Offset(left, 0f), Size(right - left, .5.dp.roundToPx().coerceAtLeast(1).toFloat()))
                        }
                    },
                ),
        ) { content() }
    }
}

/**
 * A slice's share of the card shadow. It draws the shadow of a card that runs on [SliceShadowOverrun]
 * past each neighbour (the real card's ends on the first and last slice) and keeps only the band
 * beside its own slice (plus everything above the first and below the last). The bands add up to one
 * long card's shadow: no seams, no step at each row, nothing drawn over a neighbour.
 */
@Composable
private fun BoxScope.SliceShadow(first: Boolean, last: Boolean) {
    Box(
        Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val above = if (first) 0 else SliceShadowOverrun.roundToPx()
                val below = if (last) 0 else SliceShadowOverrun.roundToPx()
                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + above + below))
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, -above) }
            }
            .drawWithContent {
                val above = if (first) 0f else SliceShadowOverrun.toPx()
                val below = if (last) 0f else SliceShadowOverrun.toPx()
                val far = size.height + SliceShadowOverrun.toPx()
                clipRect(
                    left = -far,
                    top = if (first) -far else above,
                    right = size.width + far,
                    bottom = if (last) size.height + far else size.height - below,
                ) { this@drawWithContent.drawContent() }
            }
            .emberShadow(Elevation.Card, SliceOnly, Ember.colors.isDark),
    )
}

/**
 * A list's cascade (S1): open while the list is new. Rows composed in that moment fade up in turn;
 * rows composed later, as the list scrolls, simply appear.
 */
private class CascadeWindow {
    var open = true
}

/** Closes [window] once its cascade has had time to start (the 12th row starts at 480 ms). */
@Composable
private fun CascadeClock(window: CascadeWindow) {
    LaunchedEffect(window) {
        motionDelay(600)
        window.open = false
    }
}

/** Fades [content] up 8 dp, [index] × 40 ms after its list appears (S1); at rest when it shows up later. */
@Composable
private fun Cascade(index: Int, window: CascadeWindow, content: @Composable () -> Unit) {
    val reduced = Ember.motion.reduced
    val p = remember(window) { Animatable(if (reduced || !window.open) 1f else 0f) }
    LaunchedEffect(window) {
        if (p.value < 1f) p.animateTo(1f, tween(420, 40 * index.coerceAtMost(12), EmberEasing.Out))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = 8.dp.toPx() * (1f - p.value)
        },
    ) { content() }
}

/** The 40 dp glyph tile of a result: a bottle for drinks, a leaf for the user's own foods, a bowl otherwise. */
@Composable
private fun GlyphTile(icon: EmberIcons) {
    val c = Ember.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(EmberShapes.glyph)
            .background(c.fill2),
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(icon, null, size = 22.dp, tint = c.label)
    }
}

private fun glyphFor(product: FoodProductEntity): EmberIcons = when {
    product.isLiquid -> EmberIcons.Bottle
    product.source == ProductSource.LOCAL -> EmberIcons.Leaf
    else -> EmberIcons.Bowl
}

/** "Dairy farm · 68 kcal / 100 g" (or "/ 100 ml" for a drink). */
@Composable
private fun productLine(product: FoodProductEntity): String {
    val kcal = stringResource(
        if (product.isLiquid) R.string.kcal_per_100ml else R.string.kcal_per_100g,
        product.kcalPer100g.roundToInt(),
    )
    // "238 kcal / 100 g" never breaks inside itself; the brand before it may wrap.
    val whole = kcal.replace(' ', '\u00A0')
    return product.brand?.takeIf { it.isNotBlank() }?.let { "$it · $whole" } ?: whole
}

/**
 * One product: tap = the amount sheet. The star (a toggle with its state), the pencil for the
 * user's own products, and the 36 dp "+" that collects it into the basket (a check once in).
 */
@Composable
private fun ProductRow(
    product: FoodProductEntity,
    inBasket: Boolean,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphTile(glyphFor(product))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                product.name,
                style = Ember.type.callout.copy(fontWeight = FontWeight.Medium),
                color = { c.label },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                productLine(product),
                style = Ember.type.footnote,
                color = { c.label2 },
                // Large text: brand and "78 kcal / 100 ml" take a line each and still end in full.
                maxLines = if (LocalDensity.current.fontScale >= 1.5f) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FavoriteStar(on = product.isFavorite, onToggle = onToggleFavorite)
            // Only the user's own products are editable — OFF and shop data are read-only.
            if (product.source == ProductSource.LOCAL) {
                EmberIconButton(
                    icon = EmberIcons.Pencil,
                    contentDescription = stringResource(R.string.custom_food_edit_title),
                    onClick = onEdit,
                    style = IconButtonStyle.Plain,
                    size = 36.dp,
                )
            }
            // Quick multi-add: "+" collects into the basket, a check marks "already in".
            BasketToggle(inBasket = inBasket, onAdd = onQuickAdd)
        }
    }
}

/**
 * The 36 dp "+" that collects a product into the basket (48 dp to the finger). Once in, it morphs
 * into the Ember disc with a white check (the bouncy pop) and is disabled, named "In basket".
 */
@Composable
private fun BasketToggle(inBasket: Boolean, onAdd: () -> Unit) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val reduced = Ember.motion.reduced
    // 0 = the "+" on its fill disc … 1 = the check on the Ember disc.
    val p = remember { Animatable(if (inBasket) 1f else 0f) }
    LaunchedEffect(inBasket, reduced) {
        val to = if (inBasket) 1f else 0f
        if (reduced) p.snapTo(to) else p.animateTo(to, if (inBasket) EmberSprings.bouncy() else EmberSprings.snappy())
    }
    val brush = EmberBrushes.emberIcon(c)
    val inLabel = stringResource(R.string.basket_in)
    val addLabel = stringResource(R.string.basket_quick_add)
    // 48 dp to the finger round a 36 dp disc.
    Box(
        Modifier
            .size(48.dp)
            .clip(EmberShapes.circle)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.circle, c.focus),
                enabled = !inBasket,
                role = Role.Button,
                onClick = onAdd,
            )
            .semantics { contentDescription = if (inBasket) inLabel else addLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pressScale(source, .9f)
                .size(36.dp)
                .drawBehind {
                    drawCircle(c.fill)
                    val q = p.value
                    if (q > 0f) drawCircle(brush, radius = size.minDimension / 2f * q.coerceAtMost(1.04f))
                },
            contentAlignment = Alignment.Center,
        ) {
            val q = p.value.coerceIn(0f, 1f)
            EmberIcon(
                EmberIcons.Plus, null,
                Modifier.graphicsLayer {
                    alpha = 1f - q
                    rotationZ = 90f * q
                },
                size = 18.dp,
                tint = c.label,
            )
            EmberIcon(
                EmberIcons.Check, null,
                Modifier.graphicsLayer {
                    alpha = q
                    scaleX = .5f + .5f * p.value
                    scaleY = .5f + .5f * p.value
                },
                size = 18.dp,
                tint = c.onAccent,
                strokeWidth = 2.8f,
            )
        }
    }
}

/**
 * The favourite star: a toggle named "Favorite" that says its state ("In favorites" / "Not in
 * favorites"); filled tint when on, a quiet outline when off. Turning it on gives it one small pop.
 */
@Composable
private fun FavoriteStar(on: Boolean, onToggle: () -> Unit) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val name = stringResource(R.string.favorite_toggle)
    val state = stringResource(if (on) R.string.favorite_on else R.string.favorite_off)
    val tint by animateColorAsState(
        if (on) c.tint else c.label3, emberSpec(tween(EmberDurations.State, easing = EmberEasing.Out)), label = "star",
    )
    val pop = remember { Animatable(1f) }
    val was = remember { booleanArrayOf(on) }
    val reduced = Ember.motion.reduced
    LaunchedEffect(on) {
        if (on && !was[0] && !reduced) {
            pop.snapTo(.7f)
            pop.animateTo(1f, EmberSprings.bouncy())
        }
        was[0] = on
    }
    // 48 dp to the finger round the 20 dp glyph.
    Box(
        Modifier
            .size(48.dp)
            .pressScale(source, .9f)
            .clip(EmberShapes.circle)
            .toggleable(
                value = on,
                interactionSource = source,
                indication = EmberIndication(EmberShapes.circle, c.focus),
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics {
                contentDescription = name
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        EmberIcon(
            EmberIcons.Star, null,
            Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
            size = 20.dp,
            tint = tint,
            filled = on,
        )
    }
}

/** One saved meal: tap = log everything into this meal and day at once; the bin deletes it. */
@Composable
private fun SavedMealRow(
    meal: SavedMealSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphTile(EmberIcons.Bookmark)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(meal.name, style = Ember.type.callout.copy(fontWeight = FontWeight.Medium), color = { c.label })
            BasicText(
                stringResource(R.string.saved_meal_summary, meal.itemCount, meal.totalKcal.roundToInt()),
                style = Ember.type.footnote,
                color = { c.label2 },
            )
        }
        EmberIconButton(
            icon = EmberIcons.Trash,
            contentDescription = stringResource(R.string.saved_meal_delete),
            onClick = onDelete,
            style = IconButtonStyle.Plain,
            size = 40.dp,
        )
    }
}

/** A number no basket will ever hold, to find where the count sits inside "Basket: %1$d — review and add". */
private const val CountMark = 7_654_321

/**
 * The docked basket bar: an ink capsule "Basket: 3 — review and add" whose count hands off digit by
 * digit as products are collected. At large text sizes it is a plain wrapping button.
 */
@Composable
private fun BasketBar(count: Int, onClick: () -> Unit) {
    val c = Ember.colors
    val label = stringResource(R.string.basket_button, count)
    val template = stringResource(R.string.basket_button, CountMark)
    val mark = template.indexOf(CountMark.toString())
    val shadow = Modifier.fillMaxWidth().emberShadow(Elevation.Float, EmberShapes.capsule, c.isDark)
    if (mark < 0 || LocalDensity.current.fontScale >= 1.5f) {
        EmberButton(label, onClick, shadow, size = ButtonSize.Lg, icon = EmberIcons.Basket)
        return
    }
    val before = template.substring(0, mark).trimEnd()
    val after = template.substring(mark + CountMark.toString().length).trimStart()
    val source = remember { MutableInteractionSource() }
    val style = Ember.type.headline
    Row(
        shadow
            .pressScale(source, .96f)
            .heightIn(min = 54.dp)
            .clip(EmberShapes.capsule)
            .background(c.ink)
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                role = Role.Button,
                onClick = onClick,
            )
            // One stop named by the whole label; the clickable before it keeps the click and the role.
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmberIcon(EmberIcons.Basket, null, size = 20.dp, tint = c.onInk)
        if (before.isNotEmpty()) ControlText(before, style = style, color = c.onInk, maxLines = 1)
        Digits(count.toLong(), style.copy(fontWeight = FontWeight.Bold), color = c.onInk)
        if (after.isNotEmpty()) {
            ControlText(
                after,
                Modifier.weight(1f, fill = false),
                style = style,
                color = c.onInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The amount sheet
// ---------------------------------------------------------------------------------------------

/**
 * Asks how much was eaten: in grams (ml for a drink), or in portions when the product states a
 * portion size. The kcal for the typed amount is live (digits hand off as you type or step); with
 * the day's budget, the preview ring grows a raspberry "this food" arc and says what is left after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(
    product: FoodProductEntity,
    budget: DayBudget?,
    onConfirm: (grams: Double, servings: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ember.colors
    val locale = LocalConfiguration.current.locales[0]
    // rememberSaveable: the typed amount must survive screen rotation.
    var usePortions by rememberSaveable(product.id) { mutableStateOf(false) }
    var amountText by rememberSaveable(product.id) { mutableStateOf("100") }

    val serving = product.servingSizeG
    val amount = amountText.replace(',', '.').toDoubleOrNull()
    // The grams behind the typed amount (portions get converted).
    val grams = when {
        amount == null || amount <= 0.0 -> null
        usePortions && serving != null -> FoodMath.servingsToGrams(amount, serving)
        else -> amount
    }?.takeIf { it <= 5000.0 } // sanity limit
    val nutrition = grams?.let {
        FoodMath.forGrams(product.kcalPer100g, product.proteinPer100g, product.fatPer100g, product.carbsPer100g, it)
    }

    val gUnit = stringResource(R.string.diary_unit_g)
    val mlUnit = stringResource(R.string.ml_unit)
    val massUnit = if (product.isLiquid) mlUnit else gUnit
    val massMode = stringResource(if (product.isLiquid) R.string.ml_mode else R.string.grams_mode)
    val portionsMode = stringResource(R.string.portions_mode)

    EmberSheet(onDismissRequest = onDismiss, paneTitle = product.name) {
        SheetHeader(
            title = product.name,
            subtitle = productLine(product),
            glyph = glyphFor(product),
            onClose = onDismiss,
            closeLabel = stringResource(R.string.cancel),
        )
        AmountHero(kcal = nutrition?.kcal?.roundToInt(), budget = budget)

        val step = if (usePortions) .5 else 10.0
        AmountStepper(
            text = amountText,
            onTextChange = { amountText = it },
            unit = if (usePortions) pluralStringResource(R.plurals.diary_unit_portions, pluralCount(amount)) else massUnit,
            onDecrease = {
                val now = amount ?: return@AmountStepper
                // Never steps below one step: the amount stays loggable.
                amountText = formatAmount(if (now - step >= step) now - step else minOf(now, step), locale)
            },
            onIncrease = { amountText = formatAmount(((amount ?: 0.0) + step).coerceAtMost(5000.0), locale) },
            fieldLabel = if (usePortions) portionsMode else massMode,
            decreaseLabel = stringResource(R.string.amount_less),
            increaseLabel = stringResource(R.string.amount_more),
            // Grams (or ml) ↔ portions, starting over at 100 / 1, as the old unit chips did.
            onSwitchUnit = serving?.let {
                {
                    usePortions = !usePortions
                    amountText = if (usePortions) "1" else "100"
                }
            },
            switchUnitLabel = if (usePortions) massMode else portionsMode,
            isError = amountText.isNotBlank() && grams == null,
        )

        if (serving != null) {
            val format = rememberIntegerFormat()
            val portions = listOf(1, 2).map { n ->
                pluralStringResource(R.plurals.portion_chip, n, n.toString(), format.format((serving * n).roundToInt()), massUnit)
            }
            val selected = when {
                !usePortions && amount == 100.0 -> setOf(0)
                usePortions && amount == 1.0 -> setOf(1)
                usePortions && amount == 2.0 -> setOf(2)
                else -> emptySet()
            }
            ChipGroup(
                options = listOf("100 $massUnit") + portions,
                selected = selected,
                onToggle = { i ->
                    usePortions = i > 0
                    amountText = if (i == 0) "100" else i.toString()
                },
            )
            if (usePortions) {
                ControlText(
                    stringResource(
                        if (product.isLiquid) R.string.portion_size_note_ml else R.string.portion_size_note,
                        serving.roundToInt(),
                    ),
                    Modifier.padding(start = 4.dp),
                    style = Ember.type.footnote,
                    color = c.label2,
                )
            }
        }

        Hairline()
        // For a valid amount the eaten macros; otherwise the label's own split.
        val shown = nutrition ?: FoodMath.forGrams(product.kcalPer100g, product.proteinPer100g, product.fatPer100g, product.carbsPer100g, 100.0)
        CompositionBar(
            proteinKcal = (shown.proteinG * 4).toFloat(),
            fatKcal = (shown.fatG * 9).toFloat(),
            carbsKcal = (shown.carbsG * 4).toFloat(),
        )
        MacroColumns(nutrition, gUnit)

        Per100(product)

        product.additivesCsv?.let { csv ->
            ControlText(
                stringResource(R.string.additives_line, csv.replace(",", ", ")),
                Modifier.padding(horizontal = 4.dp),
                style = Ember.type.footnote,
                color = c.label2,
            )
        }
        // A shop's catalogue is a less checked source than a label: say where these numbers come from.
        if (product.source == ProductSource.ZAKAZ) {
            ControlText(
                stringResource(R.string.product_source_zakaz),
                Modifier.padding(horizontal = 4.dp),
                style = Ember.type.footnote,
                color = c.label2,
            )
        }
        EmberButton(
            text = stringResource(R.string.add_action),
            onClick = { if (grams != null) onConfirm(grams, if (usePortions) amount else null) },
            modifier = Modifier.padding(top = 4.dp),
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
            enabled = grams != null,
        )
    }
}

/** The plural count for an amount: whole amounts as they are, fractions as "other" ("1.5 portions"). */
private fun pluralCount(amount: Double?): Int =
    if (amount != null && amount % 1.0 == 0.0 && amount in 0.0..1_000_000.0) amount.toInt() else 2

/**
 * A stepped amount as the user would type it: "110", "1.5" ("1,5" in Ukrainian). Always ASCII
 * digits, whatever the language's own digits are ("۱۱۰" in Persian), and only "." or "," before the
 * fraction: the field is read back with toDouble, which knows no others.
 */
internal fun formatAmount(value: Double, locale: Locale): String {
    val comma = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply { decimalSeparator = if (comma) ',' else '.' }
    return DecimalFormat("0.##", symbols).format(value)
}

/**
 * The sheet's hero: the preview ring (today so far, muted, plus the raspberry arc of this food, with
 * the resulting percentage inside) and the kcal in gradient digits, with "130 kcal left after this".
 * Without a budget only the kcal. An invalid amount shows a dash.
 */
@Composable
private fun AmountHero(kcal: Int?, budget: DayBudget?) {
    val c = Ember.colors
    val format = rememberIntegerFormat()
    val locale = LocalConfiguration.current.locales[0]
    // The percentage and "left after this" follow the kcal the digits show, which swap a moment
    // after the amount changes, so "830 left" never reads beside the next amount's number. When the
    // kcal comes back from a dash the digits start over without a hand-off, and so does this.
    val shownKcal = key(kcal == null) { rememberCaptionValue(kcal) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (budget != null && budget.targetKcal > 0) {
            val after = budget.eatenKcal + (shownKcal ?: 0)
            val percent = remember(locale, after, budget.targetKcal) {
                NumberFormat.getPercentInstance(locale).format(after.toDouble() / budget.targetKcal)
            }
            Ring(
                value = budget.eatenKcal.toFloat(),
                target = budget.targetKcal.toFloat(),
                size = RingSizes.Sheet.size,
                stroke = RingSizes.Sheet.stroke,
                add = kcal?.toFloat(),
                muted = true,
                contentDescription = stringResource(R.string.diary_ring_after_cd, percent),
            ) {
                BasicText(
                    percent,
                    style = Ember.type.kpi.copy(fontSize = 17.sp, textAlign = TextAlign.Center),
                    color = { c.label },
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp),
                    modifier = Modifier.width(RingSizes.Sheet.size - RingSizes.Sheet.stroke * 2 - 8.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kcal != null) {
                FittedNumber(
                    value = kcal.toLong(),
                    numberStyle = Ember.type.display,
                    unit = stringResource(R.string.kcal_unit),
                    minFontSize = 30.sp,
                    gradient = true,
                    unitSize = 20.sp,
                )
            } else {
                BasicText(stringResource(R.string.no_data_dash), style = Ember.type.display, color = { c.label3 })
            }
            if (budget != null && kcal != null && shownKcal != null) {
                val left = budget.remainingKcal - shownKcal
                val number = format.format(abs(left))
                val text = stringResource(if (left >= 0) R.string.add_left_after else R.string.add_over_after, number)
                BasicText(
                    emphasise(text, number, c.label),
                    style = Ember.type.subhead.copy(fontWeight = FontWeight.Normal),
                    color = { c.label2 },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** [text] with the first occurrence of [part] in bold [color] ("**130** kcal left after this"). */
private fun emphasise(text: String, part: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val at = text.indexOf(part)
    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), at, at + part.length)
}

/** A 0.5 dp separator across a sheet. */
@Composable
private fun Hairline() {
    val color = Ember.colors.sep
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 1.dp)
            .drawBehind { drawRect(color, Offset.Zero, Size(size.width, .5.dp.toPx())) },
    )
}

/**
 * Protein / fat / carbs of the chosen amount: a metric dot, the name and the grams ("—" without a
 * valid amount). Three columns; at large text sizes one row each (name left, grams right), so a
 * long name such as "Вуглеводи" is never cut.
 */
@Composable
private fun MacroColumns(nutrition: FoodMath.Nutrition?, gUnit: String) {
    val c = Ember.colors
    val decimal = rememberDecimalFormat(1)
    val dash = stringResource(R.string.no_data_dash)
    val items = listOf(
        Triple(R.string.summary_protein, c.protein, nutrition?.proteinG),
        Triple(R.string.summary_fat, c.fat, nutrition?.fatG),
        Triple(R.string.summary_carbs, c.carbs, nutrition?.carbsG),
    )
    val labelStyle = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2)
    val value: @Composable (Double?) -> Unit = { grams ->
        if (grams != null) {
            NumberWithUnit(grams, gUnit, Ember.type.kpi, format = decimal)
        } else {
            ControlText(dash, style = Ember.type.kpi, color = c.label3)
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (label, color, grams) ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(color, EmberShapes.circle))
                    ControlText(stringResource(label), Modifier.weight(1f), style = labelStyle)
                    value(grams)
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (label, color, grams) ->
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(color, EmberShapes.circle))
                    // A third of the sheet: a long name steps down a little rather than lose its end.
                    WholeWordsText(stringResource(label), labelStyle)
                }
                value(grams)
            }
        }
    }
}

/**
 * The full label per 100 g (or ml): seven nutrients in two columns ("—" = the source didn't state
 * it, never 0); one column at large text sizes.
 */
@Composable
private fun Per100(product: FoodProductEntity) {
    val c = Ember.colors
    val rows = listOf(
        R.string.summary_protein to product.proteinPer100g,
        R.string.summary_fat to product.fatPer100g,
        R.string.summary_carbs to product.carbsPer100g,
        R.string.nutrient_fiber to product.fiberPer100g,
        R.string.nutrient_sugars to product.sugarsPer100g,
        R.string.nutrient_salt to product.saltPer100g,
        R.string.nutrient_sat_fat to product.saturatedFatPer100g,
    )
    val columns = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ControlText(
            stringResource(if (product.isLiquid) R.string.per100ml_header else R.string.per100g_header),
            style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = c.label2,
        )
        rows.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                line.forEach { (label, value) -> NutrientRow(label, value, Modifier.weight(1f)) }
                if (line.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One label row of the per-100 table; null renders as a dash, never 0. */
@Composable
private fun NutrientRow(@StringRes labelRes: Int, valuePer100g: Double?, modifier: Modifier) {
    val c = Ember.colors
    Row(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlText(stringResource(labelRes), Modifier.weight(1f), style = Ember.type.footnote, color = c.label2)
        ControlText(
            valuePer100g?.let { stringResource(R.string.nutrient_grams_value, it) } ?: stringResource(R.string.no_data_dash),
            style = Ember.type.footnote.copy(fontFeatureSettings = "tnum"),
            color = c.label,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The custom food sheet
// ---------------------------------------------------------------------------------------------

/**
 * Create/edit form for a user-defined product. All numbers are per 100 g, like on any nutrition
 * label. Save stays disabled until the input is sane; a field holding something out of range gets
 * the danger ring.
 *
 * Opened from a scan, the form also says which barcode the product will be found under next time,
 * and — when Open Food Facts or a Ukrainian shop knew the product only partly — starts with
 * everything it knew filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodSheet(
    target: CustomFormTarget,
    onSave: (CustomFoodDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = Ember.colors
    val editing = target.editing
    val prefill = target.prefill
    // rememberSaveable: typed values survive rotation, like the amount sheet. Keyed by the target:
    // reopened for another product or barcode, the fields start over instead of showing the
    // previous one's values.
    var name by rememberSaveable(target) { mutableStateOf(editing?.name ?: prefill?.name ?: "") }
    var brand by rememberSaveable(target) { mutableStateOf(editing?.brand ?: prefill?.brand ?: "") }
    var kcalText by rememberSaveable(target) {
        mutableStateOf(editing?.kcalPer100g?.roundToInt()?.toString() ?: prefillText(prefill?.kcalPer100g, decimals = 0))
    }
    var proteinText by rememberSaveable(target) {
        mutableStateOf(editing?.proteinPer100g?.toString() ?: prefillText(prefill?.proteinPer100g))
    }
    var fatText by rememberSaveable(target) {
        mutableStateOf(editing?.fatPer100g?.toString() ?: prefillText(prefill?.fatPer100g))
    }
    var carbsText by rememberSaveable(target) {
        mutableStateOf(editing?.carbsPer100g?.toString() ?: prefillText(prefill?.carbsPer100g))
    }
    var servingText by rememberSaveable(target) {
        mutableStateOf(editing?.servingSizeG?.roundToInt()?.toString() ?: prefillText(prefill?.servingSizeG, decimals = 0))
    }
    var fiberText by rememberSaveable(target) {
        mutableStateOf(editing?.fiberPer100g?.toString() ?: prefillText(prefill?.fiberPer100g))
    }
    var sugarsText by rememberSaveable(target) {
        mutableStateOf(editing?.sugarsPer100g?.toString() ?: prefillText(prefill?.sugarsPer100g))
    }
    var saltText by rememberSaveable(target) {
        mutableStateOf(editing?.saltPer100g?.toString() ?: prefillText(prefill?.saltPer100g))
    }
    var satFatText by rememberSaveable(target) {
        mutableStateOf(editing?.saturatedFatPer100g?.toString() ?: prefillText(prefill?.saturatedFatPer100g))
    }
    // A drink is typed and shown in ml; the per-100 values stay as they are.
    var isLiquid by rememberSaveable(target) { mutableStateOf(target.startsAsLiquid) }

    fun parse(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
    // Optional field: blank is fine (null), a typed value must be sane.
    fun optional(text: String): Double? =
        if (text.isBlank()) null else parse(text)?.takeIf { it in 0.0..100.0 }
    fun optionalOk(text: String): Boolean = text.isBlank() || optional(text) != null
    // A typed value out of range gets the danger ring; an empty required field only keeps Save off.
    fun bad(text: String, ok: Boolean): Boolean = text.isNotBlank() && !ok

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

    val title = stringResource(if (editing == null) R.string.custom_food_new_title else R.string.custom_food_edit_title)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
        // No close button: the footer's Cancel and Save are the way out, as on the web and in the
        // Diary's footer sheets (drag, the scrim and back still dismiss).
        SheetHeader(title = title, glyph = EmberIcons.Leaf)
        val note = prefill?.let { stringResource(prefillNote(it, target.prefillFromShop)) }
        val code = target.barcode?.let { barcodeLine(it) }
        if (note != null || code != null) {
            Notice(
                tone = NoticeTone.Info,
                title = note ?: code.orEmpty(),
                detail = if (note != null) code else null,
                nested = true,
                icon = if (note == null) EmberIcons.Scan else null,
            )
        }
        EmberTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.custom_food_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        EmberTextField(
            value = brand,
            onValueChange = { brand = it },
            label = stringResource(R.string.custom_food_brand),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        // The drink checkbox is a switch row on a soft block, as on the web.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(EmberShapes.nestedSmall)
                .background(c.surface2),
        ) {
            SwitchRow(
                title = stringResource(R.string.custom_food_liquid),
                checked = isLiquid,
                onCheckedChange = { isLiquid = it },
            )
        }
        FieldPair(
            { NumberField(kcalText, { kcalText = it }, R.string.custom_food_kcal, bad(kcalText, kcal != null), it) },
            { NumberField(proteinText, { proteinText = it }, R.string.custom_food_protein, bad(proteinText, protein != null), it) },
        )
        FieldPair(
            { NumberField(fatText, { fatText = it }, R.string.custom_food_fat, bad(fatText, fat != null), it) },
            { NumberField(carbsText, { carbsText = it }, R.string.custom_food_carbs, bad(carbsText, carbs != null), it) },
        )
        NumberField(servingText, { servingText = it }, R.string.custom_food_serving, !servingOk, Modifier.fillMaxWidth())
        // The optional details: a quiet head in the sheet's own inset, not a list section head.
        ControlText(
            stringResource(R.string.custom_food_optional_header),
            Modifier
                .padding(start = 4.dp, top = 10.dp)
                .semantics { heading() },
            style = Ember.type.headline,
            color = c.label,
        )
        FieldPair(
            { NumberField(fiberText, { fiberText = it }, R.string.custom_food_fiber, !optionalOk(fiberText), it) },
            { NumberField(sugarsText, { sugarsText = it }, R.string.custom_food_sugars, !optionalOk(sugarsText), it) },
        )
        FieldPair(
            { NumberField(saltText, { saltText = it }, R.string.custom_food_salt, !optionalOk(saltText), it) },
            { NumberField(satFatText, { satFatText = it }, R.string.custom_food_sat_fat, !optionalOk(satFatText), it) },
        )
        SheetActions(
            confirmLabel = stringResource(R.string.save),
            confirmEnabled = valid,
            onCancel = onDismiss,
            onConfirm = {
                if (valid) {
                    onSave(
                        CustomFoodDraft(
                            name = name,
                            brand = brand.takeIf { it.isNotBlank() },
                            kcalPer100g = kcal,
                            proteinPer100g = protein,
                            fatPer100g = fat,
                            carbsPer100g = carbs,
                            servingSizeG = serving,
                            fiberPer100g = optional(fiberText),
                            sugarsPer100g = optional(sugarsText),
                            saltPer100g = optional(saltText),
                            saturatedFatPer100g = optional(satFatText),
                            isLiquid = isLiquid,
                        )
                    )
                }
            },
        )
        if (onDelete != null) {
            EmberButton(
                text = stringResource(R.string.custom_food_delete),
                onClick = onDelete,
                variant = ButtonVariant.Danger,
                size = ButtonSize.Lg,
                icon = EmberIcons.Trash,
            )
        }
    }
}

/** Two fields side by side; stacked at large text sizes. */
@Composable
private fun FieldPair(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            left(Modifier.fillMaxWidth())
            right(Modifier.fillMaxWidth())
        }
    } else {
        // Bottom-aligned: when one label wraps to two lines, the two wells still sit side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            left(Modifier.weight(1f))
            right(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, @StringRes labelRes: Int, isError: Boolean, modifier: Modifier) {
    EmberTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(labelRes),
        modifier = modifier,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

// ---------------------------------------------------------------------------------------------
// The basket sheet
// ---------------------------------------------------------------------------------------------

/** The multi-add review: one line per product with editable grams, the total, "Add all". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasketSheet(
    items: List<BasketItem>,
    subtitle: String,
    onGramsChange: (productId: String, text: String) -> Unit,
    onRemove: (productId: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ember.colors
    val allValid = items.isNotEmpty() && items.all { basketGrams(it) != null }
    val totalKcal = items.sumOf { item ->
        basketGrams(item)?.let { g -> item.product.kcalPer100g * g / 100.0 } ?: 0.0
    }.roundToInt()
    val title = stringResource(R.string.basket_title)
    val gUnit = stringResource(R.string.diary_unit_g)
    val mlUnit = stringResource(R.string.ml_unit)
    EmberSheet(onDismissRequest = onDismiss, paneTitle = title) {
        SheetHeader(title = title, subtitle = subtitle, onClose = onDismiss, closeLabel = stringResource(R.string.cancel))
        Column {
            items.forEachIndexed { i, item ->
                val grams = basketGrams(item)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i > 0) Modifier.drawBehind { drawRect(c.sep, Offset.Zero, Size(size.width, .5.dp.toPx())) } else Modifier)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        BasicText(item.product.name, style = Ember.type.callout, color = { c.label }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (grams != null) {
                            BasicText(
                                stringResource(R.string.kcal_value, (item.product.kcalPer100g * grams / 100.0).roundToInt()),
                                style = Ember.type.footnote,
                                color = { c.label2 },
                            )
                        }
                    }
                    GramsWell(
                        text = item.gramsText,
                        onChange = { onGramsChange(item.product.id, it) },
                        unit = if (item.product.isLiquid) mlUnit else gUnit,
                        label = "${stringResource(if (item.product.isLiquid) R.string.ml_mode else R.string.grams_mode)}, ${item.product.name}",
                        isError = grams == null,
                    )
                    EmberIconButton(
                        icon = EmberIcons.Minus,
                        contentDescription = stringResource(R.string.delete),
                        onClick = { onRemove(item.product.id) },
                        style = IconButtonStyle.Plain,
                        size = 40.dp,
                    )
                }
            }
        }
        val totalLine = stringResource(R.string.basket_total, totalKcal)
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(c.sep, Offset.Zero, Size(size.width, .5.dp.toPx())) }
                .clearAndSetSemantics { contentDescription = totalLine }
                .padding(start = 2.dp, end = 2.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlText(
                stringResource(R.string.day_total),
                Modifier.weight(1f),
                style = Ember.type.subhead,
                color = c.label2,
            )
            NumberWithUnit(
                value = totalKcal.toLong(),
                unit = stringResource(R.string.kcal_unit),
                numberStyle = Ember.type.stat.copy(fontSize = 30.sp),
                gradient = true,
            )
        }
        EmberButton(
            text = stringResource(R.string.basket_add_all),
            onClick = onConfirm,
            modifier = Modifier.padding(top = 4.dp),
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
            enabled = allValid,
        )
    }
}

/**
 * A basket line's amount: a compact well (96 dp, wider at large text sizes) with the number on the
 * right and its unit, named [label] for TalkBack; a danger ring while it is not a loggable amount.
 */
@Composable
private fun GramsWell(text: String, onChange: (String) -> Unit, unit: String, label: String, isError: Boolean) {
    val c = Ember.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ring = when {
        isError -> c.danger
        focused -> c.focus
        else -> c.sep
    }
    val width = if (LocalDensity.current.fontScale >= 1.5f) 136.dp else 96.dp
    BasicTextField(
        value = text,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        textStyle = Ember.type.rowNumber.copy(color = c.label, textAlign = TextAlign.End),
        cursorBrush = SolidColor(c.tint),
        interactionSource = source,
        modifier = Modifier
            .widthIn(min = width, max = width)
            .semantics {
                contentDescription = label
                if (isError) error(label)
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(EmberShapes.field)
                    .background(if (focused) c.surface else c.fill2)
                    .border(if (isError || focused) 2.dp else 1.dp, ring, EmberShapes.field)
                    .padding(start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.weight(1f)) { inner() }
                ControlText(unit, style = Ember.type.callout, color = c.label2, maxLines = 1, softWrap = false)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Texts
// ---------------------------------------------------------------------------------------------

/**
 * The line above a prefilled form. Never credits Open Food Facts with a
 * shop's data. With all four values filled in, nothing is missing: those
 * numbers are shop values that don't add up, or OFF's estimates, and the
 * user is asked to check them against the label, not to fill gaps (the
 * form would otherwise save them as the user's own with one tap).
 */
@StringRes
private fun prefillNote(prefill: ProductPrefill, fromShop: Boolean): Int = when {
    fromShop && prefill.hasCoreValues -> R.string.barcode_incomplete_shop_check
    fromShop -> R.string.barcode_incomplete_shop
    prefill.estimated && prefill.hasCoreValues -> R.string.barcode_incomplete_estimated
    else -> R.string.barcode_incomplete
}

/**
 * The toast text for a scan that found nothing. Each one names only the
 * sources that answered; one that didn't is reported as unreachable.
 */
@StringRes
private fun noticeText(notice: LookupNotice): Int = when (notice) {
    LookupNotice.OFF_DOWN -> R.string.barcode_off_down
    LookupNotice.OFF_DOWN_SHOPS_MISS -> R.string.barcode_off_down_shops_miss
    LookupNotice.NOT_FOUND_UKRAINE -> R.string.barcode_not_found_ukraine
    LookupNotice.NOT_FOUND_BELARUS -> R.string.barcode_not_found_belarus
    LookupNotice.NOT_FOUND_OTHER -> R.string.barcode_not_found_other
    LookupNotice.NOT_FOUND_UKRAINE_SHOPS -> R.string.barcode_not_found_ukraine_shops
    LookupNotice.NOT_FOUND_OTHER_SHOPS -> R.string.barcode_not_found_other_shops
    LookupNotice.NOT_FOUND_SHOPS_DOWN -> R.string.barcode_not_found_shops_down
}

/** "Barcode 4820024700016 (Ukraine)": the code a scanned product is saved under. */
@Composable
private fun barcodeLine(barcode: String): String = when (BarcodeOrigin.countryOf(barcode)) {
    BarcodeCountry.UKRAINE -> stringResource(R.string.barcode_line_ukraine, barcode)
    BarcodeCountry.BELARUS -> stringResource(R.string.barcode_line_belarus, barcode)
    null -> stringResource(R.string.barcode_line, barcode)
}

/**
 * An Open Food Facts value as the user would type it: at most [decimals]
 * decimals, no trailing zeros ("13.33", "8", "240"); unknown -> blank field.
 * A per-serving value rescaled to 100 g would otherwise show as
 * "13.333333333333334".
 */
private fun prefillText(value: Double?, decimals: Int = 2): String {
    if (value == null || !value.isFinite()) return ""
    return BigDecimal.valueOf(value)
        .setScale(decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
