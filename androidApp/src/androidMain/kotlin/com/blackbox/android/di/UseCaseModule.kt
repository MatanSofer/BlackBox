package com.blackbox.android.di

import com.blackbox.domain.query.EntityExtractor
import com.blackbox.domain.query.IntentClassifier
import com.blackbox.domain.query.QueryBuilder
import com.blackbox.domain.query.QueryEngine
import com.blackbox.domain.query.ResponseGenerator
import com.blackbox.domain.query.TimeExpressionParser
import com.blackbox.domain.usecase.record.SaveLocationRecordUseCase
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import org.koin.dsl.module

/**
 * Koin module providing domain use cases and query engine singletons.
 */
val useCaseModule = module {
    // Record use cases
    single { SaveRecordUseCase(get(), get()) }
    single { SaveLocationRecordUseCase(get(), get(), get()) }

    // Query engine components
    single { TimeExpressionParser() }
    single { IntentClassifier() }
    single { EntityExtractor() }
    single { QueryBuilder(get(), get()) }
    single { ResponseGenerator() }
    single { QueryEngine(get(), get(), get(), get(), get(), get()) }
}
