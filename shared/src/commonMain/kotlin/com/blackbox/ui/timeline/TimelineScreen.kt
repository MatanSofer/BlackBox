package com.blackbox.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Timeline screen.
 *
 * Connects the [TimelineViewModel] to the pure [TimelineContent] composable.
 *
 * @param viewModel The Timeline ViewModel, provided by Koin.
 */
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineContract.Event.ShowSnackbar -> { /* handled by scaffold */ }
            }
        }
    }

    TimelineContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
