package com.nutricart.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.SavedMealRepository
import com.nutricart.app.domain.model.MealSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DiaryUiState(
    val epochDay: Long,
    val isToday: Boolean = true,
    val entriesBySlot: Map<MealSlot, List<FoodLogEntryEntity>> = emptyMap(),
    val totals: DayNutritionTotals = DayNutritionTotals(0.0, 0.0, 0.0, 0.0),
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val savedMealRepository: SavedMealRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Non-null shows the "name this meal" dialog for that diary section. */
    val savingSlot = MutableStateFlow<MealSlot?>(null)

    /** One-shot: a meal was just saved; the screen shows a snackbar and clears it. */
    val mealSaved = MutableStateFlow(false)

    // Backed by SavedStateHandle so the chosen day survives process death.
    private val selectedDay: StateFlow<Long> =
        savedStateHandle.getStateFlow(KEY_SELECTED_DAY, LocalDate.now().toEpochDay())

    // What "today" means; the screen refreshes it on every resume, so the
    // forward-arrow limit stays correct even after midnight in the background.
    private val todayEpochDay = MutableStateFlow(LocalDate.now().toEpochDay())

    /** The day shown right now — the screen passes it to the food search. */
    val currentEpochDay: Long
        get() = selectedDay.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DiaryUiState> =
        combine(selectedDay, todayEpochDay) { selected, today -> selected to today }
            .flatMapLatest { (selected, today) ->
                combine(
                    diaryRepository.observeDay(selected),
                    diaryRepository.observeDayTotals(selected),
                ) { entries, totals ->
                    DiaryUiState(
                        epochDay = selected,
                        isToday = selected == today,
                        entriesBySlot = entries.groupBy { it.meal },
                        totals = totals,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DiaryUiState(epochDay = LocalDate.now().toEpochDay()),
            )

    fun refreshToday() {
        todayEpochDay.value = LocalDate.now().toEpochDay()
    }

    fun previousDay() {
        savedStateHandle[KEY_SELECTED_DAY] = selectedDay.value - 1
    }

    /** Browsing stops at today — there is nothing to see in the future yet. */
    fun nextDay() {
        val today = LocalDate.now().toEpochDay()
        if (selectedDay.value < today) {
            savedStateHandle[KEY_SELECTED_DAY] = selectedDay.value + 1
        }
    }

    fun delete(entry: FoodLogEntryEntity) {
        viewModelScope.launch { diaryRepository.delete(entry) }
    }

    fun startSavingMeal(slot: MealSlot) {
        savingSlot.value = slot
    }

    fun cancelSavingMeal() {
        savingSlot.value = null
    }

    /** Freezes the current entries of the chosen section under the given name. */
    fun saveMeal(name: String) {
        val slot = savingSlot.value ?: return
        savingSlot.value = null
        val entries = uiState.value.entriesBySlot[slot].orEmpty()
        if (name.isBlank() || entries.isEmpty()) return
        viewModelScope.launch {
            savedMealRepository.saveFromEntries(name.trim(), entries)
            mealSaved.value = true
        }
    }

    fun clearMealSaved() {
        mealSaved.value = false
    }

    companion object {
        private const val KEY_SELECTED_DAY = "selectedDay"
    }
}
