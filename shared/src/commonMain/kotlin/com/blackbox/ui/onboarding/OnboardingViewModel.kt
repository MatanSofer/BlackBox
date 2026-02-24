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
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
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

    private fun requestPermissions() {
        viewModelScope.launch {
            if (!_state.value.locationGranted) {
                _events.emit(OnboardingContract.Event.RequestLocationPermission)
            }
            if (!_state.value.activityGranted) {
                _events.emit(OnboardingContract.Event.RequestActivityPermission)
            }
            if (!_state.value.notificationGranted) {
                _events.emit(OnboardingContract.Event.RequestNotificationPermission)
            }
        }
    }

    companion object {
        /** Index of the permissions request page. */
        private const val PERMISSIONS_PAGE = 1
    }
}
