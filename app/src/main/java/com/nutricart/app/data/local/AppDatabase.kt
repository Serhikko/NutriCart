package com.nutricart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.entity.DailyActivityEntity
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity

// More tables (recipes, meal plan, shopping list) are added in later build steps.
// Every version bump needs a matching Migration in DatabaseModule.
@Database(
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
        DailyActivityEntity::class,
        FoodProductEntity::class,
        FoodLogEntryEntity::class,
    ],
    version = 3,
    exportSchema = false, // enable + commit schema JSONs before the first real release
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun activityDao(): ActivityDao
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
}
