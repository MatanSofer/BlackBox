package com.blackbox.android.di

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.blackbox.android.collector.AppUsageCollector
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.ui.insights.InsightsViewModel
import com.blackbox.ui.map.MapViewModel
import com.blackbox.ui.onboarding.OnboardingViewModel
import com.blackbox.ui.search.SearchViewModel
import com.blackbox.ui.settings.SettingsViewModel
import com.blackbox.ui.timeline.TimelineViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module providing all screen ViewModels.
 *
 * Each ViewModel is scoped per-instance (viewModel scope) so that
 * Compose Navigation can manage their lifecycle correctly.
 */
val viewModelModule = module {
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { TimelineViewModel(get(), get(), get()) }
    viewModel { InsightsViewModel(get(), get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel {
        val context = androidContext()
        val appUsageCollector = get<AppUsageCollector>()
        SettingsViewModel(
            settingsRepository = get(),
            updateCollectorSettingUseCase = get(),
            dumpDbRecordsUseCase = get(),
            checkPermission = { type ->
                when (type) {
                    CollectorType.LOCATION ->
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    CollectorType.ACTIVITY ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                        } else true
                    CollectorType.APP_USAGE -> appUsageCollector.hasUsageStatsPermission()
                    CollectorType.AUDIO_LEVEL ->
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    CollectorType.CALL_LOG ->
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                    else -> true
                }
            },
        )
    }
    viewModel {
        val context = androidContext()
        OnboardingViewModel(
            settingsRepository = get(),
            checkUsageAccess = { get<AppUsageCollector>().hasUsageStatsPermission() },
            checkLocationPermission = {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
            },
            checkActivityPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true // implicit on older OS versions
                }
            },
            checkNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true // implicit on older OS versions
                }
            },
        )
    }
}
