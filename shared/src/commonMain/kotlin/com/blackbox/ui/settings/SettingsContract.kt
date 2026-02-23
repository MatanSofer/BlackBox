package com.blackbox.ui.settings

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting

/**
 * MVI contract for the Settings screen.
 * Defines all possible states, user actions, and one-time events.
 */
object SettingsContract {

    /**
     * Single immutable UI state for the Settings screen.
     *
     * @property isLoading Whether settings are being loaded.
     * @property collectorSettings Current settings for all collectors.
     * @property isServiceRunning Whether the foreground service is running.
     * @property error Error message to display, if any.
     */
    data class State(
        val isLoading: Boolean = false,
        val collectorSettings: List<CollectorSetting> = emptyList(),
        val isServiceRunning: Boolean = false,
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User toggled a collector on or off. */
        data class CollectorToggled(val collectorType: CollectorType, val enabled: Boolean) : Action
        /** User pulled to refresh settings. */
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
