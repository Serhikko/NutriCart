package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One pending cloud write. Every synced Room write also appends a row here in
 * the same transaction; CloudSyncWorker drains the table oldest-first when
 * the phone is online, so nothing the user does ever waits for the network.
 *
 * [payloadJson] is the complete PostgREST row (minus owner_id, added at send
 * time). A delete is an upsert with `deleted_at` set, so there is one shape.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index("createdAtEpochMillis")],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** PostgREST table name, e.g. "food_log_entries". */
    val tableName: String,
    /** The row's primary key on the server, for de-duplication in the log. */
    val remoteId: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
)
