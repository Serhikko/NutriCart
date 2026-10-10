package com.nutricart.app.cloud

import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.widget.DayNumbers
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Room entity -> PostgREST row, one function per table. The column names are
 * the schema in supabase/migrations/0001_init.sql; this file and that file
 * change together. owner_id is NOT here: the worker adds it at send time, so
 * a row queued before sign-in (or after a re-login) is still correct.
 *
 * Row ids are minted here from the install's device id and the Room id, so a
 * retried upload upserts instead of duplicating, and a second phone on the
 * same account can never collide with this one. A row another client wrote
 * (pulled in milestone 3) keeps that client's id in the entity's cloudId,
 * and that id wins, so the phone's later delete hits the same server row.
 */
object CloudRows {

    const val TABLE_FOOD = "food_log_entries"
    const val TABLE_WATER = "water_entries"
    const val TABLE_WEIGHT = "weight_entries"
    const val TABLE_DAYS = "day_summaries"
    const val TABLE_PROFILES = "profiles"
    const val TABLE_PROFILE_DETAILS = "profile_details"
    const val TABLE_PAIRING_CODES = "pairing_codes"

    fun foodId(deviceId: String, e: FoodLogEntryEntity) = e.cloudId ?: "$deviceId:f:${e.id}"
    fun waterId(deviceId: String, e: WaterEntryEntity) = e.cloudId ?: "$deviceId:w:${e.id}"

    /** True for an id this install minted (as opposed to the website's "web:..." ids). */
    fun isOwnId(deviceId: String, cloudId: String) = cloudId.startsWith("$deviceId:")

    /** The Room id inside one of this install's ids, or null for any other id. */
    fun localIdOf(deviceId: String, cloudId: String): Long? =
        if (isOwnId(deviceId, cloudId)) cloudId.substringAfterLast(':').toLongOrNull() else null

    /** A diary line, or its tombstone when [deletedAtEpochMillis] is set. */
    fun foodEntry(deviceId: String, e: FoodLogEntryEntity, deletedAtEpochMillis: Long? = null): JsonObject =
        buildJsonObject {
            put("id", foodId(deviceId, e))
            put("epoch_day", e.epochDay)
            put("meal", e.meal.name)
            put("name", e.name)
            put("grams", e.grams)
            put("servings", e.servings)
            put("kcal", e.kcal)
            put("protein_g", e.proteinG)
            put("fat_g", e.fatG)
            put("carbs_g", e.carbsG)
            put("fiber_g", e.fiberG)
            put("sugars_g", e.sugarsG)
            put("salt_g", e.saltG)
            put("saturated_fat_g", e.saturatedFatG)
            put("logged_at", iso(e.loggedAtEpochMillis))
            put("deleted_at", deletedAtEpochMillis?.let { iso(it) })
        }

    fun waterEntry(deviceId: String, e: WaterEntryEntity, deletedAtEpochMillis: Long? = null): JsonObject =
        buildJsonObject {
            put("id", waterId(deviceId, e))
            put("epoch_day", e.epochDay)
            put("ml", e.ml)
            put("logged_at", iso(e.loggedAtEpochMillis))
            put("deleted_at", deletedAtEpochMillis?.let { iso(it) })
        }

    /** Keyed on the server by (owner, day, source), like the Room unique index. */
    fun weightEntry(e: WeightEntryEntity): JsonObject = buildJsonObject {
        put("epoch_day", e.epochDay)
        put("source", e.source.name)
        put("weight_kg", e.weightKg)
    }

    fun daySummary(n: DayNumbers): JsonObject = buildJsonObject {
        put("epoch_day", n.epochDay)
        put("target_kcal", n.targetKcal)
        put("eaten_kcal", n.eatenKcal)
        put("active_kcal", n.activeKcal)
        put("steps", n.steps)
        put("workout_kcal", n.manualWorkoutKcal)
    }

    /** profiles is keyed by user_id, which the worker fills like owner_id elsewhere. */
    fun profile(displayName: String): JsonObject = buildJsonObject {
        put("display_name", displayName)
    }

    /**
     * The questionnaire, so the website computes the same target for this
     * account with the same math (web/src/domain/calories.ts) and knows the
     * phone publishes day_summaries. Keyed by user_id like profiles.
     */
    fun profileDetails(p: UserProfileEntity): JsonObject = buildJsonObject {
        put("sex", p.sex.name)
        put("birth_date", LocalDate.ofEpochDay(p.birthDateEpochDay).toString())
        put("height_cm", p.heightCm)
        put("activity_level", p.activityLevel.name)
        put("goal", p.goal.name)
        put("target_kg_per_week", p.targetKgPerWeek)
        put("custom_kcal_target", p.customKcalTarget)
        put("custom_protein_g", p.customProteinG)
        put("custom_fat_g", p.customFatG)
        put("custom_carbs_g", p.customCarbsG)
        put("primary_client", "phone")
    }

    fun pairingCode(codeHash: String, expiresAtEpochMillis: Long): JsonObject = buildJsonObject {
        put("code_hash", codeHash)
        put("expires_at", iso(expiresAtEpochMillis))
    }

    /** The owner column each table uses; the two profile tables differ. */
    fun ownerColumn(table: String): String =
        if (table == TABLE_PROFILES || table == TABLE_PROFILE_DETAILS) "user_id" else "owner_id"

    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
