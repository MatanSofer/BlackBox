package com.blackbox.android.di

import com.blackbox.android.platform.AndroidStepCounterProvider
import com.blackbox.android.security.BiometricManager
import com.blackbox.android.security.KeyManager
import com.blackbox.android.util.AndroidLogger
import com.blackbox.data.remote.NoOpAiClient
import com.blackbox.data.remote.OpenAiClientImpl
import com.blackbox.domain.platform.StepCounterProvider
import com.blackbox.domain.service.AiClient
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
    single<StepCounterProvider> { AndroidStepCounterProvider(androidContext(), get()) }
    single { KeyManager(androidContext(), get()) }
    single { BiometricManager(get()) }
    single<AiClient> {
        val key = com.blackbox.android.BuildConfig.OPENAI_API_KEY
        if (key.isNotBlank()) OpenAiClientImpl(key) else NoOpAiClient()
    }
}

/**
 * All Koin modules aggregated for application startup.
 */
val appModules = listOf(appModule, databaseModule, repositoryModule, useCaseModule, collectorModule, viewModelModule)
