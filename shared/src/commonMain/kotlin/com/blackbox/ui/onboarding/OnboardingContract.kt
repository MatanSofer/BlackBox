package com.blackbox.ui.onboarding

/**
 * MVI contract for the Onboarding screen.
 * Defines all possible states, user actions, and one-time events.
 */
object OnboardingContract {

    /**
     * Single immutable UI state for the Onboarding screen.
     *
     * @property currentPage The currently visible onboarding page index.
     * @property totalPages Total number of onboarding pages.
     * @property locationGranted Whether location permission has been granted.
     * @property activityGranted Whether activity recognition permission has been granted.
     * @property notificationGranted Whether notification permission has been granted.
     * @property usageAccessGranted Whether the special Usage Access permission has been granted.
     * @property usageAccessRequested Whether the user has already opened the Usage Access settings.
     * @property locationRequested Whether the location permission dialog has been shown this session.
     * @property activityRequested Whether the activity recognition dialog has been shown this session.
     * @property notificationRequested Whether the notification permission dialog has been shown this session.
     */
    data class State(
        val currentPage: Int = 0,
        val totalPages: Int = 3,
        val locationGranted: Boolean = false,
        val activityGranted: Boolean = false,
        val notificationGranted: Boolean = false,
        val usageAccessGranted: Boolean = false,
        val usageAccessRequested: Boolean = false,
        val locationRequested: Boolean = false,
        val activityRequested: Boolean = false,
        val notificationRequested: Boolean = false,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User tapped "Next" to advance to the next page. */
        data object NextPage : Action
        /** User tapped "Back" to go to the previous page. */
        data object PreviousPage : Action
        /** User tapped "Get Started" on the final page. */
        data object Complete : Action
        /** User granted or denied location permission. */
        data class LocationPermissionResult(val granted: Boolean) : Action
        /** User granted or denied activity recognition permission. */
        data class ActivityPermissionResult(val granted: Boolean) : Action
        /** User granted or denied notification permission. */
        data class NotificationPermissionResult(val granted: Boolean) : Action
        /** User tapped "Skip" to skip permission requests. */
        data object SkipPermissions : Action
        /** User tapped "Open Settings" for the Usage Access special permission. */
        data object OpenUsageAccessSettings : Action
        /** User tapped "I've enabled it" to re-check Usage Access after returning from Settings. */
        data object CheckUsageAccess : Action
        /** Re-checks all runtime permission states (called on screen resume). */
        data object CheckPermissions : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Request location permission from the system. */
        data object RequestLocationPermission : Event
        /** Request activity recognition permission from the system. */
        data object RequestActivityPermission : Event
        /** Request notification permission from the system. */
        data object RequestNotificationPermission : Event
        /** Onboarding is complete — navigate to the main app. */
        data object NavigateToMain : Event
        /** Open the system Usage Access settings screen so the user can grant the permission. */
        data object OpenUsageAccessSettings : Event
    }
}
