package com.blackbox.android.di

import com.blackbox.android.security.BiometricManager
import com.blackbox.android.security.KeyManager
import com.blackbox.android.util.AndroidLogger
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module providing application-wide singletons.
 *
 * Provides the logger, key manager, and biometric manager.
 */
val appModule = module {
    single<BlackBoxLogger> { AndroidLogger() }
    single { KeyManager(androidContext(), get()) }
    single { BiometricManager(get()) }
}

/**
 * All Koin modules aggregated for application startup.
 */
val appModules = listOf(appModule, databaseModule, repositoryModule, useCaseModule, collectorModule, viewModelModule)
