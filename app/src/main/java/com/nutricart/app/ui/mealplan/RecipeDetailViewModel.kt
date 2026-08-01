package com.nutricart.app.ui.mealplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.PlanRepository
import com.nutricart.app.data.repository.RecipeDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val details: RecipeDetails? = null,
    /** The portion factor this screen was opened with (1.0 = as written). */
    val portionFactor: Double = 1.0,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    planRepository: PlanRepository,
    savedStateHandle: SavedStateHandle, // navigation arguments arrive here
) : ViewModel() {

    private val recipeId: Long = checkNotNull(savedStateHandle["recipeId"])
    private val portionFactor: Double =
        (savedStateHandle.get<Float>("factor") ?: 1f).toDouble()

    private val _uiState = MutableStateFlow(RecipeDetailUiState(portionFactor = portionFactor))
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = RecipeDetailUiState(
                loading = false,
                details = planRepository.recipeDetails(recipeId),
                portionFactor = portionFactor,
            )
        }
    }
}
