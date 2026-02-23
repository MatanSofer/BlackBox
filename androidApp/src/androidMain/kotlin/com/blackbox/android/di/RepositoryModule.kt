package com.blackbox.android.di

import com.blackbox.data.repository.InsightRepositoryImpl
import com.blackbox.data.repository.LocationRepositoryImpl
import com.blackbox.data.repository.PlaceRepositoryImpl
import com.blackbox.data.repository.QueryRepositoryImpl
import com.blackbox.data.repository.RecordRepositoryImpl
import com.blackbox.data.repository.SettingsRepositoryImpl
import com.blackbox.data.repository.TimelineRepositoryImpl
import com.blackbox.domain.repository.InsightRepository
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.repository.TimelineRepository
import org.koin.dsl.module

/**
 * Koin module binding repository interfaces to their implementations.
 *
 * Each repository is a singleton backed by [BlackBoxDatabase] and [BlackBoxLogger].
 */
val repositoryModule = module {
    single<RecordRepository> { RecordRepositoryImpl(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get()) }
    single<TimelineRepository> { TimelineRepositoryImpl(get(), get()) }
    single<PlaceRepository> { PlaceRepositoryImpl(get(), get()) }
    single<InsightRepository> { InsightRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    single<QueryRepository> { QueryRepositoryImpl(get(), get()) }
}
