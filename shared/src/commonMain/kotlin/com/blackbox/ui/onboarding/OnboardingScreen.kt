package com.blackbox.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Onboarding screen.
 *
 * Connects the [OnboardingViewModel] to the pure [OnboardingContent].
 * Handles one-time events for permission requests and navigation.
 *
 * @param viewModel The Onboarding ViewModel, provided by Koin.
 * @param onRequestLocationPermission Callback to trigger location permission request.
 * @param onRequestActivityPermission Callback to trigger activity recognition permission request.
 * @param onRequestNotificationPermission Callback to trigger notification permission request.
 * @param onOnboardingComplete Callback when onboarding is finished.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    onRequestLocationPermission: () -> Unit = {},
    onRequestActivityPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOnboardingComplete: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingContract.Event.RequestLocationPermission ->
                    onRequestLocationPermission()
                is OnboardingContract.Event.RequestActivityPermission ->
                    onRequestActivityPermission()
                is OnboardingContract.Event.RequestNotificationPermission ->
                    onRequestNotificationPermission()
                is OnboardingContract.Event.NavigateToMain ->
                    onOnboardingComplete()
            }
        }
    }

    OnboardingContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
