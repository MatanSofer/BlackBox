package com.blackbox.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen root for the Map screen.
 *
 * Connects the [MapViewModel] to the pure [MapContent] composable.
 * The full OSMDroid map integration will be added in a future step.
 *
 * @param viewModel The Map ViewModel, provided by Koin.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MapContract.Event.ShowSnackbar -> { /* handled by scaffold */ }
            }
        }
    }

    MapContent(
        state = state,
        onAction = viewModel::onAction,
    )
}
