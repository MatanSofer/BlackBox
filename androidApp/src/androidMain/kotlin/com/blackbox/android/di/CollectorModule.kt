package com.blackbox.android.di

import com.blackbox.android.collector.LocationCollector
import com.blackbox.android.collector.base.CollectorOrchestrator
import com.blackbox.android.collector.base.RecordBatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module providing collector infrastructure singletons.
 *
 * The [CollectorOrchestrator] is a singleton that manages all collectors.
 * Each collector is registered with the orchestrator after creation.
 */
val collectorModule = module {
    single { RecordBatcher(get(), get()) }
    single { LocationCollector(androidContext(), get(), get()) }
    single {
        CollectorOrchestrator(get(), get(), get()).apply {
            register(get<LocationCollector>())
        }
    }
}
