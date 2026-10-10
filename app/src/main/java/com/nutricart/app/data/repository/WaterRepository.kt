package com.nutricart.app.data.repository

import com.nutricart.app.cloud.CloudMirror
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Water intake: a running daily total plus quick-add and undo. */
@Singleton
class WaterRepository @Inject constructor(
    private val waterDao: WaterDao,
    private val cloudMirror: CloudMirror,
) {

    fun observeDayTotal(epochDay: Long): Flow<Int> = waterDao.observeDayTotal(epochDay)

    suspend fun add(epochDay: Long, ml: Int) {
        val entry = WaterEntryEntity(
            epochDay = epochDay,
            ml = ml,
            loggedAtEpochMillis = System.currentTimeMillis(),
        )
        val id = waterDao.insert(entry)
        cloudMirror.waterLogged(entry.copy(id = id))
    }

    /** Undo removes the most recent entry of the day (a mistaken tap). */
    suspend fun undoLast(epochDay: Long) {
        val last = waterDao.last(epochDay) ?: return
        waterDao.delete(last)
        cloudMirror.waterDeleted(last)
    }
}
