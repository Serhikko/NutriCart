package com.nutricart.app.ui.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.data.repository.FridgeAiRepository
import com.nutricart.app.data.repository.FridgeRepository
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.domain.logic.ShoppingListBuilder
import com.nutricart.app.domain.model.Aisle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One fridge line, ready for display. */
data class FridgeItemUi(
    val name: String,
    val aisle: Aisle,
    /** Raw grams, for prefilling the edit dialog. */
    val grams: Double,
    /** Rounded for the eye, exactly like the shopping list rounds. */
    val displayGrams: Int,
)

data class FridgeUiState(
    val loading: Boolean = true,
    val itemsByAisle: Map<Aisle, List<FridgeItemUi>> = emptyMap(),
    val itemCount: Int = 0,
    /** Best "you could cook this now" ideas, already diet-filtered. */
    val ideas: List<FridgeMath.Match> = emptyList(),
)

/**
 * The optional assistant's corner of the screen. Separate from the stock state
 * so that asking a question never recomputes the fridge, and a stock change
 * never throws away an answer the user is reading.
 */
data class AiUiState(
    /** No key configured: the screen offers Settings instead of a request. */
    val hasKey: Boolean = false,
    val loading: Boolean = false,
    val result: AiResult? = null,
)

@HiltViewModel
class FridgeViewModel @Inject constructor(
    private val fridgeRepository: FridgeRepository,
    private val aiRepository: FridgeAiRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<FridgeUiState> = combine(
        fridgeRepository.observeAll(),
        profileRepository.observeProfile(),
    ) { items, profile ->
        FridgeUiState(
            loading = false,
            itemsByAisle = items
                .map { row ->
                    FridgeItemUi(
                        name = row.ingredientName,
                        aisle = row.aisle,
                        grams = row.grams,
                        displayGrams = ShoppingListBuilder.displayGrams(row.grams),
                    )
                }
                // Sorted HERE, not in SQL: `aisle` is stored as TEXT, so the
                // database would order the enum names alphabetically instead
                // of in store-walk order.
                .sortedWith(compareBy({ it.aisle.ordinal }, { it.name }))
                .groupBy { it.aisle },
            itemCount = items.size,
            // Recomputed on every stock change — 32 recipes is nothing, and a
            // cached ranking would go stale the moment something is cooked.
            ideas = if (profile == null || items.isEmpty()) {
                emptyList()
            } else {
                fridgeRepository.cookableNow(profile).take(IDEAS_SHOWN)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FridgeUiState(),
    )

    private val aiLocal = MutableStateFlow(AiUiState())

    val aiState: StateFlow<AiUiState> = combine(aiRepository.hasKey, aiLocal) { hasKey, local ->
        local.copy(hasKey = hasKey)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiUiState(),
    )

    /**
     * One request, on an explicit tap. Never on screen open, never from a flow,
     * never from a worker — it is the user's own key and their own money.
     */
    fun askAi() {
        if (aiLocal.value.loading) return
        viewModelScope.launch {
            aiLocal.update { it.copy(loading = true, result = null) }
            val profile = profileRepository.observeProfile().first()
            val result = if (profile == null) {
                AiResult.Failed
            } else {
                aiRepository.suggest(fridgeRepository.items(), profile)
            }
            aiLocal.update { it.copy(loading = false, result = result) }
        }
    }

    fun dismissAiAnswer() {
        aiLocal.update { it.copy(result = null) }
    }

    /** The catalogue the add dialog picks from; loaded once, seeded on demand. */
    private val _pickable = MutableStateFlow<List<IngredientEntity>>(emptyList())
    val pickable: StateFlow<List<IngredientEntity>> = _pickable.asStateFlow()

    init {
        viewModelScope.launch { _pickable.value = fridgeRepository.pickableIngredients() }
    }

    fun add(ingredient: IngredientEntity, grams: Double) {
        if (grams <= 0.0) return
        viewModelScope.launch { fridgeRepository.add(ingredient.name, ingredient.aisle, grams) }
    }

    /** The correction path: an absolute amount, where 0 g means "gone". */
    fun setGrams(item: FridgeItemUi, grams: Double) {
        viewModelScope.launch { fridgeRepository.setGrams(item.name, item.aisle, grams) }
    }

    fun remove(item: FridgeItemUi) {
        viewModelScope.launch { fridgeRepository.remove(item.name) }
    }

    private companion object {
        const val IDEAS_SHOWN = 5
    }
}
