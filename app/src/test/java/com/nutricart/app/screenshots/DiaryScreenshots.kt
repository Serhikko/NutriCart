package com.nutricart.app.screenshots

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.diary.CustomFormTarget
import com.nutricart.app.ui.diary.DiaryContent
import com.nutricart.app.ui.diary.DiaryUiState
import com.nutricart.app.ui.diary.FoodSearchActions
import com.nutricart.app.ui.diary.FoodSearchContent
import com.nutricart.app.ui.diary.FoodSearchUiState
import com.nutricart.app.ui.navigation.Routes
import org.junit.Test

/** The Diary tab, the food search screen and every dialog the search opens. */
class DiaryScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Diary(state: DiaryUiState, savingSlot: MealSlot? = null, editingNote: Boolean = false) =
        TabFrame(Routes.DIARY) {
            DiaryContent(
                state = state,
                savingSlot = savingSlot,
                editingNote = editingNote,
                snackbarHostState = remember { SnackbarHostState() },
                onAddFood = { _, _ -> },
                onPreviousDay = {},
                onNextDay = {},
                onEditNote = {},
                onDelete = {},
                onSaveAsMeal = {},
                onSaveMeal = {},
                onCancelSavingMeal = {},
                onSaveNote = {},
                onCancelEditingNote = {},
            )
        }

    /** Food search is a stacked screen: no bottom bar, as in the app. */
    @Composable
    private fun Search(
        state: FoodSearchUiState,
        snackbar: SnackbarHostState = remember { SnackbarHostState() },
    ) = FoodSearchContent(
        state = state,
        mealSlot = MealSlot.LUNCH,
        epochDay = FakeData.today,
        snackbarHostState = snackbar,
        onDone = {},
        actions = FakeData.noActions<FoodSearchActions>(),
    )

    @Test
    @KeyScreen
    fun day() = shoot("diary-day") { Diary(FakeData.diary()) }

    @Test
    fun emptyPastDay() = shoot("diary-empty-yesterday") { Diary(FakeData.diaryEmptyYesterday()) }

    @Test
    fun saveMealDialog() = shoot("diary-save-meal-dialog") { Diary(FakeData.diary(), savingSlot = MealSlot.LUNCH) }

    /**
     * Adding a note. Editing an EXISTING one is not shot: with text in the field,
     * the long floating label inside the AlertDialog never lets the UI go idle
     * under Robolectric (frames keep coming, memory climbs to the OOM killer).
     */
    @Test
    fun noteDialog() = shoot("diary-note-dialog-new") { Diary(FakeData.diary().copy(note = null), editingNote = true) }

    @Test
    @KeyScreen
    fun searchFrequent() = shoot("food-search-frequent-basket") { Search(FakeData.foodSearchFrequent()) }

    @Test
    @KeyScreen
    fun searchResults() = shoot("food-search-results") { Search(FakeData.foodSearchResults()) }

    @Test
    fun searchOffline() = shoot("food-search-results-offline") { Search(FakeData.foodSearchOffline()) }

    @Test
    fun searching() = shoot("food-search-searching") { Search(FakeData.foodSearchSearching()) }

    @Test
    fun noResults() = shoot("food-search-no-results") { Search(FakeData.foodSearchNoResults()) }

    @Test
    fun favorites() = shoot("food-search-favorites") { Search(FakeData.foodSearchFavorites()) }

    @Test
    fun savedMeals() = shoot("food-search-saved-meals") { Search(FakeData.foodSearchSavedMeals()) }

    @Test
    fun barcodeNotFoundSnackbar() = shoot("food-search-snackbar-not-found") {
        val host = remember { SnackbarHostState() }
        val message = stringResource(R.string.barcode_not_found)
        val action = stringResource(R.string.add_action)
        LaunchedEffect(Unit) {
            host.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Indefinite)
        }
        Search(FakeData.foodSearchFrequent().copy(basket = emptyList()), host)
    }

    @Test
    @KeyScreen
    fun amountDialog() = shoot("food-amount-dialog") {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.yogurtProduct))
    }

    @Test
    fun amountDialogDrink() = shoot("food-amount-dialog-drink") {
        Search(FakeData.foodSearchResults().copy(selected = FakeData.milkProduct))
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

    @Test
    fun basketDialog() = shoot("food-basket-dialog") {
        Search(FakeData.foodSearchFrequent().copy(basketOpen = true))
    }
}
