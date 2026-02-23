package com.blackbox.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Settings screen.
 *
 * Connects the [SettingsViewModel] to the pure [SettingsContent] composable.
 *
 * @param viewModel The Settings ViewModel, provided by Koin.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsContract.Event.ShowSnackbar -> { /* handled by scaffold */ }
            }
        }
    }

    SettingsContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
