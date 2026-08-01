package com.nutricart.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.FoodRepository
import com.nutricart.app.domain.model.MealSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodSearchUiState(
    val query: String = "",
    /** True when the user tried to search with fewer than 2 characters. */
    val queryTooShort: Boolean = false,
    val searching: Boolean = false,
    /** False until the first search, so we don't show "nothing found" too early. */
    val searched: Boolean = false,
    val results: List<FoodProductEntity> = emptyList(),
    /** True when the results came from the local cache (no internet). */
    val offline: Boolean = false,
    /** The product the user tapped — non-null shows the amount dialog. */
    val selected: FoodProductEntity? = null,
    /** Flips to true after saving; the screen then navigates back. */
    val logged: Boolean = false,
)

@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val diaryRepository: DiaryRepository,
    savedStateHandle: SavedStateHandle, // navigation arguments arrive here
) : ViewModel() {

    /** Which meal and which day this search will log into (from the route). */
    val mealSlot: MealSlot = MealSlot.valueOf(checkNotNull(savedStateHandle["slot"]))
    private val epochDay: Long = checkNotNull(savedStateHandle["epochDay"])

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(text: String) =
        _uiState.update { it.copy(query = text, queryTooShort = false) }

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
            _uiState.update { it.copy(logged = true) }
        }
    }
}
