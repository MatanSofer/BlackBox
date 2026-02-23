package com.blackbox.android.di

import com.blackbox.android.collector.base.CollectorOrchestrator
import com.blackbox.android.collector.base.RecordBatcher
import org.koin.dsl.module

/**
 * Koin module providing collector infrastructure singletons.
 *
 * The [CollectorOrchestrator] is a singleton that manages all collectors.
 * Individual collector registrations will be added in subsequent steps
 * (Steps 7-11) as each collector is implemented.
 */
val collectorModule = module {
    single { RecordBatcher(get(), get()) }
    single { CollectorOrchestrator(get(), get(), get()) }
}
