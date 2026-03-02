package com.blackbox.android.di

import com.blackbox.domain.query.EntityExtractor
import com.blackbox.domain.query.IntentClassifier
import com.blackbox.domain.query.QueryBuilder
import com.blackbox.domain.query.QueryEngine
import com.blackbox.domain.query.ResponseGenerator
import com.blackbox.domain.query.TimeExpressionParser
import com.blackbox.domain.usecase.insight.GetInsightsUseCase
import com.blackbox.domain.usecase.place.DetectKnownPlacesUseCase
import com.blackbox.domain.usecase.place.GetPlacesUseCase
import com.blackbox.domain.usecase.query.GetRecentQueriesUseCase
import com.blackbox.domain.usecase.query.ProcessQueryUseCase
import com.blackbox.domain.usecase.record.GetRecordsUseCase
import com.blackbox.domain.usecase.record.SaveLocationRecordUseCase
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.usecase.settings.DumpDbRecordsUseCase
import com.blackbox.domain.usecase.settings.UpdateCollectorSettingUseCase
import com.blackbox.domain.usecase.timeline.GenerateDailySummaryUseCase
import com.blackbox.domain.usecase.timeline.GetCollectorGroupsUseCase
import com.blackbox.domain.usecase.timeline.GetTimelineUseCase
import org.koin.dsl.module

/**
 * Koin module providing domain use cases and query engine singletons.
 */
val useCaseModule = module {
    // Query engine components
    single { TimeExpressionParser() }
    single { IntentClassifier() }
    single { EntityExtractor() }
    single { QueryBuilder(get(), get()) }
    single { ResponseGenerator() }
    single { QueryEngine(get(), get(), get(), get(), get(), get()) }

    // Record use cases
    single { SaveRecordUseCase(get(), get()) }
    single { SaveLocationRecordUseCase(get(), get(), get()) }
    single { GetRecordsUseCase(get(), get()) }

    // Query use cases
    single { ProcessQueryUseCase(get(), get(), get()) }
    single { GetRecentQueriesUseCase(get(), get()) }

    // Timeline use cases
    single { GetTimelineUseCase(get(), get(), get(), get(), get()) }
    single { GetCollectorGroupsUseCase(get(), get(), get(), get(), get()) }
    single { GenerateDailySummaryUseCase(get(), get(), get(), get()) }

    // Place use cases
    single { GetPlacesUseCase(get(), get()) }
    single { DetectKnownPlacesUseCase(get(), get(), get()) }

    // Insight use cases
    single { GetInsightsUseCase(get(), get()) }

    // Settings use cases
    single { UpdateCollectorSettingUseCase(get(), get()) }
    single { DumpDbRecordsUseCase(get(), get()) }
}
