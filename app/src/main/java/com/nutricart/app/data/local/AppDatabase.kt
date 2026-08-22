package com.nutricart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.FridgeDao
import com.nutricart.app.data.local.dao.NoteDao
import com.nutricart.app.data.local.dao.PlanDao
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.dao.RecurringWorkoutDao
import com.nutricart.app.data.local.dao.SavedMealDao
import com.nutricart.app.data.local.dao.ShoppingDao
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.dao.WorkoutDao
import com.nutricart.app.data.local.entity.DailyActivityEntity
import com.nutricart.app.data.local.entity.DayNoteEntity
import com.nutricart.app.data.local.entity.FoodLogEntryEntity
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.local.entity.FridgeItemEntity
import com.nutricart.app.data.local.entity.IngredientEntity
import com.nutricart.app.data.local.entity.PlannedMealEntity
import com.nutricart.app.data.local.entity.RecipeEntity
import com.nutricart.app.data.local.entity.RecipeIngredientEntity
import com.nutricart.app.data.local.entity.RecipeStepEntity
import com.nutricart.app.data.local.entity.RecurringWorkoutEntity
import com.nutricart.app.data.local.entity.SavedMealEntity
import com.nutricart.app.data.local.entity.SavedMealItemEntity
import com.nutricart.app.data.local.entity.ShoppingListItemEntity
import com.nutricart.app.data.local.entity.UserProfileEntity
import com.nutricart.app.data.local.entity.WaterEntryEntity
import com.nutricart.app.data.local.entity.WeightEntryEntity
import com.nutricart.app.data.local.entity.WorkoutEntryEntity

// Every version bump needs a matching Migration in DatabaseModule.
@Database(
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
        DailyActivityEntity::class,
        FoodProductEntity::class,
        FoodLogEntryEntity::class,
        FridgeItemEntity::class,
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeStepEntity::class,
        RecipeIngredientEntity::class,
        PlannedMealEntity::class,
        ShoppingListItemEntity::class,
        WaterEntryEntity::class,
        WorkoutEntryEntity::class,
        SavedMealEntity::class,
        SavedMealItemEntity::class,
        DayNoteEntity::class,
        RecurringWorkoutEntity::class,
    ],
    version = 12,
    exportSchema = false, // enable + commit schema JSONs before the first real release
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun activityDao(): ActivityDao
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun fridgeDao(): FridgeDao
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun waterDao(): WaterDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun noteDao(): NoteDao
    abstract fun recurringWorkoutDao(): RecurringWorkoutDao
}
