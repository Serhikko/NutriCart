package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Water intake: a running daily total plus quick-add and undo. */
@Singleton
class WaterRepository @Inject constructor(
    private val waterDao: WaterDao,
) {

    fun observeDayTotal(epochDay: Long): Flow<Int> = waterDao.observeDayTotal(epochDay)

    suspend fun add(epochDay: Long, ml: Int) {
        waterDao.insert(
            WaterEntryEntity(
                epochDay = epochDay,
                ml = ml,
                loggedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun undoLast(epochDay: Long) = waterDao.removeLast(epochDay)
}
