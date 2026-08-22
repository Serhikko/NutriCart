package com.nutricart.app.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.data.repository.WaterRepository
import com.nutricart.app.data.repository.WorkoutRepository
import com.nutricart.app.domain.model.WorkoutKind
import com.nutricart.app.domain.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The three write actions of the quick-add sheet that do not leave it: water,
 * a workout and today's weight. Food and the scanner navigate instead, so they
 * are not here.
 *
 * Every write reads the day at the moment it happens — this view model outlives
 * the sheet, so a cached "today" would file a midnight entry under yesterday.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val waterRepository: WaterRepository,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** Latest known weight — only for the workout dialog's kcal preview. */
    val weightKg: StateFlow<Double> = profileRepository.observeLatestWeight()
        .map { it?.weightKg ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** Today's water total, for the row's own feedback after a tap. */
    fun observeWater(epochDay: Long): Flow<Int> = waterRepository.observeDayTotal(epochDay)

    private fun today(): Long = LocalDate.now().toEpochDay()

    fun addWater(ml: Int) {
        viewModelScope.launch { waterRepository.add(today(), ml) }
    }

    fun undoWater() {
        viewModelScope.launch { waterRepository.undoLast(today()) }
    }

    fun addWorkout(type: WorkoutType, amount: Int) {
        viewModelScope.launch {
            // Read the weight fresh instead of trusting the preview's cached
            // copy: with no collector that StateFlow can still hold its 0.0.
            val weight = profileRepository.observeLatestWeight().first()?.weightKg ?: return@launch
            if (weight <= 0.0 || amount <= 0) return@launch
            val day = today()
            when (type.kind) {
                WorkoutKind.DURATION -> workoutRepository.addDuration(day, type, weight, amount)
                WorkoutKind.REPS -> workoutRepository.addReps(day, type, weight, amount)
            }
        }
    }

    /**
     * Writes today's weight — but only when the number actually changed, the
     * same rule the settings form follows. A MANUAL row permanently outranks
     * the watch reading for that day and nothing in the UI can delete it, so
     * confirming a prefilled value must not quietly shadow real watch data.
     */
    fun logWeight(weightKg: Double) {
        viewModelScope.launch {
            val latest = profileRepository.observeLatestWeight().first()?.weightKg
            if (latest == weightKg) return@launch
            profileRepository.logWeight(weightKg, today())
        }
    }
}
