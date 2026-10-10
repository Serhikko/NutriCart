package com.nutricart.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.common.DayBudget
import com.nutricart.app.ui.diary.BasketItem
import com.nutricart.app.ui.diary.CustomFormTarget
import com.nutricart.app.ui.diary.DiaryContent
import com.nutricart.app.ui.diary.DiaryUiState
import com.nutricart.app.ui.diary.FoodSearchActions
import com.nutricart.app.ui.diary.FoodSearchContent
import com.nutricart.app.ui.diary.FoodSearchUiState
import com.nutricart.app.ui.ember.EmberToastVisuals
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.ember.ToastIcon
import com.nutricart.app.ui.ember.seedLastShown
import com.nutricart.app.ui.navigation.AddedHandOff
import com.nutricart.app.ui.navigation.AppBottomBar
import com.nutricart.app.ui.navigation.LocalAddedHandOff
import com.nutricart.app.ui.navigation.Routes
import org.junit.Test

/**
 * The Diary tab, the food search screen and every sheet they open (G2). Pages sit on the app's
 * receding stage, so a sheet shows the page behind it as the app does.
 */
class DiaryScreenshots(variant: Variant) : ScreenshotTest(variant) {

    /** The Diary tab as the shell lays it out: the page on the stage, the tab bar over it (gone while a sheet presents). */
    @Composable
    private fun Diary(
        state: DiaryUiState,
        savingSlot: MealSlot? = null,
        editingNote: Boolean = false,
        budget: DayBudget? = DiaryFakes.budget(state),
        snackbar: SnackbarHostState = remember { SnackbarHostState() },
        onPreviousDay: () -> Unit = {},
    ) {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            Box(Modifier.fillMaxSize()) {
                SheetStage {
                    DiaryContent(
                        state = state,
                        savingSlot = savingSlot,
                        editingNote = editingNote,
                        snackbarHostState = snackbar,
                        onAddFood = { _, _ -> },
                        onPreviousDay = onPreviousDay,
                        onNextDay = {},
                        onEditNote = {},
                        onDelete = {},
                        onSaveAsMeal = {},
                        onSaveMeal = {},
                        onCancelSavingMeal = {},
                        onSaveNote = {},
                        onCancelEditingNote = {},
                        budget = budget,
                    )
                }
                AppBottomBar(
                    Routes.DIARY, {}, {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = !presentation.presenting,
                )
            }
        }
    }

    /** Food search is a stacked screen: no tab bar, a back link to the Diary. */
    @Composable
    private fun Search(
        state: FoodSearchUiState,
        snackbar: SnackbarHostState = remember { SnackbarHostState() },
        budget: DayBudget? = DiaryFakes.searchBudget(),
        actions: FoodSearchActions = FakeData.noActions(),
    ) {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            SheetStage {
                FoodSearchContent(
                    state = state,
                    mealSlot = MealSlot.LUNCH,
                    epochDay = FakeData.today,
                    snackbarHostState = snackbar,
                    onDone = {},
                    actions = actions,
                    budget = budget,
                    backLabel = stringResource(R.string.diary_title),
                )
            }
        }
    }

    // ---------- Diary ----------

    @Test
    @KeyScreen
    fun day() = shoot("diary-day") { Diary(FakeData.diary()) }

    /**
     * The whole day, every meal card down to its footer: a key screen, because the footers ("+ Add
     * food" beside "Save as meal") and the entry names are where long Ukrainian words and large
     * text run out of room first.
     */
    @Test
    @KeyScreen
    @Tall(1900)
    fun dayFull() = shoot("diary-day-full") { Diary(FakeData.diary()) }

    @Test
    fun dayScrolled() = shoot(
        "diary-day-scrolled",
        interact = { onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(1) },
    ) { Diary(FakeData.diary()) }

    @Test
    fun dayOver() = shoot("diary-day-over") { Diary(DiaryFakes.diaryOver()) }

    /** Without the day budget (onboarding not finished, or not loaded yet): the total alone, no ring. */
    @Test
    fun dayNoBudget() = shoot("diary-day-no-budget") { Diary(FakeData.diary(), budget = null) }

    @Test
    fun emptyPastDay() = shoot("diary-empty-yesterday") { Diary(FakeData.diaryEmptyYesterday()) }

    @Test
    fun saveMealDialog() = shoot("diary-save-meal-dialog") { Diary(FakeData.diary(), savingSlot = MealSlot.LUNCH) }

    @Test
    fun noteDialog() = shoot("diary-note-dialog-new") { Diary(FakeData.diary().copy(note = null), editingNote = true) }

    /**
     * Editing an EXISTING note: the case that never settled with the old floating-label field in an
     * AlertDialog. The Ember sheet's label sits above the well, so it renders and goes idle.
     */
    @Test
    @KeyScreen
    fun noteSheetEdit() = shoot("diary-note-sheet-edit") { Diary(FakeData.diary(), editingNote = true) }

    @Test
    fun savedMealToast() = shoot("diary-toast-saved-meal") {
        val host = remember { SnackbarHostState() }
        val message = stringResource(R.string.saved_meal_saved)
        LaunchedEffect(Unit) {
            host.showSnackbar(EmberToastVisuals(message, ToastIcon.Check, duration = SnackbarDuration.Indefinite))
        }
        Diary(FakeData.diary(), snackbar = host)
    }

    /** First open of the day: title, cards rising in a cascade, the summary ring sweeping, digits arriving. */
    @Test
    fun firstOpenFrames() = shootFrames("diary-first-open", times = listOf(60, 250, 450, 700, 1200, 2000)) {
        Diary(FakeData.diary())
    }

    /**
     * Back from Add: the banana was just logged to Snacks. The ring sweeps on from 1,235, the day and
     * Snacks totals hand off, the new row unfolds under its Ember glow, "Added to Snacks".
     */
    @Test
    @Tall(1700)
    fun backFromAddFrames() {
        val today = FakeData.today
        seedLastShown("ring/$today", 1235f)
        seedLastShown("diary-total/$today", 1235L)
        seedLastShown("slot/$today/${MealSlot.SNACK}", 116L)
        seedLastShown("entries/$today/${MealSlot.SNACK}", setOf(6L))
        shootFrames("diary-back-from-add", times = listOf(60, 400, 650, 900, 1300, 2800), firstOpen = false) {
            CompositionLocalProvider(LocalAddedHandOff provides AddedHandOff(MealSlot.SNACK) {}) {
                val host = remember { SnackbarHostState() }
                Diary(FakeData.diary(), snackbar = host)
            }
        }
    }

    /** ‹ to yesterday: the new day slides in from the left, the ring re-sweeps, totals hand off. */
    @Test
    fun dayChangeFrames() = shootFrames(
        "diary-day-change",
        times = listOf(60, 150, 300, 600, 1400),
        firstOpen = false,
        interact = { onNodeWithContentDescription(str(R.string.previous_day)).performClick() },
    ) {
        var state by remember { mutableStateOf(FakeData.diary()) }
        Diary(state, budget = DiaryFakes.budget(state), onPreviousDay = { state = DiaryFakes.diaryYesterday() })
    }

    // ---------- Food search ----------

    @Test
    @KeyScreen
    fun searchFrequent() = shoot("food-search-frequent-basket") { Search(FakeData.foodSearchFrequent()) }

    @Test
    @KeyScreen
    fun searchResults() = shoot("food-search-results") { Search(FakeData.foodSearchResults()) }

    @Test
    fun searchIdle() = shoot("food-search-idle") { Search(FoodSearchUiState(), budget = null) }

    /**
     * A new search replaces the results: the new list cascades in (S1), row by row. The rows are
     * keyed by product, so the rye bread (a favourite) landing where a plain yogurt drink was keeps a
     * still star: the favourite pop plays only for a star someone tapped.
     */
    @Test
    fun newResultsFrames() {
        val state = mutableStateOf(FakeData.foodSearchResults())
        val results = FakeData.foodSearchResults().results
        val next = FakeData.foodSearchResults().copy(
            query = "skyr",
            results = listOf(results[3], FakeData.breadProduct, FakeData.oatsProduct, FakeData.milkProduct),
        )
        shootFrames(
            "food-search-new-results",
            times = listOf(60, 200, 450, 900),
            firstOpen = false,
            interact = {
                mainClock.advanceTimeBy(1500)
                state.value = next
                // A write from the test thread reaches the composition on the next frame only once it is applied.
                Snapshot.sendApplyNotifications()
            },
        ) { Search(state.value) }
    }

    @Test
    fun searchTooShort() = shoot("food-search-too-short") {
        Search(FoodSearchUiState(query = "y", queryTooShort = true, frequent = FakeData.foodSearchFrequent().frequent))
    }

    @Test
    fun searchOffline() = shoot("food-search-results-offline") { Search(FakeData.foodSearchOffline()) }

    @Test
    fun searching() = shoot("food-search-searching") { Search(FakeData.foodSearchSearching()) }

    @Test
    fun noResults() = shoot("food-search-no-results") { Search(FakeData.foodSearchNoResults()) }

    @Test
    fun favorites() = shoot("food-search-favorites") { Search(FakeData.foodSearchFavorites()) }

    @Test
    fun favoritesEmpty() = shoot("food-search-favorites-empty") { Search(FoodSearchUiState(favoritesMode = true)) }

    @Test
    fun savedMeals() = shoot("food-search-saved-meals") { Search(FakeData.foodSearchSavedMeals()) }

    @Test
    fun savedMealsEmpty() = shoot("food-search-saved-meals-empty") { Search(FoodSearchUiState(savedMealsMode = true)) }

    @Test
    fun barcodeNotFoundSnackbar() = shoot("food-search-snackbar-not-found") {
        val host = remember { SnackbarHostState() }
        val message = stringResource(R.string.barcode_not_found_ukraine)
        val action = stringResource(R.string.add_action)
        LaunchedEffect(Unit) {
            host.showSnackbar(EmberToastVisuals(message, ToastIcon.Info, actionLabel = action, duration = SnackbarDuration.Indefinite))
        }
        Search(FakeData.foodSearchFrequent().copy(basket = emptyList()), host)
    }

    /** The amount sheet with the day budget: the preview ring with this food's arc, "left after this". */
    @Test
    @KeyScreen
    fun amountSheetBudget() = shoot("food-amount-sheet-budget") {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.yogurtProduct))
    }

    /** Without a budget: the kcal alone. */
    @Test
    @KeyScreen
    fun amountDialog() = shoot("food-amount-dialog") {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.yogurtProduct), budget = null)
    }

    @Test
    fun amountDialogDrink() = shoot("food-amount-dialog-drink") {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.milkProduct))
    }

    /** "1 portion · 170 g" picked: the unit becomes portions and the portion note shows. */
    @Test
    fun amountPortions() = shoot(
        "food-amount-sheet-portions",
        interact = { onNodeWithContentDescription(str(R.string.portions_mode)).performClick() },
    ) {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.yogurtProduct))
    }

    /** "+" on the stepper: 100 → 110 g, the kcal digits hand off and the raspberry arc grows. */
    @Test
    fun amountStepFrames() = shootFrames(
        "food-amount-step",
        times = listOf(60, 200, 350, 700),
        firstOpen = false,
        interact = {
            mainClock.advanceTimeBy(1500)
            onNodeWithContentDescription(str(R.string.amount_more)).performClick()
        },
    ) {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.yogurtProduct))
    }

    /** "+" on Rolled oats: it morphs into the Ember check and the basket count hands off 2 → 3. */
    @Test
    fun basketAddFrames() = shootFrames(
        "food-basket-add",
        times = listOf(60, 200, 400, 900),
        firstOpen = false,
        interact = {
            mainClock.advanceTimeBy(1000)
            onAllNodesWithContentDescription(str(R.string.basket_quick_add)).onFirst().performClick()
        },
    ) {
        var state by remember { mutableStateOf(FakeData.foodSearchFrequent()) }
        Search(state, actions = DiaryFakes.basketActions { product ->
            state = state.copy(basket = state.basket + BasketItem(product, "50"))
        })
    }

    @Test
    @KeyScreen
    fun customFoodNew() = shoot("food-custom-dialog-new") {
        Search(FakeData.foodSearchFrequent().copy(customForm = CustomFormTarget(editing = null)))
    }

    @Test
    fun customFoodFromBarcode() = shoot("food-custom-dialog-barcode-prefill") {
        Search(
            FakeData.foodSearchFrequent().copy(
                customForm = CustomFormTarget(
                    editing = null,
                    barcode = FakeData.barcodePrefill.barcode,
                    prefill = FakeData.barcodePrefill,
                ),
            ),
        )
    }

    @Test
    fun customFoodEdit() = shoot("food-custom-dialog-edit") {
        Search(FakeData.foodSearchFavorites().copy(customForm = CustomFormTarget(editing = FakeData.breadProduct)))
    }

    /** Values out of range get the danger ring; Save stays off. */
    @Test
    fun customFoodInvalid() = shoot("food-custom-dialog-invalid") {
        Search(FakeData.foodSearchFrequent().copy(customForm = CustomFormTarget(editing = DiaryFakes.outOfRangeProduct)))
    }

    @Test
    fun basketDialog() = shoot("food-basket-dialog") {
        Search(FakeData.foodSearchFrequent().copy(basketOpen = true))
    }
}
