package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.RecipeNutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MealPlanGeneratorTest {

    // Fixed seed -> the generator's random picks are the same on every run.
    private fun generator() = MealPlanGenerator(Random(42))

    private var nextId = 1L
    private fun recipe(
        name: String,
        slots: Set<MealSlot>,
        kcal: Double,
        proteinG: Double = kcal * 0.05, // ~5 g protein per 100 kcal by default
        fatG: Double = kcal * 0.03,
        carbsG: Double = kcal * 0.12,
        isVegetarian: Boolean = false,
        containsPork: Boolean = false,
        allergens: Set<Allergen> = emptySet(),
    ) = RecipeNutrition(
        id = nextId++, name = name, slots = slots,
        kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG,
        isVegetarian = isVegetarian, containsPork = containsPork,
        allergens = allergens, cookTimeMin = 20,
    )

    /** A workable set: several options per slot around typical sizes. */
    private fun standardRecipes() = listOf(
        recipe("Порідж", setOf(MealSlot.BREAKFAST), 420.0, proteinG = 16.0),
        recipe("Омлет", setOf(MealSlot.BREAKFAST), 450.0, proteinG = 28.0),
        recipe("Сирники", setOf(MealSlot.BREAKFAST), 500.0, proteinG = 30.0),
        recipe("Борщ", setOf(MealSlot.LUNCH), 550.0, proteinG = 25.0),
        recipe("Плов", setOf(MealSlot.LUNCH, MealSlot.DINNER), 650.0, proteinG = 30.0),
        recipe("Паста", setOf(MealSlot.LUNCH, MealSlot.DINNER), 600.0, proteinG = 26.0),
        recipe("Риба з овочами", setOf(MealSlot.DINNER), 500.0, proteinG = 35.0),
        recipe("Салат з куркою", setOf(MealSlot.DINNER), 450.0, proteinG = 32.0),
        recipe("Йогурт з горіхами", setOf(MealSlot.SNACK), 220.0, proteinG = 12.0),
        recipe("Яблуко з арахісовою пастою", setOf(MealSlot.SNACK), 250.0, proteinG = 8.0),
    )

    private val targets = DailyTargets(kcal = 2200, proteinG = 130, fatG = 61, carbsG = 298)

    // ---------- Success path ----------

    @Test
    fun `generated day lands within 5 percent of the kcal target`() {
        val result = generator().generateDay(targets, standardRecipes(), snacksPerDay = 1)

        val meals = (result as PlanDayResult.Success).meals
        val dayKcal = meals.sumOf { it.recipe.kcal * it.portionFactor }
        assertTrue(
            "day kcal $dayKcal is outside ±5% of ${targets.kcal}",
            abs(dayKcal - targets.kcal) / targets.kcal <= MealPlanGenerator.KCAL_TOLERANCE,
        )
    }

    @Test
    fun `every portion factor stays within the allowed bounds`() {
        val result = generator().generateDay(targets, standardRecipes(), snacksPerDay = 1)

        (result as PlanDayResult.Success).meals.forEach { meal ->
            assertTrue(meal.portionFactor >= MealPlanGenerator.MIN_FACTOR)
            assertTrue(meal.portionFactor <= MealPlanGenerator.MAX_FACTOR)
        }
    }

    @Test
    fun `day template matches the chosen number of snacks`() {
        val gen = generator()
        val recipes = standardRecipes()

        val none = gen.generateDay(targets, recipes, snacksPerDay = 0) as PlanDayResult.Success
        assertEquals(0, none.meals.count { it.slot == MealSlot.SNACK })
        assertEquals(3, none.meals.size)

        val two = gen.generateDay(targets, recipes, snacksPerDay = 2) as PlanDayResult.Success
        assertEquals(2, two.meals.count { it.slot == MealSlot.SNACK })
        assertEquals(listOf(0, 1), two.meals.filter { it.slot == MealSlot.SNACK }.map { it.position })
    }

    @Test
    fun `protein-first scoring picks the high-protein breakfast`() {
        // Two breakfasts, identical kcal, wildly different protein. With a
        // demanding protein target the scorer must keep the high-protein one.
        val highProtein = recipe("Скір", setOf(MealSlot.BREAKFAST), 450.0, proteinG = 40.0)
        val lowProtein = recipe("Круасан", setOf(MealSlot.BREAKFAST), 450.0, proteinG = 6.0)
        val recipes = listOf(
            highProtein, lowProtein,
            recipe("Борщ", setOf(MealSlot.LUNCH), 600.0, proteinG = 30.0),
            recipe("Риба", setOf(MealSlot.DINNER), 550.0, proteinG = 35.0),
            recipe("Йогурт", setOf(MealSlot.SNACK), 220.0, proteinG = 12.0),
        )

        val result = generator().generateDay(targets, recipes, snacksPerDay = 1)

        val breakfast = (result as PlanDayResult.Success).meals.first { it.slot == MealSlot.BREAKFAST }
        assertEquals(highProtein.name, breakfast.recipe.name)
    }

    // ---------- Locked meals ----------

    @Test
    fun `locked meal survives regeneration untouched and the day still fits`() {
        val lockedDinner = PlannedMealDraft(
            slot = MealSlot.DINNER, position = 0,
            recipe = recipe("Улюблена піца", setOf(MealSlot.DINNER), 700.0, proteinG = 30.0),
            portionFactor = 1.0, isLocked = true,
        )

        val result = generator().generateDay(
            targets, standardRecipes(), snacksPerDay = 1, locked = listOf(lockedDinner),
        )

        val meals = (result as PlanDayResult.Success).meals
        val dinner = meals.first { it.slot == MealSlot.DINNER }
        assertEquals("Улюблена піца", dinner.recipe.name)
        assertEquals(1.0, dinner.portionFactor, 0.0001)

        val dayKcal = meals.sumOf { it.recipe.kcal * it.portionFactor }
        assertTrue(abs(dayKcal - targets.kcal) / targets.kcal <= MealPlanGenerator.KCAL_TOLERANCE)
    }

    @Test
    fun `locked meals that overshoot the whole day fail with a clear reason`() {
        val hugeLocked = PlannedMealDraft(
            slot = MealSlot.DINNER, position = 0,
            recipe = recipe("Бенкет", setOf(MealSlot.DINNER), 2500.0),
            portionFactor = 1.0, isLocked = true,
        )

        val result = generator().generateDay(
            targets, standardRecipes(), snacksPerDay = 1, locked = listOf(hugeLocked),
        )

        assertEquals(
            PlanFailureReason.TARGET_UNREACHABLE,
            (result as PlanDayResult.Failure).reason,
        )
    }

    // ---------- Failure paths ----------

    @Test
    fun `missing snack recipes fail with NO_RECIPES_FOR_SLOT`() {
        val noSnacks = standardRecipes().filterNot { MealSlot.SNACK in it.slots }

        val result = generator().generateDay(targets, noSnacks, snacksPerDay = 1)

        assertEquals(
            PlanFailureReason.NO_RECIPES_FOR_SLOT,
            (result as PlanDayResult.Failure).reason,
        )
    }

    @Test
    fun `impossible small target fails with TARGET_UNREACHABLE`() {
        // Only huge recipes: even at the minimum factor 0.5 three meals
        // cannot get down to 1200 kcal.
        val huge = listOf(
            recipe("Велетень 1", setOf(MealSlot.BREAKFAST), 900.0),
            recipe("Велетень 2", setOf(MealSlot.LUNCH), 950.0),
            recipe("Велетень 3", setOf(MealSlot.DINNER), 900.0),
        )
        val small = DailyTargets(kcal = 1200, proteinG = 90, fatG = 33, carbsG = 120)

        val result = generator().generateDay(small, huge, snacksPerDay = 0)

        assertEquals(
            PlanFailureReason.TARGET_UNREACHABLE,
            (result as PlanDayResult.Failure).reason,
        )
    }

    // ---------- Cooking groups (batch cooking) ----------

    @Test
    fun `cooking every day makes seven groups of one`() {
        val days = (0L..6L).map { 20_000L + it }
        val groups = MealPlanGenerator.buildCookingGroups(days, sessionsPerWeek = 7)
        assertEquals(7, groups.size)
        assertTrue(groups.all { it.size == 1 })
    }

    @Test
    fun `three cooking sessions split the week into 3 plus 2 plus 2 days`() {
        val days = (0L..6L).map { 20_000L + it }
        val groups = MealPlanGenerator.buildCookingGroups(days, sessionsPerWeek = 3)
        assertEquals(listOf(3, 2, 2), groups.map { it.size })
        // Groups are contiguous and cover the whole week in order.
        assertEquals(days, groups.flatten())
    }

    @Test
    fun `four cooking sessions split the week into 2 2 2 1`() {
        val days = (0L..6L).map { 20_000L + it }
        val groups = MealPlanGenerator.buildCookingGroups(days, sessionsPerWeek = 4)
        assertEquals(listOf(2, 2, 2, 1), groups.map { it.size })
    }

    // ---------- Diet filter ----------

    @Test
    fun `diet filter removes forbidden recipes exactly`() {
        val veggie = recipe("Овочеве рагу", setOf(MealSlot.DINNER), 400.0, isVegetarian = true)
        val pork = recipe("Свинячі реберця", setOf(MealSlot.DINNER), 700.0, containsPork = true)
        val nutty = recipe(
            "Горіховий батончик", setOf(MealSlot.SNACK), 250.0,
            isVegetarian = true, allergens = setOf(Allergen.NUTS),
        )
        val all = listOf(veggie, pork, nutty)

        assertEquals(listOf(veggie, nutty), filterRecipesForDiet(all, true, false, emptySet()))
        assertEquals(listOf(veggie, nutty), filterRecipesForDiet(all, false, true, emptySet()))
        assertEquals(listOf(veggie, pork), filterRecipesForDiet(all, false, false, setOf(Allergen.NUTS)))
        assertEquals(listOf(veggie), filterRecipesForDiet(all, true, true, setOf(Allergen.NUTS)))
    }
}
