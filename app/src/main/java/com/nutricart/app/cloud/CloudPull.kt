package com.nutricart.app.cloud

import android.util.Log
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.SyncOutboxDao
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.settings.SettingsDataStore
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.WeightSource
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The other direction of the sync (milestone 3): what the website wrote comes
 * down to Room. Each table keeps a watermark, the newest `updated_at` the
 * phone has seen; a pull asks for everything past it (less a minute of
 * overlap, see [PullRules.since]) except this install's own live rows, oldest
 * first, and applies [PullRules] row by row through the DAOs directly. The
 * repositories are bypassed on purpose: a pulled row must not be mirrored
 * back up, and the partner digest is for meals the user logs, not for what
 * the sync moves.
 *
 * Returns whether anything changed locally, so the worker knows a fresh day
 * summary is due.
 */
@Singleton
class CloudPull @Inject constructor(
    private val rest: SupabaseRestApi,
    private val settings: SettingsDataStore,
    private val outbox: SyncOutboxDao,
    private val foodLogDao: FoodLogDao,
    private val waterDao: WaterDao,
    private val weightDao: WeightDao,
) {

    suspend fun run(userId: String, deviceId: String, bearer: suspend () -> String): Boolean {
        val pending = outbox.oldest(Int.MAX_VALUE).map { it.remoteId }.toSet()
        var changed = false
        changed = pullFood(userId, deviceId, pending, bearer) || changed
        changed = pullWater(userId, deviceId, pending, bearer) || changed
        changed = pullWeight(userId, pending, bearer) || changed
        return changed
    }

    private suspend fun pullFood(userId: String, deviceId: String, pending: Set<String>, bearer: suspend () -> String): Boolean {
        var changed = false
        val watermark = settings.cloudPullWatermark(CloudRows.TABLE_FOOD).first()
        var offset = 0
        while (true) {
            val rows = rest.foodRows(bearer(), filters(userId, watermark, offset, deviceId))
            for (row in rows) {
                when (val action = PullRules.forRow(deviceId, row.id, row.deletedAt != null, pending)) {
                    PullAction.Skip -> Unit
                    is PullAction.DeleteLocal -> {
                        if (foodLogDao.byId(action.localId) != null) {
                            foodLogDao.deleteById(action.localId); changed = true
                        }
                    }
                    is PullAction.DeleteForeign -> {
                        if (foodLogDao.byCloudId(action.cloudId) != null) {
                            foodLogDao.deleteByCloudId(action.cloudId); changed = true
                        }
                    }
                    is PullAction.UpsertForeign -> {
                        val existing = foodLogDao.byCloudId(action.cloudId)
                        val fresh = row.toEntity(existing?.id ?: 0)
                        // A re-pulled row (the watermark overlap) maps to an equal entity: no write, no change.
                        if (existing == null) {
                            foodLogDao.insert(fresh); changed = true
                        } else if (existing != fresh) {
                            foodLogDao.update(fresh); changed = true
                        }
                    }
                }
            }
            val newest = rows.lastOrNull()?.updatedAt
            if (newest != null) settings.setCloudPullWatermark(CloudRows.TABLE_FOOD, PullRules.newest(watermark, newest))
            if (rows.size < PAGE) break
            offset += PAGE
        }
        return changed
    }

    private suspend fun pullWater(userId: String, deviceId: String, pending: Set<String>, bearer: suspend () -> String): Boolean {
        var changed = false
        val watermark = settings.cloudPullWatermark(CloudRows.TABLE_WATER).first()
        var offset = 0
        while (true) {
            val rows = rest.waterRows(bearer(), filters(userId, watermark, offset, deviceId))
            for (row in rows) {
                when (val action = PullRules.forRow(deviceId, row.id, row.deletedAt != null, pending)) {
                    PullAction.Skip -> Unit
                    is PullAction.DeleteLocal -> { if (waterDao.deleteById(action.localId) > 0) changed = true }
                    is PullAction.DeleteForeign -> {
                        if (waterDao.byCloudId(action.cloudId) != null) {
                            waterDao.deleteByCloudId(action.cloudId); changed = true
                        }
                    }
                    is PullAction.UpsertForeign -> {
                        if (waterDao.byCloudId(action.cloudId) == null) {
                            waterDao.insert(
                                WaterEntryEntity(
                                    epochDay = row.epochDay, ml = row.ml,
                                    loggedAtEpochMillis = epochMillis(row.loggedAt), cloudId = row.id,
                                )
                            )
                            changed = true
                        }
                    }
                }
            }
            val newest = rows.lastOrNull()?.updatedAt
            if (newest != null) settings.setCloudPullWatermark(CloudRows.TABLE_WATER, PullRules.newest(watermark, newest))
            if (rows.size < PAGE) break
            offset += PAGE
        }
        return changed
    }

    private suspend fun pullWeight(userId: String, pending: Set<String>, bearer: suspend () -> String): Boolean {
        var changed = false
        val watermark = settings.cloudPullWatermark(CloudRows.TABLE_WEIGHT).first()
        val rows = rest.weightRows(bearer(), filters(userId, watermark, 0))
        for (row in rows) {
            val source = runCatching { WeightSource.valueOf(row.source) }.getOrNull() ?: continue
            val local = weightDao.forDay(row.epochDay, source)
            if (PullRules.applyWeight("${row.epochDay}:${source.name}", pending, local?.weightKg, row.weightKg)) {
                weightDao.insert(WeightEntryEntity(epochDay = row.epochDay, weightKg = row.weightKg, source = source))
                changed = true
            }
        }
        rows.lastOrNull()?.updatedAt?.let { settings.setCloudPullWatermark(CloudRows.TABLE_WEIGHT, PullRules.newest(watermark, it)) }
        return changed
    }

    /**
     * Rows past the watermark, less the overlap (see [PullRules.since]). With [deviceId] (food and
     * water, whose ids name the install that minted them), only the rows the pull can act on (see
     * [PullRules.actionable]). Weight rows carry no such id, and there are few of them.
     */
    private fun filters(userId: String, watermark: String?, offset: Int, deviceId: String? = null) = buildMap {
        put("owner_id", "eq.$userId")
        put("updated_at", "gt.${PullRules.since(watermark)}")
        if (deviceId != null) put("or", PullRules.actionable(deviceId))
        put("order", "updated_at.asc")
        put("limit", PAGE.toString())
        put("offset", offset.toString())
    }

    private fun SbFoodRowDto.toEntity(localId: Long) = FoodLogEntryEntity(
        id = localId,
        epochDay = epochDay,
        meal = runCatching { MealSlot.valueOf(meal) }.getOrDefault(MealSlot.SNACK),
        productId = null, // a snapshot from another client; no cached product on this phone
        name = name,
        grams = grams,
        servings = servings,
        kcal = kcal,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
        fiberG = fiberG,
        sugarsG = sugarsG,
        saltG = saltG,
        saturatedFatG = saturatedFatG,
        loggedAtEpochMillis = epochMillis(loggedAt),
        cloudId = id,
    )

    private fun epochMillis(iso: String): Long = try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse {
            Log.w(TAG, "Unreadable timestamp from the cloud")
            System.currentTimeMillis()
        }
    }

    private companion object {
        const val TAG = "CloudPull"
        const val PAGE = 500
    }
}
