package com.nutricart.app.ui.common

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.widget.WidgetDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One day's calorie budget: the target (the same rule as Today and the widget) and what is
 * already eaten. The Diary's "898 left of 2,240", Food search's budget chip and the amount
 * sheet's "left after this" are drawn from it.
 */
@Immutable
data class DayBudget(
    val epochDay: Long,
    val targetKcal: Int,
    val eatenKcal: Int,
) {
    /** Negative when the day is over its target. */
    val remainingKcal: Int get() = targetKcal - eatenKcal
}

/**
 * Read-only: the day budget of whichever day the screen shows ([show]). It asks the widget's data
 * source (WidgetDataSource.forDay, so the number can never disagree with Today or the widget) and
 * asks again whenever the day's diary totals change. It writes nothing and changes no other
 * ViewModel; null until the first answer, and while onboarding is not finished.
 */
@HiltViewModel
class DayBudgetViewModel @Inject constructor(
    private val widgetDataSource: WidgetDataSource,
    private val diaryRepository: DiaryRepository,
) : ViewModel() {

    private val day = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val budget: StateFlow<DayBudget?> = day
        .filterNotNull()
        .flatMapLatest { epochDay ->
            // Every change of the day's totals (a log, a delete) re-reads the budget; mapLatest drops
            // a read that a newer change has already made stale.
            diaryRepository.observeDayTotals(epochDay).mapLatest {
                widgetDataSource.forDay(epochDay)?.let { numbers ->
                    DayBudget(epochDay, numbers.targetKcal, numbers.eatenKcal)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day to follow: the Diary's displayed day, or the day Food search logs into. */
    fun show(epochDay: Long) {
        day.value = epochDay
    }
}
