package com.blackbox.android

import android.app.Application
import com.blackbox.android.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application class for BlackBox.
 *
 * Initializes Koin dependency injection on startup, providing
 * the Android application context and registering all DI modules.
 */
class BlackBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@BlackBoxApplication)
            modules(appModules)
        }
    }
}
