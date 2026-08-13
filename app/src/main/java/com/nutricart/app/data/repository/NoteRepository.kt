package com.nutricart.app.data.repository

import com.nutricart.app.data.local.dao.NoteDao
import com.nutricart.app.data.local.entity.DayNoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Day notes: one short free-text note per calendar day. */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
) {

    fun observeDay(epochDay: Long): Flow<DayNoteEntity?> = noteDao.observeDay(epochDay)

    /** Blank text deletes the note — an empty note is no note. */
    suspend fun save(epochDay: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            noteDao.deleteByDay(epochDay)
        } else {
            noteDao.upsert(
                DayNoteEntity(
                    epochDay = epochDay,
                    text = trimmed,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
        }
    }
}
