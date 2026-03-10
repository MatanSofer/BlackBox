package com.blackbox.ui.timeline

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.timeline.CollectorGroup

/**
 * MVI contract for the Timeline screen.
 * Defines all possible states, user actions, and one-time events.
 */
object TimelineContract {

    /**
     * Single immutable UI state for the Timeline screen.
     *
     * @property selectedDate     ISO date string for the currently viewed day.
     * @property isLoading        Whether timeline data is being loaded.
     * @property groups           Collector groups for the selected day.
     * @property showAllCollectors Whether the Timeline shows all 10 collectors' raw records.
     * @property error            Error message to display, if any.
     * @property availableDates   Dates that have at least one record; used to grey out empty days in the picker.
     * @property showDatePicker   Whether the date picker dialog is visible.
     */
    data class State(
        val selectedDate: String = "",
        val isLoading: Boolean = false,
        val groups: List<CollectorGroup> = emptyList(),
        val showAllCollectors: Boolean = false,
        val error: String? = null,
        val availableDates: Set<String> = emptySet(),
        val showDatePicker: Boolean = false,
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
        /** User pulled to refresh. */
        data object Refresh : Action
        /** User tapped a group card header to expand or collapse it. */
        data class GroupToggled(val collectorType: CollectorType?) : Action
        /** User tapped the date display to open the picker. */
        data object ShowDatePicker : Action
        /** User dismissed the date picker without selecting. */
        data object DismissDatePicker : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Show a snackbar with a message. */
        data class ShowSnackbar(val message: String) : Event
    }
}
