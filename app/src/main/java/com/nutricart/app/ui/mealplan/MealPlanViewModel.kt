package com.nutricart.app.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.FridgeRepository
import com.nutricart.app.data.repository.PlanRepository
import com.nutricart.app.data.repository.ProfileRepository
import com.nutricart.app.domain.logic.CalorieCalculator
import com.nutricart.app.domain.logic.MealPlanGenerator
import com.nutricart.app.domain.logic.PlanFailureReason
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.RecipeNutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/** One planned meal, ready for display. */
data class PlanMealUi(
    val id: Long,
    val epochDay: Long,
    val slot: MealSlot,
    val position: Int,
    val recipeId: Long,
    val recipeName: String,
    val kcal: Int,
    val portionFactor: Double,
    val isLocked: Boolean,
)

data class PlanDayUi(
    val epochDay: Long,
    val meals: List<PlanMealUi>,
    val totalKcal: Int,
)

data class MealPlanUiState(
    val loading: Boolean = true,
    val days: List<PlanDayUi> = emptyList(),
    val targetKcal: Int = 0,
    val hasPlan: Boolean = false,
    val generating: Boolean = false,
    /** One-shot: the last generation failed; the screen shows it and clears it. */
    val error: PlanFailureReason? = null,
    /** One-shot: a meal was copied to the diary; the screen confirms and clears it. */
    val loggedToDiary: Boolean = false,
)

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val diaryRepository: DiaryRepository,
    private val fridgeRepository: FridgeRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    // The plan covers 7 days starting today. The screen refreshes this on every
    // resume, so after midnight both the view and generation roll to the new day.
    private val weekStart = MutableStateFlow(LocalDate.now().toEpochDay())

    private val generating = MutableStateFlow(false)
    private val error = MutableStateFlow<PlanFailureReason?>(null)
    private val loggedToDiary = MutableStateFlow(false)
    private val nutrition = MutableStateFlow<Map<Long, RecipeNutrition>>(emptyMap())

    private val profileFlow = profileRepository.observeProfile()
    private val weightFlow = profileRepository.observeLatestWeight()

    init {
        // First access also seeds the recipe tables from assets.
        viewModelScope.launch { nutrition.value = planRepository.nutritionById() }
    }

    // (generating, error, loggedToDiary) grouped so the main combine stays small.
    private data class Signals(
        val generating: Boolean,
        val error: PlanFailureReason?,
        val logged: Boolean,
    )

    private val signals = combine(generating, error, loggedToDiary) { g, e, l -> Signals(g, e, l) }

    // Whenever the week rolls over, observe the new date range.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val weekMeals = weekStart.flatMapLatest { start ->
        planRepository.observeWeek(start).map { meals -> start to meals }
    }

    val uiState: StateFlow<MealPlanUiState> = combine(
        weekMeals,
        profileFlow,
        weightFlow,
        nutrition,
        signals,
    ) { (start, meals), profile, weight, byId, sig ->
        if (profile == null || weight == null || byId.isEmpty()) {
            MealPlanUiState(
                loading = true,
                generating = sig.generating,
                error = sig.error,
                loggedToDiary = sig.logged,
            )
        } else {
            val targets = computeTargets(profile, weight.weightKg)
            val days = (0..6).map { offset ->
                val day = start + offset
                val dayMeals = meals
                    .filter { it.epochDay == day }
                    // MealSlot is declared in day order, so ordinal sorts the day.
                    .sortedWith(compareBy({ it.slot.ordinal }, { it.position }))
                    .mapNotNull { row ->
                        byId[row.recipeId]?.let { n ->
                            PlanMealUi(
                                id = row.id,
                                epochDay = row.epochDay,
                                slot = row.slot,
                                position = row.position,
                                recipeId = row.recipeId,
                                recipeName = n.name,
                                kcal = (n.kcal * row.portionFactor).roundToInt(),
                                portionFactor = row.portionFactor,
                                isLocked = row.isLocked,
                            )
                        }
                    }
                PlanDayUi(epochDay = day, meals = dayMeals, totalKcal = dayMeals.sumOf { it.kcal })
            }
            MealPlanUiState(
                loading = false,
                days = days,
                targetKcal = targets.kcal,
                hasPlan = days.any { it.meals.isNotEmpty() },
                generating = sig.generating,
                error = sig.error,
                loggedToDiary = sig.logged,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MealPlanUiState(),
    )

    /** Called on every screen resume — rolls the week if midnight passed. */
    fun refreshWeek() {
        weekStart.value = LocalDate.now().toEpochDay()
    }

    fun generate() {
        if (generating.value) return // double-tap guard
        viewModelScope.launch {
            generating.value = true
            try {
                refreshWeek()
                val profile = profileFlow.filterNotNull().first()
                val weight = weightFlow.filterNotNull().first()
                val recipes = planRepository.recipesForProfile(profile)
                nutrition.value = planRepository.nutritionById()
                error.value = planRepository.generateWeek(
                    startEpochDay = weekStart.value,
                    targets = computeTargets(profile, weight.weightKg),
                    snacksPerDay = profile.snacksPerDay,
                    cookingSessionsPerWeek = profile.cookingSessionsPerWeek,
                    recipes = recipes,
                    generator = MealPlanGenerator(Random.Default),
                )
            } finally {
                // finally: the button must never stay stuck on "generating"
                // if anything above throws.
                generating.value = false
            }
        }
    }

    fun toggleLock(meal: PlanMealUi) {
        viewModelScope.launch { planRepository.setLocked(meal.id, !meal.isLocked) }
    }

    fun swap(meal: PlanMealUi) {
        viewModelScope.launch {
            val profile = profileFlow.filterNotNull().first()
            planRepository.swapMeal(meal.id, planRepository.recipesForProfile(profile), Random.Default)
        }
    }

    /** Copies the meal's scaled nutrition into that day's diary as a snapshot. */
    /**
     * "I cooked this", from the row where the meal already is — cooking starts
     * on this screen far more often than on the recipe screen.
     *
     * It deducts stock and NOTHING else: the diary keeps its own separate "+",
     * so cooking on Sunday for Monday can never create an entry on the wrong
     * day. [portions] covers batch cooking, where one pot feeds several days.
     */
    fun cook(meal: PlanMealUi, portions: Int) {
        viewModelScope.launch {
            fridgeRepository.cook(meal.recipeId, meal.portionFactor, portions)
        }
    }

    fun addToDiary(meal: PlanMealUi) {
        viewModelScope.launch {
            // The diary can only browse up to today, so a FUTURE-day entry
            // would be invisible and undeletable until that day arrives. The
            // screen hides the button on future days; this guard covers a
            // stale UI (e.g. the list rendered before midnight).
            if (meal.epochDay > LocalDate.now().toEpochDay()) return@launch
            val n = nutrition.value[meal.recipeId] ?: return@launch
            diaryRepository.logSnapshot(
                name = n.name,
                kcal = n.kcal * meal.portionFactor,
                proteinG = n.proteinG * meal.portionFactor,
                fatG = n.fatG * meal.portionFactor,
                carbsG = n.carbsG * meal.portionFactor,
                meal = meal.slot,
                epochDay = meal.epochDay,
            )
            // Confirmation fires only AFTER the insert actually happened.
            loggedToDiary.value = true
        }
    }

    fun clearError() {
        error.value = null
    }

    fun clearLoggedToDiary() {
        loggedToDiary.value = false
    }

    private fun computeTargets(profile: UserProfileEntity, weightKg: Double): DailyTargets {
        // Manual override wins: the plan aims at exactly the user's numbers.
        if (profile.customKcalTarget != null && profile.customProteinG != null &&
            profile.customFatG != null && profile.customCarbsG != null
        ) {
            return DailyTargets(
                kcal = profile.customKcalTarget,
                proteinG = profile.customProteinG,
                fatG = profile.customFatG,
                carbsG = profile.customCarbsG,
            )
        }
        val age = CalorieCalculator.ageYears(
            LocalDate.ofEpochDay(profile.birthDateEpochDay),
            LocalDate.now(),
        )
        // The plan always aims at the BASE target — future activity is unknown;
        // the watch-adjusted number is a dashboard-only concept.
        val kcal = CalorieCalculator.baseTargetKcal(
            profile.sex, weightKg, profile.heightCm.toDouble(), age,
            profile.activityLevel, profile.goal, profile.targetKgPerWeek,
        )
        return CalorieCalculator.macroTargets(kcal, weightKg)
    }
}
