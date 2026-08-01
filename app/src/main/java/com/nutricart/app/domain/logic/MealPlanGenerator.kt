package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.RecipeNutrition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** One meal of a generated day (before it is saved to the database). */
data class PlannedMealDraft(
    val slot: MealSlot,
    /** 0 for single meals; snacks can repeat (0, 1). */
    val position: Int,
    val recipe: RecipeNutrition,
    /** Scales every ingredient: eaten grams = recipe grams * portionFactor. */
    val portionFactor: Double,
    val isLocked: Boolean = false,
)

sealed interface PlanDayResult {
    data class Success(val meals: List<PlannedMealDraft>) : PlanDayResult
    data class Failure(val reason: PlanFailureReason) : PlanDayResult
}

enum class PlanFailureReason {
    /** The diet filters left zero candidate recipes for some slot. */
    NO_RECIPES_FOR_SLOT,

    /** No portion sizes within the allowed bounds can land in the kcal band
     *  (often caused by locked meals taking up too much of the day). */
    TARGET_UNREACHABLE,
}

/** Removes recipes the user's diet forbids. Exact enum matching, never text. */
fun filterRecipesForDiet(
    recipes: List<RecipeNutrition>,
    isVegetarian: Boolean,
    noPork: Boolean,
    allergies: Set<Allergen>,
): List<RecipeNutrition> = recipes.filter { recipe ->
    (!isVegetarian || recipe.isVegetarian) &&
        (!noPork || !recipe.containsPork) &&
        recipe.allergens.intersect(allergies).isEmpty()
}

/**
 * Generates one day of meals that lands within ±5% of the kcal target.
 *
 * How it works (simple by design):
 *  1. The day is split into slots with rough kcal shares (see [slotTemplate]).
 *  2. Up to [ATTEMPTS] random candidate days are built: pick a random recipe
 *     per free slot, size the portions toward the target, clamp portions to
 *     [MIN_FACTOR]..[MAX_FACTOR] (no absurd triple dinners).
 *  3. Among candidates inside the ±5% kcal band, the BEST one is kept, scored
 *     by the spec's priority: protein first, then fat (carbs are determined
 *     arithmetically once kcal, protein and fat are fixed, so they need no
 *     score of their own).
 *  4. Locked meals are untouchable: their kcal is subtracted from the target
 *     first, and only the remaining slots are generated.
 *
 * Random comes from outside, so tests pass a fixed seed and are deterministic.
 */
class MealPlanGenerator(private val random: Random) {

    companion object {
        const val MIN_FACTOR = 0.5
        const val MAX_FACTOR = 2.0
        const val KCAL_TOLERANCE = 0.05
        const val ATTEMPTS = 80

        /**
         * Portion factors are kept to 0.05 steps — "×1.35", never "×1.3482".
         * roundToInt (nearest), NOT toInt (truncation): truncation would bias
         * every portion downward (0.7/0.05 is 13.999... in floating point).
         */
        fun roundFactor(factor: Double): Double =
            (factor / 0.05).roundToInt() * 0.05

        /**
         * Batch cooking: splits the week into [sessionsPerWeek] CONTIGUOUS
         * groups of (almost) equal size — the user cooks once per group and
         * eats that menu on every day of the group. Repeating a whole day
         * keeps it inside the ±5% band automatically, because every day has
         * the same calorie target. 7 days / 3 sessions -> sizes 3, 2, 2.
         */
        fun buildCookingGroups(days: List<Long>, sessionsPerWeek: Int): List<List<Long>> {
            val sessions = sessionsPerWeek.coerceIn(1, days.size)
            val baseSize = days.size / sessions
            val extra = days.size % sessions // the first `extra` groups get one more day
            val groups = mutableListOf<List<Long>>()
            var index = 0
            repeat(sessions) { group ->
                val size = baseSize + if (group < extra) 1 else 0
                groups += days.subList(index, index + size)
                index += size
            }
            return groups
        }
    }

    /** Which slot gets which rough share of the day's calories. */
    data class SlotSpec(val slot: MealSlot, val position: Int, val share: Double)

    fun slotTemplate(snacksPerDay: Int): List<SlotSpec> = when (snacksPerDay) {
        0 -> listOf(
            SlotSpec(MealSlot.BREAKFAST, 0, 0.30),
            SlotSpec(MealSlot.LUNCH, 0, 0.40),
            SlotSpec(MealSlot.DINNER, 0, 0.30),
        )
        1 -> listOf(
            SlotSpec(MealSlot.BREAKFAST, 0, 0.25),
            SlotSpec(MealSlot.LUNCH, 0, 0.35),
            SlotSpec(MealSlot.DINNER, 0, 0.30),
            SlotSpec(MealSlot.SNACK, 0, 0.10),
        )
        else -> listOf(
            SlotSpec(MealSlot.BREAKFAST, 0, 0.25),
            SlotSpec(MealSlot.LUNCH, 0, 0.30),
            SlotSpec(MealSlot.DINNER, 0, 0.25),
            SlotSpec(MealSlot.SNACK, 0, 0.10),
            SlotSpec(MealSlot.SNACK, 1, 0.10),
        )
    }

    fun generateDay(
        targets: DailyTargets,
        recipes: List<RecipeNutrition>,
        snacksPerDay: Int,
        locked: List<PlannedMealDraft> = emptyList(),
    ): PlanDayResult {
        val template = slotTemplate(snacksPerDay)
        val lockedKeys = locked.map { it.slot to it.position }.toSet()
        val freeSpecs = template.filter { (it.slot to it.position) !in lockedKeys }

        val lockedKcal = locked.sumOf { it.recipe.kcal * it.portionFactor }
        val lockedProtein = locked.sumOf { it.recipe.proteinG * it.portionFactor }
        val lockedFat = locked.sumOf { it.recipe.fatG * it.portionFactor }
        val residualKcal = targets.kcal - lockedKcal

        // Everything is locked: the "plan" is just the locked meals — valid
        // only if they alone land in the band.
        if (freeSpecs.isEmpty()) {
            val err = abs(lockedKcal - targets.kcal) / targets.kcal
            return if (err <= KCAL_TOLERANCE) PlanDayResult.Success(sorted(locked))
            else PlanDayResult.Failure(PlanFailureReason.TARGET_UNREACHABLE)
        }
        // Locked meals already reach or overshoot the day's target — we fail
        // even if a tiny extra portion could still squeeze into the +5% band.
        // Deliberate simplification: "locked meals ate the whole budget" is an
        // honest, explainable answer; the user unlocks something and retries.
        if (residualKcal <= 0) {
            return PlanDayResult.Failure(PlanFailureReason.TARGET_UNREACHABLE)
        }

        val candidatesBySpec = freeSpecs.associateWith { spec ->
            recipes.filter { spec.slot in it.slots }
        }
        if (candidatesBySpec.values.any { it.isEmpty() }) {
            return PlanDayResult.Failure(PlanFailureReason.NO_RECIPES_FOR_SLOT)
        }

        val freeShareSum = freeSpecs.sumOf { it.share }
        var best: List<PlannedMealDraft>? = null
        var bestScore = Double.MAX_VALUE

        repeat(ATTEMPTS) {
            var meals = freeSpecs.map { spec ->
                val recipe = candidatesBySpec.getValue(spec).random(random)
                val slotKcal = residualKcal * (spec.share / freeShareSum)
                val factor = (slotKcal / recipe.kcal).coerceIn(MIN_FACTOR, MAX_FACTOR)
                PlannedMealDraft(spec.slot, spec.position, recipe, roundFactor(factor))
            }
            // Clamping above may have moved the total off target; two uniform
            // correction passes push it back as far as the bounds allow.
            repeat(2) {
                val current = meals.sumOf { it.recipe.kcal * it.portionFactor }
                if (current > 0) {
                    val scale = residualKcal / current
                    meals = meals.map {
                        it.copy(
                            portionFactor =
                                roundFactor((it.portionFactor * scale).coerceIn(MIN_FACTOR, MAX_FACTOR))
                        )
                    }
                }
            }

            val dayKcal = lockedKcal + meals.sumOf { it.recipe.kcal * it.portionFactor }
            val kcalError = abs(dayKcal - targets.kcal) / targets.kcal
            if (kcalError > KCAL_TOLERANCE) return@repeat // hard constraint

            // Priority scoring: missing protein hurts 10x more than a fat miss.
            val protein = lockedProtein + meals.sumOf { it.recipe.proteinG * it.portionFactor }
            val fat = lockedFat + meals.sumOf { it.recipe.fatG * it.portionFactor }
            val proteinShortfall = max(0.0, targets.proteinG - protein) / max(1.0, targets.proteinG.toDouble())
            val fatMiss = abs(targets.fatG - fat) / max(1.0, targets.fatG.toDouble())
            val score = proteinShortfall * 10.0 + fatMiss

            if (score < bestScore) {
                bestScore = score
                best = meals
            }
        }

        val result = best ?: return PlanDayResult.Failure(PlanFailureReason.TARGET_UNREACHABLE)
        return PlanDayResult.Success(sorted(locked + result))
    }

    private fun sorted(meals: List<PlannedMealDraft>): List<PlannedMealDraft> =
        // MealSlot declares its entries in day order, so ordinal is the sort key.
        meals.sortedWith(compareBy({ it.slot.ordinal }, { it.position }))
}
