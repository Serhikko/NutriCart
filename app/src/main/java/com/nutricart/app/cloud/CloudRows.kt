package com.nutricart.app.cloud

import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.widget.DayNumbers
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
 * same account can never collide with this one.
 */
object CloudRows {

    const val TABLE_FOOD = "food_log_entries"
    const val TABLE_WATER = "water_entries"
    const val TABLE_WEIGHT = "weight_entries"
    const val TABLE_DAYS = "day_summaries"
    const val TABLE_PROFILES = "profiles"
    const val TABLE_PAIRING_CODES = "pairing_codes"

    fun foodId(deviceId: String, localId: Long) = "$deviceId:f:$localId"
    fun waterId(deviceId: String, localId: Long) = "$deviceId:w:$localId"

    /** A diary line, or its tombstone when [deletedAtEpochMillis] is set. */
    fun foodEntry(deviceId: String, e: FoodLogEntryEntity, deletedAtEpochMillis: Long? = null): JsonObject =
        buildJsonObject {
            put("id", foodId(deviceId, e.id))
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
            put("id", waterId(deviceId, e.id))
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

    fun pairingCode(codeHash: String, expiresAtEpochMillis: Long): JsonObject = buildJsonObject {
        put("code_hash", codeHash)
        put("expires_at", iso(expiresAtEpochMillis))
    }

    /** The owner column each table uses; only profiles differs. */
    fun ownerColumn(table: String): String = if (table == TABLE_PROFILES) "user_id" else "owner_id"

    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
