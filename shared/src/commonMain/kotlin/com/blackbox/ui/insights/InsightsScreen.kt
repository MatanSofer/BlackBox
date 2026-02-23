package com.blackbox.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Insights screen.
 *
 * Connects the [InsightsViewModel] to the pure [InsightsContent] composable.
 *
 * @param viewModel The Insights ViewModel, provided by Koin.
 */
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
