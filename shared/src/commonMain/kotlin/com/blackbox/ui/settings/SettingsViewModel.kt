package com.blackbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.usecase.settings.UpdateCollectorSettingUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Manages collector settings by observing the settings repository
 * and delegating updates through the use case layer.
 *
 * @property settingsRepository Repository for reading collector settings.
 * @property updateCollectorSettingUseCase Use case for toggling collectors.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateCollectorSettingUseCase: UpdateCollectorSettingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsContract.State())

    /** Observable UI state for the Settings screen. */
    val state: StateFlow<SettingsContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsContract.Event>()

    /** One-time events for the Settings screen. */
    val events: SharedFlow<SettingsContract.Event> = _events.asSharedFlow()

    init {
        observeSettings()
        loadSettings()
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: SettingsContract.Action) {
        when (action) {
            is SettingsContract.Action.CollectorToggled -> handleToggle(action.collectorType, action.enabled)
            is SettingsContract.Action.Refresh -> loadSettings()
        }
    }

    private fun handleToggle(collectorType: CollectorType, enabled: Boolean) {
        viewModelScope.launch {
            updateCollectorSettingUseCase(collectorType, enabled)
                .onFailure { error ->
                    _events.emit(
                        SettingsContract.Event.ShowSnackbar(
                            error.message ?: "Failed to update setting",
                        ),
                    )
                }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { settingsRepository.getAllSettings() }
                .onSuccess { settings ->
                    _state.update { it.copy(isLoading = false, collectorSettings = settings) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load settings")
                    }
                }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update { it.copy(collectorSettings = settings) }
            }
        }
    }
}
