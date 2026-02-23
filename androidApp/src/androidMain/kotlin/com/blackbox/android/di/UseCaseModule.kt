package com.blackbox.android.di

import com.blackbox.domain.usecase.record.SaveRecordUseCase
import org.koin.dsl.module

/**
 * Koin module providing domain use case singletons.
 */
val useCaseModule = module {
    single { SaveRecordUseCase(get(), get()) }
}
