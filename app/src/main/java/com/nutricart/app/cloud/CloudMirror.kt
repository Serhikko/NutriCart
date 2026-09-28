package com.nutricart.app.cloud

import com.nutricart.app.data.local.dao.SyncOutboxDao
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.SyncOutboxEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one door from the repositories to the cloud: "this was written, mirror
 * it". Each call appends the row to the outbox and asks for a drain. When sync
 * is off nothing is queued — switching it on later backfills instead.
 *
 * Repositories call this after their Room write; they never wait for the
 * network and never learn whether the upload happened.
 */
@Singleton
class CloudMirror @Inject constructor(
    private val outbox: SyncOutboxDao,
    private val settings: SettingsDataStore,
    private val scheduling: CloudSyncScheduling,
) {

    suspend fun foodLogged(entry: FoodLogEntryEntity) =
        queue(CloudRows.TABLE_FOOD, CloudRows.foodId(deviceId(), entry)) { CloudRows.foodEntry(it, entry) }

    suspend fun foodLogged(entries: List<FoodLogEntryEntity>) {
        if (!enabled()) return
        val device = deviceId()
        outbox.insertAll(entries.map { e -> row(CloudRows.TABLE_FOOD, CloudRows.foodId(device, e), CloudRows.foodEntry(device, e)) })
        scheduling.requestSync()
    }

    suspend fun foodDeleted(entry: FoodLogEntryEntity) =
        queue(CloudRows.TABLE_FOOD, CloudRows.foodId(deviceId(), entry)) {
            CloudRows.foodEntry(it, entry, deletedAtEpochMillis = System.currentTimeMillis())
        }

    suspend fun waterLogged(entry: WaterEntryEntity) =
        queue(CloudRows.TABLE_WATER, CloudRows.waterId(deviceId(), entry)) { CloudRows.waterEntry(it, entry) }

    suspend fun waterDeleted(entry: WaterEntryEntity) =
        queue(CloudRows.TABLE_WATER, CloudRows.waterId(deviceId(), entry)) {
            CloudRows.waterEntry(it, entry, deletedAtEpochMillis = System.currentTimeMillis())
        }

    suspend fun weightLogged(entry: WeightEntryEntity) =
        queue(CloudRows.TABLE_WEIGHT, "${entry.epochDay}:${entry.source.name}") { CloudRows.weightEntry(entry) }

    /** Used by the backfill: queue without the enabled check (it is being enabled). */
    suspend fun queueRows(rows: List<SyncOutboxEntity>) {
        if (rows.isEmpty()) return
        outbox.insertAll(rows)
    }

    /** The install's random id, minted on first use. */
    suspend fun deviceId(): String {
        settings.cloudDeviceId.first()?.let { return it }
        val fresh = UUID.randomUUID().toString().substring(0, 8)
        settings.setCloudDeviceId(fresh)
        return fresh
    }

    fun row(table: String, remoteId: String, payload: JsonObject) = SyncOutboxEntity(
        tableName = table,
        remoteId = remoteId,
        payloadJson = json.encodeToString(JsonObject.serializer(), payload),
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    private suspend fun enabled() = settings.cloudSyncEnabled.first()

    private suspend fun queue(table: String, remoteId: String, build: (deviceId: String) -> JsonObject) {
        if (!enabled()) return
        outbox.insert(row(table, remoteId, build(deviceId())))
        scheduling.requestSync()
    }

    private companion object {
        val json = Json
    }
}
