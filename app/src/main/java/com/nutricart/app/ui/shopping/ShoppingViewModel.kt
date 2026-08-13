package com.nutricart.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import com.nutricart.app.data.repository.PlanRepository
import com.nutricart.app.data.repository.ShoppingRepository
import com.nutricart.app.domain.logic.ShoppingListBuilder
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.grocery.ManualExportProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One list line, ready for display. */
data class ShoppingItemUi(
    val id: Long,
    val name: String,
    val aisle: Aisle,
    val displayGrams: Int,
    val pieces: Int?,
    val isChecked: Boolean,
    val alreadyHave: Boolean,
)

data class ShoppingUiState(
    val loading: Boolean = true,
    /** The 7 plan days shown as selectable chips. */
    val weekDays: List<Long> = emptyList(),
    val selectedDays: Set<Long> = emptySet(),
    val itemsByAisle: Map<Aisle, List<ShoppingItemUi>> = emptyMap(),
    val hasPlan: Boolean = false,
    val hasList: Boolean = false,
    val generating: Boolean = false,
    /** Raw rows for the text export. */
    val rawItems: List<ShoppingListItemEntity> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    planRepository: PlanRepository,
    private val exportProvider: ManualExportProvider,
) : ViewModel() {

    // The chips follow the meal-plan week (rolls forward on screen resume);
    // the LIST itself is a single persistent thing and does not roll.
    private val weekStart = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedDays = MutableStateFlow<Set<Long>>(emptySet())
    private val generating = MutableStateFlow(false)

    // Guards the persisted selection from overwriting a fresh user tap
    // if the DataStore read finishes late.
    private var userTouchedSelection = false

    init {
        viewModelScope.launch {
            val persisted = shoppingRepository.observeSelectedDays().first()
            if (!userTouchedSelection) selectedDays.value = persisted
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val planWeek = weekStart.flatMapLatest { start ->
        planRepository.observeWeek(start).map { meals -> start to meals }
    }

    val uiState: StateFlow<ShoppingUiState> = combine(
        planWeek,
        shoppingRepository.observeList(),
        selectedDays,
        generating,
    ) { (start, meals), items, selected, isGenerating ->
        val weekDays = (0..6).map { start + it }
        ShoppingUiState(
            loading = false,
            weekDays = weekDays,
            selectedDays = effectiveSelection(selected, weekDays),
            itemsByAisle = items
                .map { row ->
                    ShoppingItemUi(
                        id = row.id,
                        name = row.ingredientName,
                        aisle = row.aisle,
                        displayGrams = ShoppingListBuilder.displayGrams(row.totalGrams),
                        pieces = row.pieces,
                        isChecked = row.isChecked,
                        alreadyHave = row.alreadyHave,
                    )
                }
                // The DB returns rows in unspecified order — sort where consumed,
                // so aisles appear in store-walk (enum) order.
                .sortedWith(compareBy({ it.aisle.ordinal }, { it.name }))
                .groupBy { it.aisle },
            hasPlan = meals.isNotEmpty(),
            hasList = items.isNotEmpty(),
            generating = isGenerating,
            rawItems = items,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingUiState(),
    )

    /** Old selections (from a past week) fall back to "all 7 days". */
    private fun effectiveSelection(selected: Set<Long>, weekDays: List<Long>): Set<Long> {
        val inWeek = selected.filter { it in weekDays }.toSet()
        return inWeek.ifEmpty { weekDays.toSet() }
    }

    fun refreshWeek() {
        weekStart.value = LocalDate.now().toEpochDay()
    }

    fun toggleDay(day: Long) {
        userTouchedSelection = true
        val current = effectiveSelection(selectedDays.value, uiState.value.weekDays)
        // At least one day stays selected. Allowing zero would feed the
        // empty set back into effectiveSelection, whose empty->"all 7 days"
        // fallback (meant for STALE past-week selections) would instantly
        // re-light every chip — the opposite of what the user just did.
        if (day in current && current.size == 1) return
        selectedDays.value =
            if (day in current) current - day else current + day
    }

    fun regenerate() {
        if (generating.value) return
        viewModelScope.launch {
            generating.value = true
            try {
                val days = effectiveSelection(selectedDays.value, uiState.value.weekDays)
                shoppingRepository.regenerate(days)
            } finally {
                generating.value = false
            }
        }
    }

    fun setChecked(item: ShoppingItemUi, checked: Boolean) {
        viewModelScope.launch { shoppingRepository.setChecked(item.id, checked) }
    }

    fun toggleAlreadyHave(item: ShoppingItemUi) {
        viewModelScope.launch { shoppingRepository.setAlreadyHave(item.id, !item.alreadyHave) }
    }

    /** The screen supplies translated labels; the provider formats the text. */
    fun buildShareText(
        aisleLabel: (Aisle) -> String,
        amountLabel: (displayGrams: Int, pieces: Int?) -> String,
    ): String = exportProvider.buildShareText(uiState.value.rawItems, aisleLabel, amountLabel)
}
