package com.blackbox.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Insights screen.
 *
 * Connects the [InsightsViewModel] to the pure [InsightsContent] composable.
 * Dispatches [InsightsContract.Action.ScreenResumed] every time the screen
 * comes back into focus so the ViewModel can refresh stale data.
 *
 * @param viewModel The Insights ViewModel, provided by Koin.
 */
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Refresh data when the screen resumes (e.g. navigated back from another screen).
    // The ViewModel skips the reload if the data is still fresh (< 5 min old),
    // so there is no visible flash during quick navigation.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(InsightsContract.Action.ScreenResumed)
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InsightsContract.Event.ShowSnackbar -> { /* handled by scaffold */ }
            }
        }
    }

    InsightsContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
