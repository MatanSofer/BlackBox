package com.blackbox.ui.map

import com.blackbox.domain.model.map.DayLocationSummary
import com.blackbox.domain.model.map.LocationStay

/**
 * MVI contract for the Map screen.
 * Defines all possible states, user actions, and one-time events.
 */
object MapContract {

    /**
     * Single immutable UI state for the Map screen.
     *
     * @property selectedDate Currently displayed date, formatted as "yyyy-MM-dd".
     * @property isLoading Whether location data is being loaded.
     * @property summary Day location summary for [selectedDate], or null while loading.
     * @property selectedStay The stay dot currently tapped by the user, or null.
     * @property error Error message to display, or null.
     */
    data class State(
        val selectedDate: String = "",
        val isLoading: Boolean = false,
        val summary: DayLocationSummary? = null,
        val selectedStay: LocationStay? = null,
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** Navigate to the previous day. */
        data object PreviousDay : Action
        /** Navigate to the next day. */
        data object NextDay : Action
        /** Jump directly to a specific date. */
        data class DateSelected(val date: String) : Action
        /** User tapped a stay dot (non-null) or the map background (null = deselect). */
        data class StayTapped(val stay: LocationStay?) : Action
        /** Reload location data for the current date. */
        data object Refresh : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        data class ShowSnackbar(val message: String) : Event
    }
}
