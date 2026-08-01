package com.nutricart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity

// More tables (food, recipes, meal plan, shopping list) are added in later build steps.
@Database(
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
    ],
    version = 1,
    exportSchema = false, // enable + commit schema JSONs before the first real release
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
}
