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
     * @property permissionsGranted Whether the required runtime permission is
     *   currently granted for each collector. Collectors with no required
     *   permission are always mapped to `true`.
     * @property isServiceRunning Whether the foreground service is running.
     * @property error Error message to display, if any.
     */
    data class State(
        val isLoading: Boolean = false,
        val collectorSettings: List<CollectorSetting> = emptyList(),
        val permissionsGranted: Map<CollectorType, Boolean> = emptyMap(),
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
        /** Screen resumed — re-check runtime permission states. */
        data object RefreshPermissions : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Show a snackbar with a message. */
        data class ShowSnackbar(val message: String) : Event
        /**
         * Open the system app settings page so the user can grant a missing
         * runtime permission (location, activity recognition, microphone).
         *
         * Directing to system settings (rather than showing the permission
         * dialog inline) avoids Android's "two-strikes" permanent-denial rule
         * and gives the user a clear, always-available path to grant any permission.
         * When they return, the ON_RESUME observer automatically re-checks
         * the permission state and flips the toggle to ON if granted.
         */
        data object OpenAppSettings : Event
        /**
         * Open the system Usage Access settings screen so the user can grant
         * the special [android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS]
         * permission for the App Usage collector. This is a separate settings
         * page from the standard app settings and is not a regular runtime permission.
         */
        data object OpenUsageAccessSettings : Event
    }
}
