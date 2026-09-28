package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutricart.app.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert
    suspend fun insert(row: SyncOutboxEntity): Long

    @Insert
    suspend fun insertAll(rows: List<SyncOutboxEntity>)

    /** Oldest first, so the server sees writes in the order they happened. */
    @Query("SELECT * FROM sync_outbox ORDER BY id ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    /** Sync switched off or the app reset: pending writes are dropped. */
    @Query("DELETE FROM sync_outbox")
    suspend fun clear()
}
