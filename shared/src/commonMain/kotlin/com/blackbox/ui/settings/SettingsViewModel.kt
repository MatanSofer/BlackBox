package com.blackbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.model.settings.RetentionPeriod
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.usecase.settings.DumpDbRecordsUseCase
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
 * The toggle for each collector reflects its **effective** state:
 * both `isEnabled` (user preference) and the required runtime permission
 * must be true for the collector to appear active. If the user tries to
 * enable a collector whose permission is not granted, the ViewModel saves
 * the preference (so it auto-enables once the permission is granted) and
 * emits [SettingsContract.Event.RequestPermission] to trigger the system dialog.
 *
 * @property settingsRepository Repository for reading collector settings.
 * @property updateCollectorSettingUseCase Use case for toggling collectors.
 * @property checkPermission Platform function that returns whether the runtime
 *   permission required by a given [CollectorType] is currently granted.
 *   Collectors that need no special permission always return `true`.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateCollectorSettingUseCase: UpdateCollectorSettingUseCase,
    private val dumpDbRecordsUseCase: DumpDbRecordsUseCase,
    private val checkPermission: (CollectorType) -> Boolean = { true },
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
        refreshPermissions()
        loadRawDataViewEnabled()
        loadRetentionPeriod()
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: SettingsContract.Action) {
        when (action) {
            is SettingsContract.Action.CollectorToggled -> handleToggle(action.collectorType, action.enabled)
            is SettingsContract.Action.Refresh -> loadSettings()
            is SettingsContract.Action.RefreshPermissions -> refreshPermissions()
            is SettingsContract.Action.RawDataViewToggled -> handleRawDataViewToggled(action.enabled)
            is SettingsContract.Action.RetentionPeriodChanged -> handleRetentionPeriodChanged(action.period)
            is SettingsContract.Action.DumpDbRecords -> handleDumpDbRecords()
            is SettingsContract.Action.DismissDbDump -> _state.update { it.copy(dbDumpText = null) }
        }
    }

    private fun handleToggle(collectorType: CollectorType, enabled: Boolean) {
        viewModelScope.launch {
            // Always persist the user's intent so the collector auto-activates
            // as soon as the permission is granted.
            updateCollectorSettingUseCase(collectorType, enabled)
                .onFailure { error ->
                    _events.emit(
                        SettingsContract.Event.ShowSnackbar(
                            error.message ?: "Failed to update setting",
                        ),
                    )
                    return@launch
                }

            // If enabling but the permission isn't granted, send the user to
            // the appropriate settings screen where they can grant it.
            // App Usage requires a dedicated Usage Access settings page;
            // all other collectors use the standard app settings page.
            if (enabled && !checkPermission(collectorType)) {
                val event = if (collectorType == CollectorType.APP_USAGE) {
                    SettingsContract.Event.OpenUsageAccessSettings
                } else {
                    SettingsContract.Event.OpenAppSettings
                }
                _events.emit(event)
            }
        }
    }

    /**
     * Queries the current runtime permission state for every collector and
     * updates [SettingsContract.State.permissionsGranted].
     */
    private fun refreshPermissions() {
        val permissions = CollectorType.entries.associateWith { checkPermission(it) }
        _state.update { it.copy(permissionsGranted = permissions) }
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

    private fun loadRawDataViewEnabled() {
        viewModelScope.launch {
            runCatching { settingsRepository.isRawDataViewEnabled() }
                .onSuccess { enabled ->
                    _state.update { it.copy(isRawDataViewEnabled = enabled) }
                }
        }
    }

    private fun handleRawDataViewToggled(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isRawDataViewEnabled = enabled) }
            runCatching { settingsRepository.setRawDataViewEnabled(enabled) }
                .onFailure { error ->
                    _state.update { it.copy(isRawDataViewEnabled = !enabled) }
                    _events.emit(
                        SettingsContract.Event.ShowSnackbar(
                            error.message ?: "Failed to update setting",
                        ),
                    )
                }
        }
    }

    private fun handleDumpDbRecords() {
        viewModelScope.launch {
            _state.update { it.copy(isDumpLoading = true) }
            dumpDbRecordsUseCase()
                .onSuccess { text ->
                    _state.update { it.copy(isDumpLoading = false, dbDumpText = text) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isDumpLoading = false) }
                    _events.emit(
                        SettingsContract.Event.ShowSnackbar(
                            error.message ?: "DB dump failed",
                        ),
                    )
                }
        }
    }

    private fun loadRetentionPeriod() {
        viewModelScope.launch {
            runCatching { settingsRepository.getRetentionPeriod() }
                .onSuccess { period ->
                    _state.update { it.copy(retentionPeriod = period) }
                }
        }
    }

    private fun handleRetentionPeriodChanged(period: RetentionPeriod) {
        viewModelScope.launch {
            val previous = _state.value.retentionPeriod
            _state.update { it.copy(retentionPeriod = period) }
            runCatching { settingsRepository.setRetentionPeriod(period) }
                .onFailure { error ->
                    _state.update { it.copy(retentionPeriod = previous) }
                    _events.emit(
                        SettingsContract.Event.ShowSnackbar(
                            error.message ?: "Failed to update retention period",
                        ),
                    )
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
