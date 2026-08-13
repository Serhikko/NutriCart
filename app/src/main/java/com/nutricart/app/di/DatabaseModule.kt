package com.nutricart.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nutricart.app.data.local.AppDatabase
import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.FoodDao
import com.nutricart.app.data.local.dao.FoodLogDao
import com.nutricart.app.data.local.dao.PlanDao
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.RecipeDao
import com.nutricart.app.data.local.dao.ShoppingDao
import com.nutricart.app.data.local.dao.WaterDao
import com.nutricart.app.data.local.dao.WeightDao
import com.nutricart.app.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * v1 -> v2: adds the daily_activity table (Health Connect cache).
 * A migration = plain SQL that upgrades an existing database WITHOUT losing data.
 * The SQL must match the entity exactly, or Room refuses to open the database.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_activity` (
                `epochDay` INTEGER NOT NULL,
                `steps` INTEGER,
                `activeKcal` REAL,
                `exerciseMinutes` INTEGER,
                `sleepMinutes` INTEGER,
                `avgHeartRateBpm` INTEGER,
                PRIMARY KEY(`epochDay`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v2 -> v3: adds the food cache and the diary tables.
 * The SQL must match the entities exactly (including the foreign key and the
 * two indices), or Room refuses to open the database.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food_product` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `kcalPer100g` REAL NOT NULL,
                `proteinPer100g` REAL NOT NULL,
                `fatPer100g` REAL NOT NULL,
                `carbsPer100g` REAL NOT NULL,
                `servingSizeG` REAL,
                `source` TEXT NOT NULL,
                `cachedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `food_log_entry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `meal` TEXT NOT NULL,
                `productId` TEXT,
                `name` TEXT NOT NULL,
                `grams` REAL,
                `servings` REAL,
                `kcal` REAL NOT NULL,
                `proteinG` REAL NOT NULL,
                `fatG` REAL NOT NULL,
                `carbsG` REAL NOT NULL,
                `loggedAtEpochMillis` INTEGER NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `food_product`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entry_epochDay` ON `food_log_entry` (`epochDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entry_productId` ON `food_log_entry` (`productId`)")
    }
}

/**
 * v3 -> v4: adds the recipe database and the weekly meal plan.
 * The SQL must match the entities exactly (FKs, indices, NOT NULL), or Room
 * refuses to open the database on existing installs.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingredient` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `aisle` TEXT NOT NULL,
                `kcalPer100g` REAL NOT NULL,
                `proteinPer100g` REAL NOT NULL,
                `fatPer100g` REAL NOT NULL,
                `carbsPer100g` REAL NOT NULL,
                `gramsPerPiece` REAL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `cookTimeMin` INTEGER NOT NULL,
                `isVegetarian` INTEGER NOT NULL,
                `containsPork` INTEGER NOT NULL,
                `allergens` TEXT NOT NULL,
                `suitableSlots` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_step` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeId` INTEGER NOT NULL,
                `stepNumber` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_step_recipeId` ON `recipe_step` (`recipeId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ingredient` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeId` INTEGER NOT NULL,
                `ingredientId` INTEGER NOT NULL,
                `grams` REAL NOT NULL,
                FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`ingredientId`) REFERENCES `ingredient`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_recipeId` ON `recipe_ingredient` (`recipeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_ingredientId` ON `recipe_ingredient` (`ingredientId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `planned_meal` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `slot` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `recipeId` INTEGER NOT NULL,
                `portionFactor` REAL NOT NULL,
                `isLocked` INTEGER NOT NULL,
                FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_planned_meal_epochDay_slot_position` ON `planned_meal` (`epochDay`, `slot`, `position`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_meal_recipeId` ON `planned_meal` (`recipeId`)")
    }
}

/** v4 -> v5: adds the materialized shopping list (one current list). */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_list_item` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ingredientName` TEXT NOT NULL,
                `aisle` TEXT NOT NULL,
                `totalGrams` REAL NOT NULL,
                `pieces` INTEGER,
                `isChecked` INTEGER NOT NULL,
                `alreadyHave` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_list_item_ingredientName` " +
                "ON `shopping_list_item` (`ingredientName`)"
        )
    }
}

/** v5 -> v6: adds water tracking. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `water_entry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `ml` INTEGER NOT NULL,
                `loggedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_entry_epochDay` ON `water_entry` (`epochDay`)")
    }
}

/** v6 -> v7: cooking frequency on the profile (existing users default to daily). */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `user_profile` ADD COLUMN `cookingSessionsPerWeek` INTEGER NOT NULL DEFAULT 7"
        )
    }
}

/**
 * v7 -> v8: adds the workout log (manual entries + imported watch sessions).
 * The unique index on hcSessionId de-duplicates re-synced Health Connect
 * sessions; SQLite ignores NULLs in unique indexes, so manual rows never clash.
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_entry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `hcSessionId` TEXT,
                `hcExerciseType` INTEGER,
                `type` TEXT,
                `title` TEXT,
                `minutes` INTEGER,
                `reps` INTEGER,
                `kcal` REAL,
                `loggedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_entry_epochDay` ON `workout_entry` (`epochDay`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_entry_hcSessionId` " +
                "ON `workout_entry` (`hcSessionId`)"
        )
    }
}

/** Tells Hilt how to build the database and its DAOs (one instance for the whole app). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "nutricart.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            )
            // Dev-only safety net for schema changes WITHOUT a migration yet:
            // wipes and recreates the DB. Remove before the first real release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()

    @Provides
    fun provideActivityDao(db: AppDatabase): ActivityDao = db.activityDao()

    @Provides
    fun provideFoodDao(db: AppDatabase): FoodDao = db.foodDao()

    @Provides
    fun provideFoodLogDao(db: AppDatabase): FoodLogDao = db.foodLogDao()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun providePlanDao(db: AppDatabase): PlanDao = db.planDao()

    @Provides
    fun provideShoppingDao(db: AppDatabase): ShoppingDao = db.shoppingDao()

    @Provides
    fun provideWaterDao(db: AppDatabase): WaterDao = db.waterDao()

    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
}
