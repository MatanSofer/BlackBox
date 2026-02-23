package com.blackbox.ui.map

import com.blackbox.domain.model.place.KnownPlace

/**
 * MVI contract for the Map screen.
 * Defines all possible states, user actions, and one-time events.
 */
object MapContract {

    /**
     * Single immutable UI state for the Map screen.
     *
     * @property isLoading Whether map data is being loaded.
     * @property knownPlaces List of known places to display on the map.
     * @property error Error message to display, if any.
     */
    data class State(
        val isLoading: Boolean = false,
        val knownPlaces: List<KnownPlace> = emptyList(),
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User tapped a place on the map. */
        data class PlaceClicked(val place: KnownPlace) : Action
        /** User pulled to refresh. */
        data object Refresh : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Show a snackbar with a message. */
        data class ShowSnackbar(val message: String) : Event
    }
}
