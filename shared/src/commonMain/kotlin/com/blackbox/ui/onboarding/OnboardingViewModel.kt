package com.blackbox.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Onboarding screen.
 *
 * Manages the multi-page onboarding flow including permission
 * requests and onboarding completion persistence.
 *
 * @property settingsRepository Repository for persisting onboarding state.
 * @property checkUsageAccess Platform function that returns true when the
 *   [AppOpsManager.OPSTR_GET_USAGE_STATS] special permission is granted.
 * @property checkLocationPermission Platform function that returns true when
 *   ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted.
 * @property checkActivityPermission Platform function that returns true when
 *   ACTIVITY_RECOGNITION is granted (or OS < API 29 where it is implicit).
 * @property checkNotificationPermission Platform function that returns true when
 *   POST_NOTIFICATIONS is granted (or OS < API 33 where it is implicit).
 *
 * All check functions are injected so the shared-module ViewModel can query
 * Android-only APIs without importing Android classes directly.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val checkUsageAccess: () -> Boolean = { false },
    private val checkLocationPermission: () -> Boolean = { false },
    private val checkActivityPermission: () -> Boolean = { true },
    private val checkNotificationPermission: () -> Boolean = { true },
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingContract.State())

    /** Observable UI state for the Onboarding screen. */
    val state: StateFlow<OnboardingContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingContract.Event>()

    /** One-time events for the Onboarding screen. */
    val events: SharedFlow<OnboardingContract.Event> = _events.asSharedFlow()

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: OnboardingContract.Action) {
        when (action) {
            is OnboardingContract.Action.NextPage -> handleNextPage()
            is OnboardingContract.Action.PreviousPage -> handlePreviousPage()
            is OnboardingContract.Action.Complete -> handleComplete()
            is OnboardingContract.Action.LocationPermissionResult ->
                _state.update { it.copy(locationGranted = action.granted) }
            is OnboardingContract.Action.ActivityPermissionResult ->
                _state.update { it.copy(activityGranted = action.granted) }
            is OnboardingContract.Action.NotificationPermissionResult ->
                _state.update { it.copy(notificationGranted = action.granted) }
            is OnboardingContract.Action.SkipPermissions -> handleComplete()
            is OnboardingContract.Action.OpenUsageAccessSettings -> handleOpenUsageAccessSettings()
            is OnboardingContract.Action.CheckUsageAccess -> handleCheckUsageAccess()
            is OnboardingContract.Action.CheckPermissions -> handleCheckPermissions()
        }
    }

    private fun handleNextPage() {
        val current = _state.value.currentPage
        if (current < _state.value.totalPages - 1) {
            _state.update { it.copy(currentPage = current + 1) }

            // Trigger permission requests when entering the permissions page
            if (current + 1 == PERMISSIONS_PAGE) {
                requestPermissions()
            }
        }
    }

    private fun handlePreviousPage() {
        val current = _state.value.currentPage
        if (current > 0) {
            _state.update { it.copy(currentPage = current - 1) }
        }
    }

    private fun handleComplete() {
        viewModelScope.launch {
            settingsRepository.setOnboardingComplete()
            _events.emit(OnboardingContract.Event.NavigateToMain)
        }
    }

    private fun handleOpenUsageAccessSettings() {
        _state.update { it.copy(usageAccessRequested = true) }
        viewModelScope.launch {
            _events.emit(OnboardingContract.Event.OpenUsageAccessSettings)
        }
    }

    private fun handleCheckUsageAccess() {
        _state.update { it.copy(usageAccessGranted = checkUsageAccess()) }
    }

    /**
     * Re-queries all runtime permission states and then requests the next
     * pending permission if the sequence is still in progress.
     *
     * Called on every ON_RESUME of the onboarding screen, which fires each
     * time the user dismisses a system permission dialog and the app returns
     * to the foreground. By requesting one permission at a time this way,
     * we avoid calling [ActivityResultLauncher.launch] while the activity is
     * already paused (which silently drops the request on Android 10+).
     */
    private fun handleCheckPermissions() {
        _state.update {
            it.copy(
                locationGranted = checkLocationPermission(),
                activityGranted = checkActivityPermission(),
                notificationGranted = checkNotificationPermission(),
            )
        }
        // If we're on the permissions page and the sequence has started,
        // request the next permission that hasn't been shown yet.
        if (_state.value.currentPage == PERMISSIONS_PAGE && _state.value.locationRequested) {
            viewModelScope.launch { requestNextPermission() }
        }
    }

    private fun requestPermissions() {
        viewModelScope.launch {
            requestNextPermission()
        }
    }

    /**
     * Emits a request for the next permission that hasn't been requested yet this session.
     *
     * Permissions are requested one at a time. Each call requests exactly one permission
     * and returns. The next permission is requested after the current dialog is dismissed
     * (detected via ON_RESUME → [handleCheckPermissions]).
     *
     * Permissions that have already been requested are skipped to avoid
     * showing the dialog again after a deny.
     */
    private suspend fun requestNextPermission() {
        val state = _state.value
        when {
            !state.locationRequested -> {
                _state.update { it.copy(locationRequested = true) }
                _events.emit(OnboardingContract.Event.RequestLocationPermission)
            }
            !state.activityRequested -> {
                _state.update { it.copy(activityRequested = true) }
                _events.emit(OnboardingContract.Event.RequestActivityPermission)
            }
            !state.notificationRequested -> {
                _state.update { it.copy(notificationRequested = true) }
                _events.emit(OnboardingContract.Event.RequestNotificationPermission)
            }
            // All permissions have been requested — nothing more to do.
        }
    }

    companion object {
        /** Index of the permissions request page. */
        private const val PERMISSIONS_PAGE = 1
    }
}
