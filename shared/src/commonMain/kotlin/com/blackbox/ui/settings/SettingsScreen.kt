package com.blackbox.ui.settings

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
 * Screen root for the Settings screen.
 *
 * Connects the [SettingsViewModel] to the pure [SettingsContent] composable.
 * Re-checks runtime permission states on every resume so that toggles
 * automatically reflect permission changes made in the system Settings app.
 *
 * When the user tries to enable a collector without the required permission,
 * [onOpenAppSettings] or [onOpenUsageAccessSettings] is invoked depending on
 * the collector. On return, the ON_RESUME observer detects the new permission
 * state and the toggle automatically turns on.
 *
 * @param viewModel The Settings ViewModel, provided by Koin.
 * @param onOpenAppSettings Callback to open the system app settings for this app
 *   (for runtime permissions: location, activity, microphone).
 * @param onOpenUsageAccessSettings Callback to open the Usage Access settings screen
 *   (for the App Usage collector, which requires a special non-runtime permission).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onOpenAppSettings: () -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Refresh permission states whenever the screen comes back to the foreground,
    // e.g. after the user grants a permission in the system Settings app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(SettingsContract.Action.RefreshPermissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsContract.Event.ShowSnackbar -> { /* TODO: wire snackbar to Scaffold */ }
                is SettingsContract.Event.OpenAppSettings -> onOpenAppSettings()
                is SettingsContract.Event.OpenUsageAccessSettings -> onOpenUsageAccessSettings()
            }
        }
    }

    SettingsContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
