package com.nutricart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nutricart.app.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    // Flow = the UI automatically gets a new value whenever the row changes.
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observeProfile(): Flow<UserProfileEntity?>

    // Upsert = insert if missing, update in place if the row exists.
    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    // Used by "reset the app" in settings.
    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
