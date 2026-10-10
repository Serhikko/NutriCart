package com.nutricart.app.screenshots

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.nutricart.app.cloud.CloudAccount
import com.nutricart.app.cloud.CloudPartner
import com.nutricart.app.data.local.dao.DayNutritionTotals
import com.nutricart.app.data.local.dao.FoodContribution
import com.nutricart.app.data.local.dao.IngredientAmountRow
import com.nutricart.app.data.local.dao.SavedMealSummary
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.data.repository.RecipeDetails
import com.nutricart.app.data.repository.TopSources
import com.nutricart.app.domain.logic.AdherenceCalculator.DayState
import com.nutricart.app.domain.logic.DietInsights
import com.nutricart.app.domain.logic.FridgeMath
import com.nutricart.app.domain.logic.WeightTrendCalculator
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.ProductPrefill
import com.nutricart.app.domain.model.ProductSource
import com.nutricart.app.domain.model.RecipeNutrition
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.domain.model.WorkoutType
import com.nutricart.app.ui.dashboard.DashboardUiState
import com.nutricart.app.ui.dashboard.HcBannerState
import com.nutricart.app.ui.dashboard.TodayMenuItem
import com.nutricart.app.ui.dashboard.WorkoutItem
import com.nutricart.app.ui.diary.BasketItem
import com.nutricart.app.ui.diary.DiaryUiState
import com.nutricart.app.ui.diary.FoodSearchUiState
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeItemUi
import com.nutricart.app.ui.fridge.FridgeUiState
import com.nutricart.app.ui.mealplan.MealPlanUiState
import com.nutricart.app.ui.mealplan.PlanDayUi
import com.nutricart.app.ui.mealplan.PlanMealUi
import com.nutricart.app.ui.mealplan.RecipeDetailUiState
import com.nutricart.app.ui.onboarding.OnboardingPreview
import com.nutricart.app.ui.onboarding.OnboardingUiState
import com.nutricart.app.ui.settings.ReminderUi
import com.nutricart.app.ui.settings.SettingsUiState
import com.nutricart.app.ui.shopping.ShoppingItemUi
import com.nutricart.app.ui.shopping.ShoppingUiState
import com.nutricart.app.ui.stats.DayBar
import com.nutricart.app.ui.stats.StatsRange
import com.nutricart.app.ui.stats.StatsUiState
import java.lang.reflect.Proxy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Realistic, deterministic fake state for every screen. Dates are relative to
 * the real "today" because the screens themselves call LocalDate.now().
 *
 * User-typed things (product names, notes) follow the shot's language; recipe
 * and ingredient names stay Ukrainian in every language, exactly as the
 * bundled recipes.json makes them look in the real app.
 */
object FakeData {
    private val uk: Boolean get() = Locale.getDefault().language == "uk"
    private fun t(en: String, ukText: String) = if (uk) ukText else en

    val today: Long get() = LocalDate.now().toEpochDay()
    private fun millisAt(epochDay: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.ofEpochDay(epochDay).atTime(hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** A no-op implementation of any callbacks interface (XxxActions). */
    inline fun <reified T : Any> noActions(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "noActions<${T::class.simpleName}>"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        } as T

    // ---------- Recipes (names as in assets/recipes.json) ----------

    private fun recipe(id: Long, name: String, kcal: Double, p: Double, f: Double, c: Double, slot: MealSlot, min: Int) =
        RecipeNutrition(id, name, setOf(slot), kcal, p, f, c, isVegetarian = false, containsPork = false, allergens = emptySet(), cookTimeMin = min)

    val oatmeal = recipe(1, "Вівсянка з бананом і волоськими горіхами", 420.0, 13.0, 14.0, 62.0, MealSlot.BREAKFAST, 10)
    val syrnyky = recipe(2, "Сирники зі сметаною", 460.0, 28.0, 18.0, 44.0, MealSlot.BREAKFAST, 25)
    val omelette = recipe(3, "Омлет зі шпинатом і сиром", 380.0, 26.0, 27.0, 6.0, MealSlot.BREAKFAST, 12)
    val borscht = recipe(9, "Борщ зі свининою та сметаною", 520.0, 27.0, 22.0, 48.0, MealSlot.LUNCH, 90)
    val buckwheat = recipe(10, "Гречка з курячим філе та овочами", 560.0, 42.0, 12.0, 68.0, MealSlot.LUNCH, 35)
    val plov = recipe(12, "Плов з куркою", 610.0, 38.0, 18.0, 72.0, MealSlot.LUNCH, 60)
    val salmon = recipe(19, "Запечений лосось з картоплею і броколі", 590.0, 39.0, 26.0, 46.0, MealSlot.DINNER, 40)
    val turkey = recipe(15, "Тушкована індичка з гречкою", 540.0, 44.0, 14.0, 58.0, MealSlot.DINNER, 45)
    val tofu = recipe(24, "Тофу з броколі та рисом", 480.0, 24.0, 15.0, 62.0, MealSlot.DINNER, 25)
    val yogurt = recipe(25, "Йогурт з ягодами і медом", 210.0, 12.0, 4.0, 32.0, MealSlot.SNACK, 3)
    val apple = recipe(26, "Яблуко з арахісовою пастою", 230.0, 6.0, 13.0, 24.0, MealSlot.SNACK, 2)
    val hummus = recipe(28, "Хумус з овочевими паличками", 250.0, 9.0, 13.0, 25.0, MealSlot.SNACK, 5)

    // ---------- Dashboard ----------

    private val weightPoints: List<Pair<Long, Double>>
        get() = (29 downTo 0).filter { it % 3 != 1 }.map { daysAgo ->
            val x = (29 - daysAgo).toDouble()
            val kg = 80.6 - x * 0.075 + sin(x * 1.3) * 0.25
            (today - daysAgo) to ((kg * 10).roundToInt() / 10.0)
        }

    val todayMenu: List<TodayMenuItem>
        get() = listOf(
            TodayMenuItem(oatmeal.id, MealSlot.BREAKFAST, oatmeal.name, 420, 1.0),
            TodayMenuItem(buckwheat.id, MealSlot.LUNCH, buckwheat.name, 670, 1.2),
            TodayMenuItem(salmon.id, MealSlot.DINNER, salmon.name, 590, 1.0),
            TodayMenuItem(yogurt.id, MealSlot.SNACK, yogurt.name, 210, 1.0),
        )

    val workouts: List<WorkoutItem>
        get() = listOf(
            WorkoutItem(1, isFromWatch = true, type = null, hcExerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                title = null, minutes = 32, reps = null, kcal = 286),
            WorkoutItem(2, isFromWatch = false, type = WorkoutType.PUSH_UPS, hcExerciseType = null,
                title = null, minutes = null, reps = 40, kcal = 14),
            WorkoutItem(3, isFromWatch = false, type = WorkoutType.YOGA, hcExerciseType = null,
                title = null, minutes = 20, reps = null, kcal = 74),
        )

    fun dashboard(): DashboardUiState {
        val points = weightPoints
        return DashboardUiState(
            loading = false,
            targets = DailyTargets(kcal = 2240, proteinG = 140, fatG = 72, carbsG = 252),
            adjustedByActivity = true,
            eatenKcal = 1385,
            remainingKcal = 855,
            eatenProteinG = 96,
            eatenFatG = 47,
            eatenCarbsG = 148,
            eatenFiberG = 17.5,
            eatenSugarsG = 38.0,
            eatenSaltG = 4.1,
            eatenSatFatG = 13.2,
            fiberTargetG = 31,
            sugarLimitG = 56,
            saltLimitG = 5,
            satFatLimitG = 24,
            caloriesOut = 2510,
            activityBonusKcal = 290,
            steps = 8432,
            activeKcal = 214,
            workouts = workouts,
            exerciseMinutes = 52,
            sleepMinutes = 437,
            avgHeartRateBpm = 68,
            weightKg = points.last().second,
            waterMl = 1250,
            weightPoints = points,
            weightTrend = WeightTrendCalculator.calculate(points),
            streakDays = 12,
            todayMenu = todayMenu,
            hcBanner = HcBannerState.NONE,
            lastSyncEpochMillis = System.currentTimeMillis() - 7 * 60_000,
        )
    }

    /** First day after onboarding: nothing eaten, no plan, no watch, one weight. */
    fun dashboardNewUser() = DashboardUiState(
        loading = false,
        targets = DailyTargets(kcal = 1950, proteinG = 118, fatG = 65, carbsG = 222),
        remainingKcal = 1950,
        fiberTargetG = 27,
        sugarLimitG = 49,
        saltLimitG = 5,
        satFatLimitG = 21,
        weightKg = 71.0,
        weightPoints = listOf(today to 71.0),
        hcBanner = HcBannerState.NOT_INSTALLED,
    )

    /** A heavy day: over the target, Health Connect permissions missing. */
    fun dashboardOver() = dashboard().copy(
        eatenKcal = 2655,
        remainingKcal = -415,
        eatenProteinG = 118,
        eatenFatG = 104,
        eatenCarbsG = 301,
        eatenSugarsG = 82.0,
        eatenSaltG = 7.4,
        eatenSatFatG = 31.0,
        adjustedByActivity = false,
        activityBonusKcal = 0,
        steps = null,
        activeKcal = null,
        exerciseMinutes = null,
        sleepMinutes = null,
        avgHeartRateBpm = null,
        workouts = emptyList(),
        streakDays = 0,
        todayMenu = emptyList(),
        hcBanner = HcBannerState.NO_PERMISSION,
    )

    // ---------- Statistics ----------

    fun stats(range: StatsRange): StatsUiState {
        val target = 2240.0
        val bars = (range.days - 1 downTo 0).map { daysAgo ->
            val day = today - daysAgo
            val eaten = if (daysAgo % 9 == 4) 0.0 else target * (0.82 + 0.3 * ((daysAgo * 37 % 11) / 10.0))
            val state = when {
                eaten == 0.0 -> DayState.EMPTY
                eaten > target * 1.1 -> DayState.OVER
                else -> DayState.GOOD
            }
            DayBar(day, eaten, state)
        }
        val month = YearMonth.now()
        val calendar = (1..LocalDate.now().dayOfMonth).associate { d ->
            val day = month.atDay(d).toEpochDay()
            day to when ((d * 7) % 10) {
                0, 3 -> DayState.OVER
                6 -> DayState.EMPTY
                else -> DayState.GOOD
            }
        }.filterValues { it != DayState.EMPTY }
        val logged = bars.count { it.state != DayState.EMPTY }
        return StatsUiState(
            loading = false,
            range = range,
            bars = bars,
            avgTargetKcal = 2240,
            avgEatenKcal = 2105,
            avgProteinG = 112,
            avgFatG = 74,
            avgCarbsG = 236,
            avgFiberG = 19,
            targets = DailyTargets(2240, 140, 72, 252),
            loggedDays = logged,
            goodDays = bars.count { it.state == DayState.GOOD },
            avgWaterMl = 1650,
            topSources = TopSources(
                protein = listOf(
                    FoodContribution(t("Chicken breast", "Куряче філе"), 412.0),
                    FoodContribution(t("Cottage cheese 5%", "Сир кисломолочний 5%"), 236.0),
                    FoodContribution(t("Eggs", "Яйця"), 158.0),
                    FoodContribution(t("Greek yogurt 2%", "Грецький йогурт 2%"), 121.0),
                    FoodContribution(t("Buckwheat", "Гречка"), 88.0),
                ),
                fat = listOf(
                    FoodContribution(t("Walnuts", "Волоські горіхи"), 96.0),
                    FoodContribution(t("Olive oil", "Оливкова олія"), 84.0),
                    FoodContribution(t("Salmon fillet", "Філе лосося"), 61.0),
                ),
                carbs = listOf(
                    FoodContribution(t("Rye bread", "Житній хліб"), 388.0),
                    FoodContribution(t("Buckwheat", "Гречка"), 352.0),
                    FoodContribution(t("Bananas", "Банани"), 190.0),
                ),
                sugars = emptyList(),
            ),
            insights = listOf(
                DietInsights.Insight(DietInsights.Kind.FIBER_LOW, 19.0, 31),
                DietInsights.Insight(DietInsights.Kind.SALT_HIGH, 6.2, 5),
            ),
            calendarMonth = month,
            calendarStates = calendar,
        )
    }

    fun statsEmpty() = StatsUiState(
        loading = false,
        bars = (6 downTo 0).map { DayBar(today - it, 0.0, DayState.EMPTY) },
        avgTargetKcal = 1950,
        targets = DailyTargets(1950, 118, 65, 222),
    )

    // ---------- Diary ----------

    private fun entry(
        id: Long, slot: MealSlot, name: String, grams: Double?, kcal: Double,
        p: Double, f: Double, c: Double, hour: Int, servings: Double? = null,
    ) = FoodLogEntryEntity(
        id = id, epochDay = today, meal = slot, productId = "p$id", name = name,
        grams = grams, servings = servings, kcal = kcal, proteinG = p, fatG = f, carbsG = c,
        loggedAtEpochMillis = millisAt(today, hour),
    )

    fun diary(): DiaryUiState {
        val entries = listOf(
            entry(1, MealSlot.BREAKFAST, oatmeal.name, null, 420.0, 13.0, 14.0, 62.0, 8),
            entry(2, MealSlot.BREAKFAST, t("Cappuccino", "Капучино"), 250.0, 110.0, 6.0, 5.5, 9.0, 8),
            entry(3, MealSlot.LUNCH, t("Chicken breast, grilled", "Куряче філе гриль"), 180.0, 297.0, 56.0, 6.5, 0.0, 13),
            entry(4, MealSlot.LUNCH, t("Buckwheat, boiled", "Гречка варена"), 200.0, 220.0, 8.4, 2.2, 42.6, 13),
            entry(5, MealSlot.LUNCH, t("Tomato & cucumber salad", "Салат з помідорів і огірків"), 150.0, 72.0, 1.5, 4.8, 5.4, 13),
            entry(6, MealSlot.SNACK, t("Greek yogurt 2%", "Грецький йогурт 2%"), 170.0, 116.0, 17.0, 3.4, 6.1, 16, servings = 1.0),
            entry(7, MealSlot.SNACK, t("Banana", "Банан"), 120.0, 107.0, 1.3, 0.4, 27.4, 17),
        )
        return DiaryUiState(
            epochDay = today,
            isToday = true,
            entriesBySlot = entries.groupBy { it.meal },
            totals = DayNutritionTotals(
                kcal = entries.sumOf { it.kcal },
                proteinG = entries.sumOf { it.proteinG },
                fatG = entries.sumOf { it.fatG },
                carbsG = entries.sumOf { it.carbsG },
            ),
            note = t("Long run in the morning, ate more than planned at lunch.",
                "Зранку довга пробіжка, в обід зʼїв більше, ніж планував."),
        )
    }

    fun diaryEmptyYesterday() = DiaryUiState(epochDay = today - 1, isToday = false)

    // ---------- Food search ----------

    private fun product(
        id: String, name: String, brand: String?, kcal: Double, p: Double, f: Double, c: Double,
        serving: Double? = null, fav: Boolean = false, liquid: Boolean = false,
        source: ProductSource = ProductSource.OPEN_FOOD_FACTS,
        fiber: Double? = null, sugars: Double? = null, salt: Double? = null, satFat: Double? = null,
    ) = FoodProductEntity(
        id = id, name = name, brand = brand, kcalPer100g = kcal, proteinPer100g = p, fatPer100g = f,
        carbsPer100g = c, servingSizeG = serving, isLiquid = liquid, fiberPer100g = fiber,
        sugarsPer100g = sugars, saltPer100g = salt, saturatedFatPer100g = satFat, source = source,
        cachedAtEpochMillis = 0L, isFavorite = fav,
    )

    val yogurtProduct get() = product("4820000000011", t("Greek yogurt 2%", "Грецький йогурт 2%"), t("Dairy farm", "Молочна ферма"),
        68.0, 10.0, 2.0, 3.6, serving = 170.0, fav = true, fiber = 0.0, sugars = 3.6, salt = 0.1, satFat = 1.3)
    val oatsProduct get() = product("4820000000028", t("Rolled oats", "Вівсяні пластівці"), null,
        366.0, 12.0, 6.5, 61.0, fiber = 10.0, sugars = 1.1, salt = 0.0, satFat = 1.2)
    val milkProduct get() = product("4820000000035", t("Milk 2.5%", "Молоко 2,5%"), t("Dairy farm", "Молочна ферма"),
        52.0, 2.8, 2.5, 4.7, serving = 200.0, liquid = true, sugars = 4.7, salt = 0.1, satFat = 1.6)
    val breadProduct get() = product("4820000000042", t("Rye bread with seeds", "Житній хліб з насінням"), t("Bakery No. 1", "Хлібзавод №1"),
        238.0, 8.1, 4.2, 40.5, serving = 35.0, fav = true, fiber = 7.8, sugars = 3.0, salt = 1.1)
    val bananaProduct get() = product("local:banana", t("Banana", "Банан"), null, 89.0, 1.1, 0.3, 22.8,
        serving = 120.0, source = ProductSource.LOCAL)
    val kefirProduct get() = product("4820000000059", t("Kefir 1%", "Кефір 1%"), null, 40.0, 3.0, 1.0, 4.0,
        liquid = true, source = ProductSource.ZAKAZ)

    fun foodSearchFrequent() = FoodSearchUiState(
        frequent = listOf(yogurtProduct, oatsProduct, bananaProduct, breadProduct, milkProduct),
        basket = listOf(BasketItem(yogurtProduct, "170"), BasketItem(bananaProduct, "120")),
    )

    fun foodSearchResults() = FoodSearchUiState(
        query = t("yogurt", "йогурт"),
        searched = true,
        results = listOf(
            yogurtProduct,
            product("4820000000066", t("Yogurt drink, strawberry", "Йогурт питний полуничний"), t("Dairy farm", "Молочна ферма"),
                78.0, 2.9, 1.5, 12.9, liquid = true),
            product("4820000000073", t("Natural yogurt 3.2%", "Йогурт натуральний 3,2%"), null, 63.0, 4.0, 3.2, 4.5),
            product("4820000000080", t("Skyr, vanilla", "Скір ванільний"), t("Northern dairy", "Північна молочарня"),
                79.0, 10.0, 0.2, 9.0, serving = 140.0),
            kefirProduct,
        ),
    )

    fun foodSearchOffline() = foodSearchResults().copy(offline = true)

    fun foodSearchFavorites() = FoodSearchUiState(
        favoritesMode = true,
        favorites = listOf(yogurtProduct, breadProduct),
    )

    fun foodSearchSavedMeals() = FoodSearchUiState(
        savedMealsMode = true,
        savedMeals = listOf(
            SavedMealSummary(1, t("My usual breakfast", "Мій звичайний сніданок"), 3, 534.0),
            SavedMealSummary(2, t("Post-workout snack", "Перекус після тренування"), 2, 286.0),
            SavedMealSummary(3, t("Office lunch", "Обід в офісі"), 4, 712.0),
        ),
    )

    fun foodSearchNoResults() = FoodSearchUiState(query = "xyzzy", searched = true)

    fun foodSearchSearching() = FoodSearchUiState(query = t("kefir", "кефір"), searching = true)

    val barcodePrefill: ProductPrefill
        get() = ProductPrefill(
            barcode = "4823061300015",
            name = t("Buckwheat porridge with mushrooms", "Каша гречана з грибами"),
            brand = null,
            kcalPer100g = 132.0,
            proteinPer100g = null,
            fatPer100g = null,
            carbsPer100g = 21.0,
            servingSizeG = 250.0,
            isLiquid = false,
        )

    // ---------- Meal plan + recipe ----------

    private fun meal(id: Long, day: Long, r: RecipeNutrition, factor: Double, locked: Boolean = false) =
        PlanMealUi(
            id = id, epochDay = day, slot = r.slots.first(), position = 0, recipeId = r.id,
            recipeName = r.name, kcal = (r.kcal * factor).roundToInt(), portionFactor = factor, isLocked = locked,
        )

    fun mealPlan(): MealPlanUiState {
        val rotation = listOf(
            listOf(oatmeal to 1.0, buckwheat to 1.2, salmon to 1.0, yogurt to 1.0),
            listOf(syrnyky to 1.0, borscht to 1.1, tofu to 1.2, apple to 1.0),
            listOf(omelette to 1.2, plov to 1.0, turkey to 1.1, hummus to 1.0),
        )
        var id = 0L
        val days = (0 until 7).map { i ->
            val day = today + i
            val meals = rotation[i % 3].mapIndexed { index, (r, f) ->
                meal(++id, day, r, f, locked = i == 0 && index == 1)
            }
            PlanDayUi(day, meals, meals.sumOf { it.kcal })
        }
        return MealPlanUiState(loading = false, days = days, targetKcal = 2240, hasPlan = true)
    }

    fun mealPlanEmpty() = MealPlanUiState(loading = false, targetKcal = 2240, hasPlan = false)

    fun recipeDetail(factor: Double = 1.0, cooked: Boolean = false) = RecipeDetailUiState(
        loading = false,
        details = RecipeDetails(
            nutrition = buckwheat,
            steps = listOf(
                "Промийте гречку і зваріть у підсоленій воді 15–18 хвилин.",
                "Наріжте куряче філе смужками, а моркву й цибулю — кубиками.",
                "Обсмажте філе на олії 5–6 хвилин до золотистої скоринки.",
                "Додайте овочі й тушкуйте під кришкою ще 8 хвилин.",
                "Змішайте з гречкою, посоліть, поперчіть і посипте зеленню.",
            ),
            ingredients = listOf(
                IngredientAmountRow("Гречка", 80.0, null),
                IngredientAmountRow("Куряче філе", 150.0, null),
                IngredientAmountRow("Морква", 60.0, 80.0),
                IngredientAmountRow("Цибуля ріпчаста", 50.0, 90.0),
                IngredientAmountRow("Олія соняшникова", 8.0, null),
                IngredientAmountRow("Сіль", 2.0, null),
            ),
        ),
        portionFactor = factor,
        cooked = cooked,
    )

    // ---------- Fridge + shopping ----------

    private fun fridgeItem(name: String, aisle: Aisle, grams: Double) =
        FridgeItemUi(name, aisle, grams, ((grams / 5).roundToInt() * 5))

    fun fridge(): FridgeUiState {
        val items = listOf(
            fridgeItem("Куряче філе", Aisle.MEAT_FISH, 640.0),
            fridgeItem("Філе лосося", Aisle.MEAT_FISH, 310.0),
            fridgeItem("Броколі", Aisle.PRODUCE, 420.0),
            fridgeItem("Морква", Aisle.PRODUCE, 380.0),
            fridgeItem("Банани", Aisle.PRODUCE, 360.0),
            fridgeItem("Яйця", Aisle.DAIRY_EGGS, 550.0),
            fridgeItem("Сир кисломолочний 5%", Aisle.DAIRY_EGGS, 400.0),
            fridgeItem("Гречка", Aisle.GRAINS_PASTA, 900.0),
            fridgeItem("Рис", Aisle.GRAINS_PASTA, 650.0),
            fridgeItem("Олія соняшникова", Aisle.SPICES_OILS, 700.0),
        )
        return FridgeUiState(
            loading = false,
            itemsByAisle = items.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }),
            itemCount = items.size,
            ideas = listOf(
                FridgeMath.Match(buckwheat.id, buckwheat.name, 0, emptyList()),
                FridgeMath.Match(salmon.id, salmon.name, 1, listOf("Картопля")),
                FridgeMath.Match(syrnyky.id, syrnyky.name, 2, listOf("Сметана", "Борошно")),
            ),
        )
    }

    fun fridgeEmpty() = FridgeUiState(loading = false)

    fun aiNoKey() = AiUiState(hasKey = false)

    fun aiAnswer() = AiUiState(
        hasKey = true,
        result = AiResult.Ok(
            text = t(
                "With chicken, broccoli and rice you can make a quick stir-fry tonight: " +
                    "cook 70 g of rice, fry 150 g of chicken strips for 6 minutes, add broccoli " +
                    "for 4 more. About 520 kcal, 45 g protein. Tomorrow: an omelette with eggs " +
                    "and cottage cheese for breakfast.",
                "З курки, броколі та рису сьогодні вийде швидке смаження: зваріть 70 г рису, " +
                    "обсмажте 150 г курячих смужок 6 хвилин, додайте броколі ще на 4. " +
                    "Близько 520 ккал, 45 г білка. Завтра на сніданок — омлет з яйцями та сиром.",
            ),
            inputTokens = 812,
            outputTokens = 164,
            truncated = false,
        ),
    )

    val pickable: List<IngredientEntity>
        get() = listOf(
            IngredientEntity(1, "Куряче філе", Aisle.MEAT_FISH, 110.0, 23.0, 1.5, 0.0, null),
            IngredientEntity(2, "Індиче філе", Aisle.MEAT_FISH, 114.0, 24.0, 1.5, 0.0, null),
            IngredientEntity(5, "Філе лосося", Aisle.MEAT_FISH, 208.0, 20.0, 13.0, 0.0, null),
            IngredientEntity(20, "Яйця", Aisle.DAIRY_EGGS, 157.0, 12.7, 11.5, 0.7, 55.0),
            IngredientEntity(31, "Броколі", Aisle.PRODUCE, 34.0, 2.8, 0.4, 6.6, null),
            IngredientEntity(32, "Морква", Aisle.PRODUCE, 41.0, 0.9, 0.2, 9.6, 80.0),
        )

    fun shopping(): ShoppingUiState {
        var id = 0L
        fun item(name: String, aisle: Aisle, grams: Int, pieces: Int? = null, checked: Boolean = false,
                 have: Boolean = false, moved: Boolean = false) =
            ShoppingItemUi(++id, name, aisle, grams, pieces, checked, have, moved)
        val items = listOf(
            item("Броколі", Aisle.PRODUCE, 600, checked = true),
            item("Морква", Aisle.PRODUCE, 350, pieces = 5),
            item("Цибуля ріпчаста", Aisle.PRODUCE, 300, pieces = 4, have = true),
            item("Банани", Aisle.PRODUCE, 720, pieces = 6),
            item("Куряче філе", Aisle.MEAT_FISH, 900, checked = true, moved = true),
            item("Філе лосося", Aisle.MEAT_FISH, 450, checked = true),
            item("Сир кисломолочний 5%", Aisle.DAIRY_EGGS, 800),
            item("Грецький йогурт", Aisle.DAIRY_EGGS, 680),
            item("Яйця", Aisle.DAIRY_EGGS, 660, pieces = 12),
            item("Гречка", Aisle.GRAINS_PASTA, 480),
            item("Пшоно", Aisle.GRAINS_PASTA, 240),
        )
        val week = (0 until 7).map { today + it }
        return ShoppingUiState(
            loading = false,
            weekDays = week,
            selectedDays = week.take(4).toSet(),
            itemsByAisle = items.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }),
            hasPlan = true,
            hasList = true,
            movableCount = 2,
        )
    }

    fun shoppingNoPlan(): ShoppingUiState {
        val week = (0 until 7).map { today + it }
        return ShoppingUiState(loading = false, weekDays = week, hasPlan = false, hasList = false)
    }

    // ---------- Onboarding ----------

    fun onboarding(step: Int): OnboardingUiState = when (step) {
        OnboardingUiState.STEP_SEX -> OnboardingUiState(step = step, sex = Sex.FEMALE)
        else -> OnboardingUiState(
            step = step,
            sex = Sex.FEMALE,
            birthDate = LocalDate.of(1993, 4, 17),
            heightCmText = "168",
            weightKgText = "64,5",
            activityLevel = if (step >= OnboardingUiState.STEP_ACTIVITY) ActivityLevel.MODERATE else null,
            goal = if (step >= OnboardingUiState.STEP_GOAL) Goal.LOSE else null,
            targetKgPerWeek = if (step >= OnboardingUiState.STEP_GOAL) 0.25 else 0.0,
            snacksPerDay = 1,
            noPork = step >= OnboardingUiState.STEP_DIET,
            allergies = if (step >= OnboardingUiState.STEP_DIET) setOf(Allergen.PEANUTS) else emptySet(),
            preview = if (step == OnboardingUiState.STEP_SUMMARY) {
                OnboardingPreview(bmr = 1391, targets = DailyTargets(1880, 113, 63, 214), raisedToFloor = false)
            } else {
                null
            },
        )
    }

    // ---------- Settings ----------

    fun settings(): SettingsUiState = SettingsUiState(
        loading = false,
        sex = Sex.MALE,
        birthDate = LocalDate.of(1990, 9, 2),
        heightCmText = "181",
        weightKgText = "78,4",
        activityLevel = ActivityLevel.MODERATE,
        goal = Goal.LOSE,
        targetKgPerWeek = 0.5,
        snacksPerDay = 1,
        cookingSessionsPerWeek = 3,
        noPork = false,
        allergies = setOf(Allergen.SHELLFISH),
        recurring = listOf(
            RecurringWorkoutEntity(1, WorkoutType.STRENGTH_TRAINING, 45, null,
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            RecurringWorkoutEntity(2, WorkoutType.PUSH_UPS, null, 50, listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
        ),
        reminders = listOf(
            ReminderUi(MealSlot.BREAKFAST, true, 8 * 60 + 30),
            ReminderUi(MealSlot.LUNCH, true, 13 * 60),
            ReminderUi(MealSlot.DINNER, false, 19 * 60),
            ReminderUi(MealSlot.SNACK, false, 16 * 60 + 30),
        ),
        createdAtEpochMillis = millisAt(today - 64, 10),
        initialWeightKg = 82.1,
        hcAvailable = true,
        lastSyncEpochMillis = System.currentTimeMillis() - 12 * 60_000,
        aiKeyStored = true,
        botTokenStored = true,
        botUsername = "nutricart_family_bot",
        partnerName = t("Olena", "Олена"),
        cloudConfigured = true,
        cloudEnabled = true,
        cloudNameText = t("Andrii", "Андрій"),
        cloudNameStored = t("Andrii", "Андрій"),
        cloudPairingCode = "K7Q-4MZ" to System.currentTimeMillis() + 9 * 60_000,
        cloudPartners = listOf(CloudPartner("l1", t("Olena", "Олена"), millisAt(today - 20, 19))),
        cloudLastSyncEpochMillis = System.currentTimeMillis() - 3 * 60_000,
        cloudPendingCount = 2,
        cloudAccount = CloudAccount(email = "andrii@example.com", pendingEmail = null),
    )
}
