package com.blackbox.android.di

import com.blackbox.android.collector.ActivityCollector
import com.blackbox.android.collector.AppUsageCollector
import com.blackbox.android.collector.AudioLevelCollector
import com.blackbox.android.collector.BarometerCollector
import com.blackbox.android.collector.BatteryCollector
import com.blackbox.android.collector.ConnectivityCollector
import com.blackbox.android.collector.LightCollector
import com.blackbox.android.collector.LocationCollector
import com.blackbox.android.collector.ScreenStateCollector
import com.blackbox.android.collector.WifiCollector
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

    // Collectors
    single { LocationCollector(androidContext(), get(), get()) }
    single { ActivityCollector(androidContext(), get(), get()) }
    single { WifiCollector(androidContext(), get(), get()) }
    single { AppUsageCollector(androidContext(), get(), get()) }
    single { ScreenStateCollector(androidContext(), get(), get()) }
    single { AudioLevelCollector(androidContext(), get(), get()) }
    single { BatteryCollector(androidContext(), get(), get()) }
    single { ConnectivityCollector(androidContext(), get(), get()) }
    single { BarometerCollector(androidContext(), get(), get()) }
    single { LightCollector(androidContext(), get(), get()) }

    // Orchestrator with all collectors registered
    single {
        CollectorOrchestrator(get(), get(), get()).apply {
            register(get<LocationCollector>())
            register(get<ActivityCollector>())
            register(get<WifiCollector>())
            register(get<AppUsageCollector>())
            register(get<ScreenStateCollector>())
            register(get<AudioLevelCollector>())
            register(get<BatteryCollector>())
            register(get<ConnectivityCollector>())
            register(get<BarometerCollector>())
            register(get<LightCollector>())
        }
    }
}
