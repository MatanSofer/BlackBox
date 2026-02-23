package com.blackbox.android.di

import com.blackbox.android.util.AndroidLogger
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.dsl.module

/**
 * Koin module providing application-wide singletons.
 *
 * Currently provides the [BlackBoxLogger] implementation.
 */
val appModule = module {
    single<BlackBoxLogger> { AndroidLogger() }
}

/**
 * All Koin modules aggregated for application startup.
 */
val appModules = listOf(appModule, databaseModule, repositoryModule)
