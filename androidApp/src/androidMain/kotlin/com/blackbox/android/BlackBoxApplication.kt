package com.blackbox.android

import android.app.Application
import com.blackbox.android.di.appModules
import com.blackbox.android.worker.CleanupWorker
import com.blackbox.android.worker.DailySummaryWorker
import com.blackbox.android.worker.GeocodingRetryWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application class for BlackBox.
 *
 * Initializes Koin dependency injection on startup, provides
 * the Android application context, registers all DI modules,
 * and schedules periodic background workers.
 */
class BlackBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@BlackBoxApplication)
            modules(appModules)
        }

        scheduleWorkers()
    }

    /**
     * Schedules periodic WorkManager jobs for daily summary
     * generation and data retention cleanup.
     */
    private fun scheduleWorkers() {
        DailySummaryWorker.schedule(this)
        CleanupWorker.schedule(this)
        GeocodingRetryWorker.schedule(this)
    }
}
