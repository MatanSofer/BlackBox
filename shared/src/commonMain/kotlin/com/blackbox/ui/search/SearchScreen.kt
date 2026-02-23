package com.blackbox.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Search screen.
 *
 * Connects the [SearchViewModel] to the pure [SearchContent] composable.
 * Collects state, handles one-time events, and passes action lambdas.
 *
 * @param viewModel The Search ViewModel, provided by Koin.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchContract.Event.ShowSnackbar -> { /* handled by scaffold */ }
            }
        }
    }

    SearchContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
