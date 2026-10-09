package com.nutricart.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.dao.SavedMealSummary
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.repository.BarcodeLookup
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.FoodRepository
import com.nutricart.app.data.repository.SavedMealRepository
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ProductSource
import com.nutricart.app.scanner.BarcodeScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** One-shot messages of the barcode flow, shown as a snackbar and cleared. */
sealed interface ScanMessage {
    /**
     * Nobody knows this code: the snackbar offers to add it under [barcode]
     * (blank only if the scan held no digits — then there is nothing to add).
     * [notice] says which sources were checked, or could not be reached.
     */
    data class ProductNotFound(val barcode: String, val notice: LookupNotice) : ScanMessage

    data object Offline : ScanMessage

    data object ScannerFailed : ScanMessage
}

/**
 * The create/edit form target: editing == null means "new product".
 *
 * A new product opened from a scan carries its [barcode] (saved as
 * "local:barcode:<digits>", so the next scan finds it) and, when Open Food
 * Facts or a Ukrainian shop knew the product only partly, the [prefill] that
 * fills the form; [prefillFromShop] says which of the two it came from.
 */
data class CustomFormTarget(
    val editing: FoodProductEntity?,
    val barcode: String? = null,
    val prefill: ProductPrefill? = null,
    val prefillFromShop: Boolean = false,
) {
    /**
     * Where the form's "drink" box starts: the product being edited, else
     * what the source's pack size said, else food. The user can change it.
     */
    val startsAsLiquid: Boolean get() = editing?.isLiquid ?: prefill?.isLiquid ?: false
}

/** One product waiting in the multi-add basket; grams stay as typed text. */
data class BasketItem(val product: FoodProductEntity, val gramsText: String)

/** Valid grams of one basket line, or null — shared by the dialog and logBasket. */
fun basketGrams(item: BasketItem): Double? =
    item.gramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..5000.0 }

/** What the custom-food form hands back on save (all values pre-validated). */
data class CustomFoodDraft(
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
    val servingSizeG: Double?,
    // Optional detail nutrients — blank fields stay null ("not stated").
    val fiberPer100g: Double? = null,
    val sugarsPer100g: Double? = null,
    val saltPer100g: Double? = null,
    val saturatedFatPer100g: Double? = null,
    /** A drink: amounts are typed and shown in ml (values stay per 100). */
    val isLiquid: Boolean = false,
)

data class FoodSearchUiState(
    val query: String = "",
    /** True when the user tried to search with fewer than 2 characters. */
    val queryTooShort: Boolean = false,
    val scanMessage: ScanMessage? = null,
    val searching: Boolean = false,
    /** False until the first search, so we don't show "nothing found" too early. */
    val searched: Boolean = false,
    val results: List<FoodProductEntity> = emptyList(),
    /** True when the results came from the local cache (no internet). */
    val offline: Boolean = false,
    /** True while the list shows starred products instead of search results. */
    val favoritesMode: Boolean = false,
    /** Starred products (filtered live by the query in favorites mode). */
    val favorites: List<FoodProductEntity> = emptyList(),
    /** Most-logged products, shown under an empty search box. */
    val frequent: List<FoodProductEntity> = emptyList(),
    /** True while the list shows saved meals instead of products. */
    val savedMealsMode: Boolean = false,
    val savedMeals: List<SavedMealSummary> = emptyList(),
    /** Products collected for one multi-add write. */
    val basket: List<BasketItem> = emptyList(),
    val basketOpen: Boolean = false,
    /** One-shot: something was logged, but the basket still holds items —
     *  the screen stays and reminds instead of navigating away. */
    val basketReminder: Boolean = false,
    /** The product the user tapped — non-null shows the amount dialog. */
    val selected: FoodProductEntity? = null,
    /** Non-null shows the create/edit custom-food form. */
    val customForm: CustomFormTarget? = null,
    /** Flips to true after saving; the screen then navigates back. */
    val logged: Boolean = false,
)

@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val diaryRepository: DiaryRepository,
    private val savedMealRepository: SavedMealRepository,
    private val barcodeScanner: BarcodeScanner, // interface — Hilt injects the ML Kit one
    private val savedStateHandle: SavedStateHandle, // navigation arguments arrive here
) : ViewModel() {

    /** Which meal and which day this search will log into (from the route). */
    val mealSlot: MealSlot = MealSlot.valueOf(checkNotNull(savedStateHandle["slot"]))
    val epochDay: Long = checkNotNull(savedStateHandle["epochDay"])

    /** Opened straight from the quick-add sheet's "Scan a barcode" row. */
    private val autoScan: Boolean = savedStateHandle["autoScan"] ?: false

    // Saved in the handle, not in a plain field: the scanner hands the
    // foreground to Play services, so the whole activity can be destroyed
    // while the camera is up. A plain field would come back false and reopen
    // the camera; the route argument itself always survives.
    private var autoScanFired: Boolean
        get() = savedStateHandle["autoScanFired"] ?: false
        set(value) {
            savedStateHandle["autoScanFired"] = value
        }

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshFrequent()
    }

    private fun refreshFrequent() {
        viewModelScope.launch {
            _uiState.update { it.copy(frequent = foodRepository.frequentProducts()) }
        }
    }

    fun setQuery(text: String) {
        _uiState.update { it.copy(query = text, queryTooShort = false) }
        // Favorites live locally, so they filter on every keystroke — no
        // "min 2 chars + search button" ceremony like the online search.
        if (_uiState.value.favoritesMode) refreshFavorites()
    }

    fun toggleFavoritesMode() {
        val on = !_uiState.value.favoritesMode
        // The two chips are exclusive: one list, one mode at a time.
        _uiState.update { it.copy(favoritesMode = on, savedMealsMode = false) }
        if (on) refreshFavorites()
    }

    fun toggleSavedMealsMode() {
        val on = !_uiState.value.savedMealsMode
        _uiState.update { it.copy(savedMealsMode = on, favoritesMode = false) }
        if (on) refreshSavedMeals()
    }

    private fun refreshSavedMeals() {
        viewModelScope.launch {
            _uiState.update { it.copy(savedMeals = savedMealRepository.summaries()) }
        }
    }

    private var loggingMeal = false

    /** One tap: every item of the saved meal goes into this slot and day. */
    fun logSavedMeal(meal: SavedMealSummary) {
        // The row stays tappable until navigation — without this flag a
        // double-tap would write the whole meal twice (review finding).
        if (loggingMeal) return
        loggingMeal = true
        viewModelScope.launch {
            try {
                val items = savedMealRepository.itemsFor(meal.id)
                if (items.isEmpty()) return@launch
                diaryRepository.logSavedMealItems(items, mealSlot, epochDay)
                finishLogging()
            } finally {
                loggingMeal = false
            }
        }
    }

    /**
     * After any successful write: navigate back — UNLESS the basket still
     * holds collected items, which navigating would silently throw away
     * (review finding). Then the screen stays and shows a reminder.
     */
    private fun finishLogging() {
        _uiState.update {
            if (it.basket.isEmpty()) it.copy(logged = true)
            else it.copy(basketReminder = true)
        }
    }

    fun clearBasketReminder() = _uiState.update { it.copy(basketReminder = false) }

    fun deleteSavedMeal(meal: SavedMealSummary) {
        viewModelScope.launch {
            savedMealRepository.delete(meal.id)
            _uiState.update { s -> s.copy(savedMeals = s.savedMeals.filter { it.id != meal.id }) }
        }
    }

    /** Quick "+": into the basket with the label portion (or 100 g) prefilled. */
    fun addToBasket(product: FoodProductEntity) {
        _uiState.update { s ->
            // Already collected — nothing to do; grams are edited in the basket.
            if (s.basket.any { it.product.id == product.id }) return@update s
            val defaultGrams = (product.servingSizeG ?: 100.0).roundToInt().toString()
            s.copy(basket = s.basket + BasketItem(product, defaultGrams))
        }
    }

    fun removeFromBasket(productId: String) = _uiState.update { s ->
        s.copy(basket = s.basket.filter { it.product.id != productId })
    }

    fun setBasketGrams(productId: String, text: String) = _uiState.update { s ->
        s.copy(basket = s.basket.map {
            if (it.product.id == productId) it.copy(gramsText = text) else it
        })
    }

    fun openBasket() = _uiState.update { it.copy(basketOpen = true) }

    fun closeBasket() = _uiState.update { it.copy(basketOpen = false) }

    /** Writes every basket line into the diary; the screen then navigates back. */
    fun logBasket() {
        val items = _uiState.value.basket
        val parsed = items.mapNotNull { item -> basketGrams(item)?.let { item.product to it } }
        // The dialog disables the button on invalid input — this is the last guard.
        if (items.isEmpty() || parsed.size != items.size) return
        _uiState.update { it.copy(basketOpen = false) }
        viewModelScope.launch {
            // One transactional write — the basket lands whole or not at all.
            diaryRepository.logProducts(parsed, mealSlot, epochDay)
            _uiState.update { it.copy(logged = true, basket = emptyList()) }
        }
    }

    private var favoritesJob: Job? = null

    private fun refreshFavorites() {
        // Same rule as search(): a slow old query must never overwrite the
        // result of a newer keystroke.
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            val query = _uiState.value.query.trim()
            _uiState.update { it.copy(favorites = foodRepository.searchFavorites(query)) }
        }
    }

    /**
     * Applies one product change to EVERY in-memory list that may hold it —
     * results, frequent, favorites AND the basket. Patching only `results`
     * was a review-caught bug: a product edited or starred while sitting in
     * the frequent list or the basket kept its stale copy.
     */
    private fun patchEverywhere(updated: FoodProductEntity) {
        _uiState.update { s ->
            fun patch(list: List<FoodProductEntity>) =
                list.map { if (it.id == updated.id) updated else it }
            s.copy(
                results = patch(s.results),
                frequent = patch(s.frequent),
                favorites = patch(s.favorites),
                basket = s.basket.map {
                    if (it.product.id == updated.id) it.copy(product = updated) else it
                },
            )
        }
    }

    /** Removes a deleted product from every list — a stale row would crash
     *  logging on the diary's foreign key (review finding). */
    private fun removeEverywhere(productId: String) {
        _uiState.update { s ->
            s.copy(
                results = s.results.filter { it.id != productId },
                frequent = s.frequent.filter { it.id != productId },
                favorites = s.favorites.filter { it.id != productId },
                basket = s.basket.filter { it.product.id != productId },
            )
        }
    }

    /** Star / un-star a product; every visible list updates in place. */
    fun toggleFavorite(product: FoodProductEntity) {
        val newValue = !product.isFavorite
        viewModelScope.launch {
            foodRepository.setFavorite(product.id, newValue)
            patchEverywhere(product.copy(isFavorite = newValue))
            if (_uiState.value.favoritesMode) refreshFavorites()
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            // One letter would match half the database — tell the user instead
            // of silently doing nothing.
            _uiState.update { it.copy(queryTooShort = true) }
            return
        }
        // Cancel the previous request: a slow old response must never
        // overwrite the results of a newer search.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(searching = true, queryTooShort = false) }
            val result = foodRepository.search(query)
            _uiState.update {
                it.copy(
                    searching = false,
                    searched = true,
                    results = result.products,
                    offline = result.offline,
                )
            }
        }
    }

    fun select(product: FoodProductEntity?) = _uiState.update { it.copy(selected = product) }

    private var scanning = false

    /**
     * Opens the scanner once when the screen was entered from the quick-add
     * sheet. The "once" lives HERE and not in the screen: a rotation recreates
     * the composable but not this view model, and the saved flag additionally
     * covers the app being killed behind the camera.
     */
    fun scanOnOpenIfAsked() {
        if (!autoScan || autoScanFired) return
        autoScanFired = true
        scanBarcode()
    }

    /**
     * Opens the system scanner. A found product goes straight to the amount
     * dialog; one Open Food Facts or a Ukrainian shop knows only partly opens
     * the new-food form prefilled; an unknown one shows a snackbar (saying
     * which sources were checked) whose "Add" action opens the form tied to
     * the barcode (openBarcodeForm).
     */
    fun scanBarcode() {
        if (scanning) return // the scanner UI takes a moment — ignore double-taps
        scanning = true
        viewModelScope.launch {
            try {
                val barcode = barcodeScanner.scan() ?: return@launch // user cancelled
                _uiState.update { it.copy(searching = true) }
                val result = foodRepository.byBarcode(barcode)
                _uiState.update {
                    when (result) {
                        is BarcodeLookup.Found ->
                            it.copy(searching = false, selected = result.product, scanMessage = null)
                        // Known only partly: the form, prefilled, instead of a
                        // dead end — the user copies the rest from the label.
                        is BarcodeLookup.Incomplete -> it.copy(
                            searching = false,
                            scanMessage = null,
                            customForm = CustomFormTarget(
                                editing = null,
                                barcode = result.prefill.barcode,
                                prefill = result.prefill,
                                prefillFromShop = result.fromShop,
                            ),
                        )
                        is BarcodeLookup.NotFound -> it.copy(
                            searching = false,
                            scanMessage = ScanMessage.ProductNotFound(result.barcode, result.notice),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The scan worked, the phone is just offline.
                _uiState.update {
                    it.copy(searching = false, scanMessage = ScanMessage.Offline)
                }
            } catch (e: Exception) {
                // E.g. the scanner module is still downloading on first use.
                _uiState.update {
                    it.copy(searching = false, scanMessage = ScanMessage.ScannerFailed)
                }
            } finally {
                scanning = false
            }
        }
    }

    fun clearScanMessage() = _uiState.update { it.copy(scanMessage = null) }

    fun openCreateForm() = _uiState.update { it.copy(customForm = CustomFormTarget(null)) }

    /** The "Add" action of a not-found scan: an empty form tied to [barcode]. */
    fun openBarcodeForm(barcode: String) {
        if (barcode.isBlank()) return
        _uiState.update { it.copy(customForm = CustomFormTarget(editing = null, barcode = barcode)) }
    }

    fun openEditForm(product: FoodProductEntity) {
        // Only user-created products are editable; OFF and shop data are not ours to change.
        if (product.source != ProductSource.LOCAL) return
        _uiState.update { it.copy(customForm = CustomFormTarget(product)) }
    }

    fun dismissCustomForm() = _uiState.update { it.copy(customForm = null) }

    /** Create or update, depending on what the form was opened for. */
    fun saveCustomProduct(draft: CustomFoodDraft) {
        val target = _uiState.value.customForm ?: return
        // Close first — same double-tap protection as the amount dialog.
        _uiState.update { it.copy(customForm = null) }
        viewModelScope.launch {
            val editing = target.editing
            if (editing == null) {
                val created = foodRepository.createCustomProduct(
                    name = draft.name,
                    brand = draft.brand,
                    kcalPer100g = draft.kcalPer100g,
                    proteinPer100g = draft.proteinPer100g,
                    fatPer100g = draft.fatPer100g,
                    carbsPer100g = draft.carbsPer100g,
                    servingSizeG = draft.servingSizeG,
                    fiberPer100g = draft.fiberPer100g,
                    sugarsPer100g = draft.sugarsPer100g,
                    saltPer100g = draft.saltPer100g,
                    saturatedFatPer100g = draft.saturatedFatPer100g,
                    barcode = target.barcode,
                    isLiquid = draft.isLiquid,
                )
                // Saved under a barcode that already had a product: the lists
                // (and the basket) must not keep the old copy.
                if (target.barcode != null) patchEverywhere(created)
                // Straight into the amount dialog: after creating a product
                // the user almost always wants to log it right away.
                _uiState.update { it.copy(selected = created) }
            } else {
                val updated = editing.copy(
                    name = draft.name.trim(),
                    brand = draft.brand?.trim()?.takeIf { b -> b.isNotEmpty() },
                    kcalPer100g = draft.kcalPer100g,
                    proteinPer100g = draft.proteinPer100g,
                    fatPer100g = draft.fatPer100g,
                    carbsPer100g = draft.carbsPer100g,
                    servingSizeG = draft.servingSizeG,
                    fiberPer100g = draft.fiberPer100g,
                    sugarsPer100g = draft.sugarsPer100g,
                    saltPer100g = draft.saltPer100g,
                    saturatedFatPer100g = draft.saturatedFatPer100g,
                    isLiquid = draft.isLiquid,
                )
                foodRepository.updateCustomProduct(updated)
                // ALL lists, not just results: the same product may sit in
                // the frequent list, favorites or the basket right now.
                patchEverywhere(updated)
            }
        }
    }

    /** Deletes the product currently open in the edit form. */
    fun deleteCustomProduct() {
        val editing = _uiState.value.customForm?.editing ?: return
        _uiState.update { it.copy(customForm = null) }
        viewModelScope.launch {
            foodRepository.deleteCustomProduct(editing.id)
            // Every list AND the basket: a surviving stale row would crash
            // the next logging attempt on the diary's foreign key.
            removeEverywhere(editing.id)
        }
    }

    /** Called by the amount dialog with the final grams (and portions, if used). */
    fun log(product: FoodProductEntity, grams: Double, servings: Double?) {
        // Close the dialog IMMEDIATELY, before the database write: while an
        // insert is in flight a still-open dialog would let a double-tap
        // insert the same entry twice.
        _uiState.update { it.copy(selected = null) }
        viewModelScope.launch {
            diaryRepository.logProduct(
                product = product,
                grams = grams,
                servings = servings,
                meal = mealSlot,
                epochDay = epochDay,
            )
            finishLogging()
        }
    }
}
