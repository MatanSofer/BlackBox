package com.blackbox.ui.timeline

import com.blackbox.domain.model.timeline.TimelineEntry

/**
 * MVI contract for the Timeline screen.
 * Defines all possible states, user actions, and one-time events.
 */
object TimelineContract {

    /**
     * Single immutable UI state for the Timeline screen.
     *
     * @property selectedDate ISO date string for the currently viewed day.
     * @property isLoading Whether timeline data is being loaded.
     * @property entries Timeline entries for the selected day.
     * @property error Error message to display, if any.
     */
    data class State(
        val selectedDate: String = "",
        val isLoading: Boolean = false,
        val entries: List<TimelineEntry> = emptyList(),
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User selected a different date. */
        data class DateSelected(val date: String) : Action
        /** User navigated to the previous day. */
        data object PreviousDay : Action
        /** User navigated to the next day. */
        data object NextDay : Action
        /** User tapped a timeline entry. */
        data class EntryClicked(val entry: TimelineEntry) : Action
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
