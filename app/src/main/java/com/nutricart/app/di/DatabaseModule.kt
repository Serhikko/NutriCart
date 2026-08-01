package com.nutricart.app.di

import android.content.Context
import androidx.room.Room
import com.nutricart.app.data.local.AppDatabase
import com.nutricart.app.data.local.dao.ProfileDao
import com.nutricart.app.data.local.dao.WeightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Tells Hilt how to build the database and its DAOs (one instance for the whole app). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "nutricart.db")
            // Dev-only shortcut: on a schema change, wipe and recreate the DB.
            // Must be replaced with real Migration objects before the first release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()
}
