package com.nutricart.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nutricart.app.sync.HealthSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** App entry point; @HiltAndroidApp turns on dependency injection for the whole app. */
@HiltAndroidApp
class NutriCartApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // WorkManager asks for this because its automatic initialization is disabled
    // in the manifest — the Hilt factory lets workers receive injected repositories.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        HealthSyncWorker.schedule(this)
    }
}
