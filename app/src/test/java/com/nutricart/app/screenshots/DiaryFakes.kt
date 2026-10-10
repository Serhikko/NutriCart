package com.nutricart.app.screenshots

import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.ProductSource
import com.nutricart.app.ui.common.DayBudget
import com.nutricart.app.ui.diary.DiaryUiState
import com.nutricart.app.ui.diary.FoodSearchActions
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Extra fake state for the Diary and Food search shots (the day budget, an over-target day, a past
 * day to move to, a product with values out of range). Built on [FakeData], relative to today.
 */
object DiaryFakes {
    private val uk: Boolean get() = Locale.getDefault().language == "uk"
    private fun t(en: String, ukText: String) = if (uk) ukText else en

    /** The harness world's target. */
    const val TARGET = 2240

    /** The day budget as DayBudgetViewModel answers it: today's target and what the diary holds. */
    fun budget(state: DiaryUiState = FakeData.diary(), target: Int = TARGET) =
        DayBudget(state.epochDay, target, state.totals.kcal.toInt())

    /** The budget Food search sees for today (the Diary's day of 1,342 kcal). */
    fun searchBudget() = budget()

    /** The id of the banana in FakeData.diary(): the entry that was "just added" in back-from-Add frames. */
    const val FRESH_ENTRY_ID = 7L

    private fun millisAt(epochDay: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.ofEpochDay(epochDay).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun entry(
        id: Long, day: Long, slot: MealSlot, name: String, grams: Double?, kcal: Double,
        p: Double, f: Double, c: Double, hour: Int, minute: Int = 0, servings: Double? = null,
    ) = FoodLogEntryEntity(
        id = id, epochDay = day, meal = slot, productId = "p$id", name = name, grams = grams,
        servings = servings, kcal = kcal, proteinG = p, fatG = f, carbsG = c,
        loggedAtEpochMillis = millisAt(day, hour, minute),
    )

    private fun state(day: Long, entries: List<FoodLogEntryEntity>, note: String? = null) = DiaryUiState(
        epochDay = day,
        isToday = day == FakeData.today,
        entriesBySlot = entries.groupBy { it.meal },
        totals = DayNutritionTotals(
            kcal = entries.sumOf { it.kcal },
            proteinG = entries.sumOf { it.proteinG },
            fatG = entries.sumOf { it.fatG },
            carbsG = entries.sumOf { it.carbsG },
        ),
        note = note,
    )

    /** Today with a big dinner on top: over the target. */
    fun diaryOver(): DiaryUiState {
        val base = FakeData.diary()
        val day = FakeData.today
        val dinner = listOf(
            entry(20, day, MealSlot.DINNER, t("Pizza margherita", "Піца маргарита"), 450.0, 1103.0, 46.0, 40.0, 140.0, 19, 40),
            entry(21, day, MealSlot.DINNER, t("Cola", "Кола"), 330.0, 139.0, 0.0, 0.0, 35.0, 19, 42),
        )
        return state(day, base.entriesBySlot.values.flatten() + dinner, base.note)
    }

    /** Yesterday: a day to move to with ‹ (no note, no snacks). */
    fun diaryYesterday(): DiaryUiState {
        val day = FakeData.today - 1
        return state(
            day,
            listOf(
                entry(30, day, MealSlot.BREAKFAST, t("Omelette with spinach", "Омлет зі шпинатом"), 220.0, 352.0, 24.0, 26.0, 5.0, 8, 20),
                entry(31, day, MealSlot.LUNCH, "Борщ зі свининою та сметаною", null, 572.0, 30.0, 24.0, 53.0, 13, 10),
                entry(32, day, MealSlot.LUNCH, t("Rye bread with seeds", "Житній хліб з насінням"), 70.0, 167.0, 5.7, 2.9, 28.4, 13, 12),
                entry(33, day, MealSlot.DINNER, t("Baked salmon", "Запечений лосось"), 160.0, 330.0, 32.0, 21.0, 0.0, 19, 5),
                entry(34, day, MealSlot.DINNER, t("Boiled potatoes", "Картопля варена"), 200.0, 172.0, 4.0, 0.2, 39.0, 19, 5),
            ),
        )
    }

    /** One of the user's own foods with values out of range, for the form's danger rings. */
    val outOfRangeProduct: FoodProductEntity
        get() = FoodProductEntity(
            id = "local:granola", name = t("Home granola", "Домашня гранола"), brand = null,
            kcalPer100g = 980.0, proteinPer100g = 12.0, fatPer100g = 140.0, carbsPer100g = 58.0,
            servingSizeG = 45.0, source = ProductSource.LOCAL, cachedAtEpochMillis = 0L,
        )

    /** Food search callbacks that do nothing except collect into the basket (for the basket frames). */
    fun basketActions(onAdd: (FoodProductEntity) -> Unit): FoodSearchActions =
        Proxy.newProxyInstance(FoodSearchActions::class.java.classLoader, arrayOf(FoodSearchActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "addToBasket" -> onAdd(args!![0] as FoodProductEntity).let { null }
                "toString" -> "basketActions"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        } as FoodSearchActions
}
