package com.blackbox.android.di

import com.blackbox.ui.insights.InsightsViewModel
import com.blackbox.ui.map.MapViewModel
import com.blackbox.ui.onboarding.OnboardingViewModel
import com.blackbox.ui.search.SearchViewModel
import com.blackbox.ui.settings.SettingsViewModel
import com.blackbox.ui.timeline.TimelineViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module providing all screen ViewModels.
 *
 * Each ViewModel is scoped per-instance (viewModel scope) so that
 * Compose Navigation can manage their lifecycle correctly.
 */
val viewModelModule = module {
    viewModel { SearchViewModel(get(), get()) }
    viewModel { TimelineViewModel(get()) }
    viewModel { InsightsViewModel(get()) }
    viewModel { MapViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { OnboardingViewModel(get()) }
}
