package com.nutricart.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nutricart.app.data.local.AppDatabase
import com.nutricart.app.data.local.dao.ActivityDao
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
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

/** Tells Hilt how to build the database and its DAOs (one instance for the whole app). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "nutricart.db")
            .addMigrations(MIGRATION_1_2)
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
}
