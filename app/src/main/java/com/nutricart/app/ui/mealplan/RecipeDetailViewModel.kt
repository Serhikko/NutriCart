package com.nutricart.app.ui.mealplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.FridgeRepository
import com.nutricart.app.data.repository.PlanRepository
import com.nutricart.app.data.repository.RecipeDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val details: RecipeDetails? = null,
    /** The portion factor this screen was opened with (1.0 = as written). */
    val portionFactor: Double = 1.0,
    /** Already deducted from the fridge during this visit. */
    val cooked: Boolean = false,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    planRepository: PlanRepository,
    private val fridgeRepository: FridgeRepository,
    savedStateHandle: SavedStateHandle, // navigation arguments arrive here
) : ViewModel() {

    private val recipeId: Long = checkNotNull(savedStateHandle["recipeId"])
    private val portionFactor: Double =
        (savedStateHandle.get<Float>("factor") ?: 1f).toDouble()

    private val _uiState = MutableStateFlow(RecipeDetailUiState(portionFactor = portionFactor))
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    // A plain field, checked synchronously: a UiState flag only disables the
    // button after the next recomposition, and two taps inside one frame would
    // both get through and deduct the meal twice.
    private var cooking = false

    init {
        viewModelScope.launch {
            _uiState.value = RecipeDetailUiState(
                loading = false,
                details = planRepository.recipeDetails(recipeId),
                portionFactor = portionFactor,
            )
        }
    }

    /**
     * Takes this meal out of the fridge. [portions] is how many were cooked at
     * once — a batch-cooking week plans the same pot for two or three days, and
     * one tap must be able to say so instead of the user opening the recipe
     * again on days they cooked nothing.
     *
     * One-shot per screen visit; ingredients the fridge never had are simply
     * not there to subtract, so this never fails and never blocks cooking.
     */
    fun cook(portions: Int) {
        if (cooking) return
        cooking = true
        viewModelScope.launch {
            fridgeRepository.cook(recipeId, portionFactor, portions)
            _uiState.update { it.copy(cooked = true) }
        }
    }
}
