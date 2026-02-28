package com.blackbox.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Onboarding screen.
 *
 * Connects the [OnboardingViewModel] to the pure [OnboardingContent].
 * Handles one-time events for permission requests and navigation.
 *
 * Permission results are detected automatically: a lifecycle observer fires
 * [OnboardingContract.Action.CheckPermissions] each time the screen resumes,
 * which covers the moment the user dismisses a system permission dialog and
 * the app returns to the foreground.
 *
 * @param viewModel The Onboarding ViewModel, provided by Koin.
 * @param onRequestLocationPermission Callback to trigger location permission request.
 * @param onRequestActivityPermission Callback to trigger activity recognition permission request.
 * @param onRequestNotificationPermission Callback to trigger notification permission request.
 * @param onOpenUsageAccessSettings Callback to open the system Usage Access settings screen.
 * @param onOnboardingComplete Callback when onboarding is finished.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    onRequestLocationPermission: () -> Unit = {},
    onRequestActivityPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
    onOnboardingComplete: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-check permission states whenever the screen comes back to the foreground.
    // This automatically picks up results from system permission dialogs without
    // needing to thread result callbacks through the composable hierarchy.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(OnboardingContract.Action.CheckPermissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingContract.Event.RequestLocationPermission ->
                    onRequestLocationPermission()
                is OnboardingContract.Event.RequestActivityPermission ->
                    onRequestActivityPermission()
                is OnboardingContract.Event.RequestNotificationPermission ->
                    onRequestNotificationPermission()
                is OnboardingContract.Event.OpenUsageAccessSettings ->
                    onOpenUsageAccessSettings()
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
