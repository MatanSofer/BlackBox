package com.blackbox.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.place.GetPlacesUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Map screen.
 *
 * Loads known places from the domain layer for display
 * on the map. Map rendering is platform-specific (OSMDroid on Android).
 *
 * @property getPlacesUseCase Use case for fetching known places.
 */
class MapViewModel(
    private val getPlacesUseCase: GetPlacesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MapContract.State())

    /** Observable UI state for the Map screen. */
    val state: StateFlow<MapContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MapContract.Event>()

    /** One-time events for the Map screen. */
    val events: SharedFlow<MapContract.Event> = _events.asSharedFlow()

    init {
        loadPlaces()
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: MapContract.Action) {
        when (action) {
            is MapContract.Action.PlaceClicked -> { /* Detail view — future */ }
            is MapContract.Action.Refresh -> loadPlaces()
        }
    }

    private fun loadPlaces() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getPlacesUseCase()
                .onSuccess { places ->
                    _state.update { it.copy(isLoading = false, knownPlaces = places) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load places")
                    }
                }
        }
    }
}
