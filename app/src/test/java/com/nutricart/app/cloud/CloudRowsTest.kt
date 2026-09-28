package com.nutricart.app.cloud

import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.domain.model.WeightSource
import com.nutricart.app.widget.DayNumbers
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's rows must match the columns in supabase/migrations/0001_init.sql. */
class CloudRowsTest {

    private val entry = FoodLogEntryEntity(
        id = 42, epochDay = 20724, meal = MealSlot.LUNCH, productId = "off:123",
        name = "Chicken with rice", grams = 350.0, servings = null,
        kcal = 450.4, proteinG = 38.0, fatG = 9.5, carbsG = 52.0,
        fiberG = 2.1, sugarsG = null, saltG = 1.2, saturatedFatG = null,
        loggedAtEpochMillis = 1_790_000_000_000L,
    )

    @Test
    fun `food row carries every schema column and a device-scoped id`() {
        val row = CloudRows.foodEntry("ab12cd34", entry)
        assertEquals("ab12cd34:f:42", row["id"]!!.jsonPrimitive.content)
        assertEquals("LUNCH", row["meal"]!!.jsonPrimitive.content)
        assertEquals("20724", row["epoch_day"]!!.jsonPrimitive.content)
        assertEquals("450.4", row["kcal"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, row["servings"])
        assertEquals(JsonNull, row["sugars_g"])
        assertEquals(JsonNull, row["deleted_at"])
        assertTrue(row["logged_at"]!!.jsonPrimitive.content.startsWith("2026-"))
        assertEquals(
            setOf(
                "id", "epoch_day", "meal", "name", "grams", "servings", "kcal", "protein_g", "fat_g",
                "carbs_g", "fiber_g", "sugars_g", "salt_g", "saturated_fat_g", "logged_at", "deleted_at",
            ),
            row.keys,
        )
    }

    @Test
    fun `a row the website wrote keeps the website's id, so a phone delete hits the same server row`() {
        val pulled = entry.copy(cloudId = "web:f:0b5c3f8e")
        assertEquals("web:f:0b5c3f8e", CloudRows.foodId("ab12cd34", pulled))
        assertEquals("web:f:0b5c3f8e", CloudRows.foodEntry("ab12cd34", pulled)["id"]!!.jsonPrimitive.content)
        assertEquals("ab12cd34:f:42", CloudRows.foodId("ab12cd34", entry))
    }

    @Test
    fun `own ids are recognised and parsed, foreign ones are not`() {
        assertTrue(CloudRows.isOwnId("ab12cd34", "ab12cd34:f:42"))
        assertEquals(42L, CloudRows.localIdOf("ab12cd34", "ab12cd34:f:42"))
        assertEquals(null, CloudRows.localIdOf("ab12cd34", "web:f:0b5c3f8e"))
        assertEquals(null, CloudRows.localIdOf("ab12cd34", "ab12cd34x:f:42"))
    }

    @Test
    fun `profile details carry the questionnaire in the web schema's names`() {
        val profile = UserProfileEntity(
            sex = Sex.MALE, birthDateEpochDay = java.time.LocalDate.of(1996, 5, 17).toEpochDay(), heightCm = 182,
            activityLevel = ActivityLevel.MODERATE, goal = Goal.LOSE, targetKgPerWeek = 0.5,
            snacksPerDay = 1, cookingSessionsPerWeek = 4, isVegetarian = false, noPork = false,
            allergies = emptyList(), createdAtEpochMillis = 0L, customKcalTarget = 2100,
        )
        val row = CloudRows.profileDetails(profile)
        assertEquals("MALE", row["sex"]!!.jsonPrimitive.content)
        assertEquals("1996-05-17", row["birth_date"]!!.jsonPrimitive.content)
        assertEquals("182", row["height_cm"]!!.jsonPrimitive.content)
        assertEquals("MODERATE", row["activity_level"]!!.jsonPrimitive.content)
        assertEquals("LOSE", row["goal"]!!.jsonPrimitive.content)
        assertEquals("2100", row["custom_kcal_target"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, row["custom_protein_g"])
        assertEquals("phone", row["primary_client"]!!.jsonPrimitive.content)
        assertEquals("user_id", CloudRows.ownerColumn(CloudRows.TABLE_PROFILE_DETAILS))
    }

    @Test
    fun `a deletion is the same row with deleted_at set`() {
        val row = CloudRows.foodEntry("ab12cd34", entry, deletedAtEpochMillis = 1_790_000_100_000L)
        assertEquals("ab12cd34:f:42", row["id"]!!.jsonPrimitive.content)
        assertEquals("2026-", row["deleted_at"]!!.jsonPrimitive.content.substring(0, 5))
    }

    @Test
    fun `water, weight and day rows`() {
        val water = CloudRows.waterEntry("dev", WaterEntryEntity(id = 7, epochDay = 20724, ml = 250, loggedAtEpochMillis = 1_790_000_000_000L))
        assertEquals("dev:w:7", water["id"]!!.jsonPrimitive.content)
        assertEquals("250", water["ml"]!!.jsonPrimitive.content)

        val weight = CloudRows.weightEntry(WeightEntryEntity(epochDay = 20724, weightKg = 81.3, source = WeightSource.MANUAL))
        assertEquals("MANUAL", weight["source"]!!.jsonPrimitive.content)
        assertEquals(setOf("epoch_day", "source", "weight_kg"), weight.keys)

        val day = CloudRows.daySummary(
            DayNumbers(epochDay = 20724, targetKcal = 2100, eatenKcal = 1230, activeKcal = null, steps = 8421, manualWorkoutKcal = 0.0)
        )
        assertEquals("2100", day["target_kcal"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, day["active_kcal"])
        assertEquals("8421", day["steps"]!!.jsonPrimitive.content)
    }

    @Test
    fun `owner column is user_id only for profiles`() {
        assertEquals("user_id", CloudRows.ownerColumn(CloudRows.TABLE_PROFILES))
        assertEquals("owner_id", CloudRows.ownerColumn(CloudRows.TABLE_FOOD))
    }

    @Test
    fun `timestamps are ISO-8601 in UTC`() {
        assertEquals("2026-09-28T00:00:00Z", CloudRows.iso(1_790_553_600_000L))
    }
}
