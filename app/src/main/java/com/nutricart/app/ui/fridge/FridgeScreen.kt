package com.nutricart.app.ui.fridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.ui.common.aisleLabel
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSpace
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.motionDelay
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.mealplan.GroupHead
import com.nutricart.app.ui.mealplan.currentLocale
import com.nutricart.app.ui.mealplan.motionSpec
import com.nutricart.app.ui.mealplan.splitHint
import com.nutricart.app.ui.shopping.LocalShoppingDock
import com.nutricart.app.ui.shopping.ShareListAction
import com.nutricart.app.ui.shopping.ShoppingBody
import com.nutricart.app.ui.shopping.ShoppingDock
import com.nutricart.app.ui.shopping.ShoppingDockedButton
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

/**
 * Groceries in both of their states: what I HAVE (in stock) and what I still
 * NEED (to buy). One tab, because it is one thing — the shopping list is where
 * the fridge gets filled from, and cooking is where it empties.
 */
@Composable
fun FridgeScreen(
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // The scope lives HERE, next to the host: a scope remembered inside the
    // swapped body dies with it, and switching halves would cut the toast
    // off mid-sentence. Both messages of the To-buy half confirm something done.
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(EmberToastVisuals(message, ToastIcon.Check)) }
    }

    FridgeContent(
        snackbarHostState = snackbarHostState,
        shareAction = { ShareListAction() },
        stockBody = { StockBody(onOpenRecipe = onOpenRecipe, onOpenSettings = onOpenSettings) },
        shoppingBody = { ShoppingBody(onMessage = showMessage) },
    )
}

/**
 * The stateless frame of [FridgeScreen]: the page, the In stock / To buy control
 * and whichever half is chosen. The halves come in as slots so screenshot
 * tests can pass [FridgeStockContent] / ShoppingContent with fake state.
 *
 * One Ember page for both halves: the large title with the segmented control under it (its thumb
 * slides; the header never rebuilds), and the chosen half below. Each half keeps its own scroll
 * position and its own saved state. On To buy the title carries Share, and the half's "Bought N"
 * docks above the tab bar.
 *
 * A half is composed beside the page's list, not inside it: there it collects its state, runs its
 * effects and holds its sheets, and it hands its rows to the list through [FridgeHalfList] as lazy
 * items (one per aisle), so a long list composes only the aisles on screen.
 */
@Composable
fun FridgeContent(
    snackbarHostState: SnackbarHostState,
    shareAction: @Composable () -> Unit,
    stockBody: @Composable () -> Unit,
    shoppingBody: @Composable () -> Unit,
    initialShowStock: Boolean = true,
) {
    var showStock by rememberSaveable { mutableStateOf(initialShowStock) }
    // One saved slot per half, so switching does not throw away the other one's state.
    val bodyState = rememberSaveableStateHolder()
    val stockList = rememberLazyListState()
    val shoppingList = rememberLazyListState()
    // One set of rows per half: a half shows only what it handed over itself, never the other's.
    val stockItems = remember { FridgeHalfItems() }
    val shoppingItems = remember { FridgeHalfItems() }
    val dock = remember { ShoppingDock() }
    val title = stringResource(R.string.fridge_title)

    CompositionLocalProvider(
        LocalShoppingDock provides dock,
        LocalFridgeHalf provides if (showStock) stockItems else shoppingItems,
    ) {
        bodyState.SaveableStateProvider(key = showStock) {
            if (showStock) stockBody() else shoppingBody()
        }
    }

    LargeTitleScaffold(
        title = title,
        // The share icon belongs to the list, so it only exists while the list is the thing on screen.
        actions = { if (!showStock) shareAction() },
        below = {
            SegmentedControl(
                options = listOf(stringResource(R.string.fridge_tab_stock), stringResource(R.string.fridge_tab_to_buy)),
                selectedIndex = if (showStock) 0 else 1,
                onSelect = { showStock = it == 0 },
                role = SegmentRole.Tab,
            )
        },
        compactTitle = title,
        toastHostState = snackbarHostState,
        dockedCta = if (showStock) null else { { ShoppingDockedButton(dock) } },
        listState = if (showStock) stockList else shoppingList,
    ) {
        (if (showStock) stockItems else shoppingItems).items(this)
    }
}

/** The rows one half of the fridge page puts into the page's list (see [FridgeHalfList]). */
@Stable
internal class FridgeHalfItems {
    var items: LazyListScope.() -> Unit by mutableStateOf({})
}

/** Provided by the fridge page around the half it shows; null elsewhere. */
internal val LocalFridgeHalf = staticCompositionLocalOf<FridgeHalfItems?> { null }

/**
 * Hands a half's rows to the fridge page's list as lazy items. Outside the page (nothing to hand
 * them to) they become a list of their own.
 */
@Composable
internal fun FridgeHalfList(items: LazyListScope.() -> Unit) {
    val half = LocalFridgeHalf.current
    if (half != null) {
        // Set while composing, like rememberUpdatedState: the list reads it only when it measures,
        // later in this same frame, so it shows the new rows at once instead of a frame behind.
        half.items = items
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(EmberSpace.StackGap)) { items() }
    }
}

@Composable
private fun StockBody(
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: FridgeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val pickable by viewModel.pickable.collectAsState()
    FridgeStockContent(
        state = state,
        aiState = aiState,
        pickable = pickable,
        onOpenRecipe = onOpenRecipe,
        onOpenSettings = onOpenSettings,
        onAskAi = viewModel::askAi,
        onDismissAi = viewModel::dismissAiAnswer,
        onAdd = viewModel::add,
        onSetGrams = viewModel::setGrams,
        onRemove = viewModel::remove,
    )
}

/**
 * The stateless "in stock" half; it owns only its add / edit sheets. Its rows go into the fridge
 * page's list ([FridgeHalfList]).
 *
 * "You could cook" from real stock, the assistant, "Add what you have", then the stock by aisle.
 * Something just added unfolds into its aisle with a short Ember glow.
 */
@Composable
fun FridgeStockContent(
    state: FridgeUiState,
    aiState: AiUiState,
    pickable: List<IngredientEntity>,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onOpenSettings: () -> Unit,
    onAskAi: () -> Unit,
    onDismissAi: () -> Unit,
    onAdd: (ingredient: IngredientEntity, grams: Double) -> Unit,
    onSetGrams: (item: FridgeItemUi, grams: Double) -> Unit,
    onRemove: (item: FridgeItemUi) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // The item being edited, by name (the stock's key); saved, so the sheet survives a rotation.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let { name -> state.itemsByAisle.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.name == name } } }
    // Gone from the fridge (cooked away, emptied elsewhere): nothing left to edit.
    LaunchedEffect(state.loading, editing == null) {
        if (!state.loading && editing == null) editingName = null
    }

    val first = rememberFirstOpen("fridge")
    val entrances = rememberEntranceState()

    // The names already on screen: a name that turns up later was just added and unfolds into its
    // aisle. Nothing is known until the stock has been listed once (nothing unfolds on the way in).
    val listed = !state.loading && state.itemCount > 0
    val names = remember(state.itemsByAisle) { state.itemsByAisle.values.flatten().mapTo(HashSet()) { it.name } }
    val known = remember { KnownNames() }
    val added: Set<String> = if (listed) known.names?.let { names - it }.orEmpty() else emptySet()
    SideEffect { known.names = if (listed) names else null }

    FridgeHalfList {
        when {
            state.loading -> item(key = "stock/loading", contentType = "stock/loading") { StockSkeleton() }
            state.itemCount == 0 -> {
                item(key = "stock/empty", contentType = "stock/empty") {
                    val (lead, rest) = splitHint(stringResource(R.string.fridge_empty_hint), currentLocale())
                    EmberCard(
                        Modifier.emberEntrance(0, EntranceKind.Rise, first, entrances, "empty"),
                        padding = PaddingValues(horizontal = 6.dp),
                    ) {
                        EmptyState(
                            title = lead,
                            icon = EmberIcons.Fridge,
                            body = rest,
                            action = {
                                EmberButton(
                                    text = stringResource(R.string.fridge_add_action),
                                    onClick = { showAdd = true },
                                    variant = ButtonVariant.Ink,
                                    icon = EmberIcons.Plus,
                                )
                            },
                        )
                    }
                }
                // Never spend the user's money on an empty fridge: the assistant waits for stock.
                item(key = "stock/ai", contentType = "stock/ai") {
                    FridgeAiBlock(
                        state = aiState,
                        canAsk = false,
                        onAsk = onAskAi,
                        onDismiss = onDismissAi,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.emberEntrance(1, EntranceKind.Rise, first, entrances, "ai"),
                    )
                }
            }
            else -> stockItems(
                state = state,
                aiState = aiState,
                added = added,
                first = first,
                entrances = entrances,
                onOpenRecipe = onOpenRecipe,
                onOpenSettings = onOpenSettings,
                onAskAi = onAskAi,
                onDismissAi = onDismissAi,
                onAddClick = { showAdd = true },
                onEdit = { editingName = it.name },
            )
        }
    }

    if (showAdd) {
        AddToFridgeDialog(
            ingredients = pickable,
            onConfirm = { ingredient, grams ->
                onAdd(ingredient, grams)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { item ->
        EditFridgeItemDialog(
            item = item,
            onConfirm = { grams ->
                onSetGrams(item, grams)
                editingName = null
            },
            onRemove = {
                onRemove(item)
                editingName = null
            },
            onDismiss = { editingName = null },
        )
    }
}

/** The names a list has shown so far; plain, so updating it never recomposes anything. */
private class KnownNames {
    var names: Set<String>? = null
}

/**
 * "You could cook" from real stock, the assistant, "Add what you have", then one item per aisle.
 * The page's 12 dp gap separates them, as the cards of the web's stack. A row whose name is in
 * [added] unfolds into its aisle.
 */
private fun LazyListScope.stockItems(
    state: FridgeUiState,
    aiState: AiUiState,
    added: Set<String>,
    first: Boolean,
    entrances: EntranceState,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    onOpenSettings: () -> Unit,
    onAskAi: () -> Unit,
    onDismissAi: () -> Unit,
    onAddClick: () -> Unit,
    onEdit: (FridgeItemUi) -> Unit,
) {
    var step = 0
    if (state.ideas.isNotEmpty()) {
        val index = step++
        item(key = "stock/ideas", contentType = "stock/ideas") {
            IdeasCard(
                ideas = state.ideas,
                onOpenRecipe = onOpenRecipe,
                modifier = Modifier.emberEntrance(index, EntranceKind.Rise, first, entrances, "ideas"),
            )
        }
    }
    // The assistant comes AFTER the ideas card on purpose: the local, free, offline answer is
    // the feature; the AI is the accelerator.
    val aiIndex = step++
    item(key = "stock/ai", contentType = "stock/ai") {
        FridgeAiBlock(
            state = aiState,
            canAsk = state.itemCount > 0,
            onAsk = onAskAi,
            onDismiss = onDismissAi,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.emberEntrance(aiIndex, EntranceKind.Rise, first, entrances, "ai"),
        )
    }
    val addIndex = step++
    item(key = "stock/add", contentType = "stock/add") {
        EmberButton(
            text = stringResource(R.string.fridge_add_action),
            onClick = onAddClick,
            modifier = Modifier
                .padding(top = 4.dp)
                .emberEntrance(addIndex, EntranceKind.Rise, first, entrances, "add"),
            variant = ButtonVariant.Ink,
            size = ButtonSize.Lg,
            icon = EmberIcons.Plus,
        )
    }
    state.itemsByAisle.forEach { (aisle, items) ->
        val index = step++
        // An aisle is one item, its card drawn whole: the list composes only the aisles on screen.
        item(key = "stock/aisle/${aisle.name}", contentType = "stock/aisle") {
            Column(Modifier.emberEntrance(index, EntranceKind.Rise, first, entrances, "aisle-$aisle")) {
                GroupHead(aisleLabel(aisle), count = items.size)
                InsetGroup {
                    items.forEach { item ->
                        // Keyed by the item: an addition moves the rows below it, and each row
                        // must keep its own number instead of handing it to its new neighbour.
                        key(item.name) {
                            row {
                                // Decided when the row first appears: only a row that turns up with
                                // the addition unfolds; one scrolled to later is simply there.
                                val fresh = remember { item.name in added }
                                Unfold(fresh) { StockRow(item, onClick = { onEdit(item) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Броколі … 420 g ›": the name, its amount (rounded like the shopping list) and the way to edit it. */
@Composable
private fun StockRow(item: FridgeItemUi, onClick: () -> Unit) {
    val t = Ember.type
    ListRow(
        title = item.name,
        onClick = onClick,
        chevron = true,
        trailing = {
            NumberWithUnit(
                value = item.displayGrams.toLong(),
                unit = stringResource(R.string.plan_unit_g),
                numberStyle = t.rowNumber,
                unitSize = 12.sp,
            )
        },
    )
}

/**
 * A row that has just been added: it unfolds into place, then an Ember glow over it fades out
 * (1.8 s, after 0.9 s). Anything else is simply there.
 */
@Composable
private fun Unfold(fresh: Boolean, content: @Composable () -> Unit) {
    if (!fresh) {
        content()
        return
    }
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val shown = remember { MutableTransitionState(reduced) }
    shown.targetState = true
    val glow = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (glow.value > 0f) {
            motionDelay(900)
            glow.animateTo(0f, tween(1800, easing = EmberEasing.Out))
        }
    }
    AnimatedVisibility(
        visibleState = shown,
        enter = expandVertically(motionSpec(EmberSprings.snappy())) + fadeIn(motionSpec(tween(260))),
    ) {
        Box(Modifier.drawBehind { if (glow.value > 0f) drawRect(c.emberGlow.copy(alpha = c.emberGlow.alpha * glow.value)) }) {
            content()
        }
    }
}

/** "You could cook this now" — computed from real stock, never from the plan. */
@Composable
private fun IdeasCard(
    ideas: List<FridgeMath.Match>,
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    // The head's meta stays on one line beside the title; with large text it would squeeze the
    // title into breaking after every word, and it only repeats what the card says anyway.
    val roomForMeta = LocalDensity.current.fontScale < 1.25f
    EmberCard(modifier, padding = PaddingValues(0.dp)) {
        CardHead(
            label = stringResource(R.string.fridge_ideas_title),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            icon = EmberIcons.Utensils,
            metric = Metric.Kcal,
            meta = if (roomForMeta) stringResource(R.string.fridge_ideas_meta) else null,
        )
        ideas.forEachIndexed { i, idea ->
            if (i > 0) {
                Row {
                    Spacer(Modifier.width(64.dp))
                    Spacer(Modifier.weight(1f).height(.5.dp).background(c.sep))
                }
            }
            // Portion factor 1.0: the recipe as written, because nothing has sized a portion for
            // this dish yet.
            key(idea.recipeId) { IdeaRow(idea, onClick = { onOpenRecipe(idea.recipeId, 1.0) }) }
        }
    }
}

@Composable
private fun IdeaRow(idea: FridgeMath.Match, onClick: () -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    val ready = idea.missingCount == 0
    // The check pops when an idea becomes "everything is in the fridge" while it is on screen.
    val wasReady = remember(idea.recipeId) { mutableStateOf(ready) }
    val pop = remember(idea.recipeId) { Animatable(1f) }
    val reduced = Ember.motion.reduced
    LaunchedEffect(ready) {
        if (ready && !wasReady.value && !reduced) {
            pop.snapTo(.4f)
            pop.animateTo(1f, EmberSprings.bouncy())
        }
        wasReady.value = ready
    }
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
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(c.fill2, EmberShapes.rowIcon), contentAlignment = Alignment.Center) {
            EmberIcon(EmberIcons.Bowl, null, size = 20.dp, tint = c.label2)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ControlText(idea.name, style = t.callout, color = c.label)
            if (ready) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    EmberIcon(
                        EmberIcons.Check, null,
                        Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                        size = 13.dp, tint = c.good, strokeWidth = 3f,
                    )
                    ControlText(
                        stringResource(R.string.fridge_idea_ready),
                        style = t.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.good,
                    )
                }
            } else {
                ControlText(
                    stringResource(R.string.fridge_idea_missing, idea.missingNames.joinToString(", ")),
                    style = t.footnote,
                    color = c.label2,
                )
            }
        }
        EmberIcon(EmberIcons.Right, null, size = 20.dp, tint = c.label3)
    }
}

/** The stock at its final geometry while it loads (decorative; it says "Loading" once). */
@Composable
private fun StockSkeleton() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmberCard(padding = PaddingValues(0.dp)) {
            SkeletonLine(150.dp, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp), height = 14.dp)
            SkeletonRows(3)
        }
        Skeleton(Modifier.fillMaxWidth().heightIn(min = 120.dp))
        Skeleton(Modifier.fillMaxWidth().heightIn(min = 54.dp), EmberShapes.capsule)
        SkeletonLine(120.dp, Modifier.padding(start = 16.dp, top = 14.dp), height = 12.dp)
        EmberCard(padding = PaddingValues(0.dp)) { SkeletonRows(3) }
    }
}
