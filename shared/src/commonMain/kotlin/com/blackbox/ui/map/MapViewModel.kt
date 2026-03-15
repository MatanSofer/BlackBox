package com.blackbox.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.usecase.map.GetDayLocationSummaryUseCase
import com.blackbox.domain.usecase.place.DeletePlaceUseCase
import com.blackbox.domain.usecase.place.GetPlacesUseCase
import com.blackbox.domain.usecase.place.SavePlaceUseCase
import com.blackbox.domain.usecase.place.UpdatePlaceUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Map screen.
 *
 * Manages day-based navigation, a reactive location summary stream,
 * a known-places list, and a route-playback animation.
 *
 * @property getDayLocationSummaryUseCase Clusters GPS fixes into stays.
 * @property recordRepository Provides dates with recorded data.
 * @property getPlacesUseCase Loads all known places for the Places tab.
 */
class MapViewModel(
    private val getDayLocationSummaryUseCase: GetDayLocationSummaryUseCase,
    private val recordRepository: RecordRepository,
    private val getPlacesUseCase: GetPlacesUseCase,
    private val savePlaceUseCase: SavePlaceUseCase,
    private val deletePlaceUseCase: DeletePlaceUseCase,
    private val updatePlaceUseCase: UpdatePlaceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MapContract.State())

    /** Observable UI state for the Map screen. */
    val state: StateFlow<MapContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MapContract.Event>()

    /** One-time events for the Map screen. */
    val events: SharedFlow<MapContract.Event> = _events.asSharedFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Tracks the running playback coroutine so it can be cancelled cleanly. */
    private var playbackJob: Job? = null

    init {
        val today = dateFormat.format(Date())
        _state.update { it.copy(selectedDate = today, isLoading = true) }
        observeLocationSummary()
        loadAvailableDates()
    }

    private fun loadAvailableDates() {
        viewModelScope.launch {
            runCatching { recordRepository.getDatesWithData() }
                .onSuccess { dates -> _state.update { it.copy(availableDates = dates.toSet()) } }
        }
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: MapContract.Action) {
        when (action) {
            is MapContract.Action.PreviousDay -> handlePreviousDay()
            is MapContract.Action.NextDay -> handleNextDay()
            is MapContract.Action.DateSelected ->
                _state.update { it.copy(selectedDate = action.date, isLoading = true, selectedStay = null, showDatePicker = false) }
            is MapContract.Action.StayTapped ->
                _state.update { it.copy(selectedStay = action.stay) }
            is MapContract.Action.Refresh ->
                _state.update { it.copy(isLoading = true, selectedStay = null) }
            is MapContract.Action.ShowDatePicker ->
                _state.update { it.copy(showDatePicker = true) }
            is MapContract.Action.DismissDatePicker ->
                _state.update { it.copy(showDatePicker = false) }
            is MapContract.Action.SwitchMode -> handleSwitchMode(action.mode)
            is MapContract.Action.StartRoutePlayback -> handleStartPlayback()
            is MapContract.Action.StopRoutePlayback -> handleStopPlayback()
            is MapContract.Action.OpenAddPlaceDialog -> handleOpenAddPlaceDialog()
            is MapContract.Action.DismissAddPlaceDialog ->
                _state.update { it.copy(addPlaceDialog = null) }
            is MapContract.Action.AddPlaceNameChanged ->
                _state.update { it.copy(addPlaceDialog = it.addPlaceDialog?.copy(name = action.name)) }
            is MapContract.Action.AddPlaceCategoryChanged ->
                _state.update { it.copy(addPlaceDialog = it.addPlaceDialog?.copy(category = action.category)) }
            is MapContract.Action.AddPlaceLatChanged ->
                _state.update { it.copy(addPlaceDialog = it.addPlaceDialog?.copy(latText = action.text)) }
            is MapContract.Action.AddPlaceLngChanged ->
                _state.update { it.copy(addPlaceDialog = it.addPlaceDialog?.copy(lngText = action.text)) }
            is MapContract.Action.AddPlaceRadiusChanged ->
                _state.update { it.copy(addPlaceDialog = it.addPlaceDialog?.copy(radiusMeters = action.radiusMeters)) }
            is MapContract.Action.ConfirmAddPlace -> handleConfirmAddPlace()
            is MapContract.Action.DeletePlace -> handleDeletePlace(action.placeId)
            is MapContract.Action.OpenEditPlaceDialog ->
                _state.update { it.copy(editPlaceDialog = MapContract.EditPlaceDialogState(originalPlace = action.place)) }
            is MapContract.Action.DismissEditPlaceDialog ->
                _state.update { it.copy(editPlaceDialog = null) }
            is MapContract.Action.EditPlaceNameChanged ->
                _state.update { it.copy(editPlaceDialog = it.editPlaceDialog?.copy(name = action.name)) }
            is MapContract.Action.EditPlaceCategoryChanged ->
                _state.update { it.copy(editPlaceDialog = it.editPlaceDialog?.copy(category = action.category)) }
            is MapContract.Action.EditPlaceRadiusChanged ->
                _state.update { it.copy(editPlaceDialog = it.editPlaceDialog?.copy(radiusMeters = action.radiusMeters)) }
            is MapContract.Action.ConfirmEditPlace -> handleConfirmEditPlace()
        }
    }

    /**
     * Observes location summary reactively. Restarts automatically when the
     * selected date changes (via [flatMapLatest]) or when new fixes are written
     * by the background service.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocationSummary() {
        viewModelScope.launch {
            _state
                .map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (start, end) = dayBoundsMs(date)
                    getDayLocationSummaryUseCase.observe(date, start, end)
                }
                .collect { result ->
                    result.onSuccess { summary ->
                        _state.update { it.copy(isLoading = false, summary = summary, error = null) }
                    }
                    result.onFailure { error ->
                        _state.update {
                            it.copy(isLoading = false, error = error.message ?: "Failed to load locations")
                        }
                    }
                }
        }
    }

    private fun handleOpenAddPlaceDialog() {
        val lastStay = _state.value.summary?.stays?.lastOrNull()
        val lat = lastStay?.latitude ?: 0.0
        val lng = lastStay?.longitude ?: 0.0
        _state.update {
            it.copy(
                addPlaceDialog = MapContract.AddPlaceDialogState(
                    latText = "%.6f".format(lat),
                    lngText = "%.6f".format(lng),
                ),
            )
        }
    }

    private fun handleConfirmAddPlace() {
        val dialog = _state.value.addPlaceDialog ?: return
        if (!dialog.isValid) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val place = KnownPlace(
                name = dialog.name.trim(),
                latitude = dialog.latText.toDouble(),
                longitude = dialog.lngText.toDouble(),
                radiusMeters = dialog.radiusMeters.toDouble(),
                category = dialog.category,
                isAutoDetected = false,
                createdAt = now,
                updatedAt = now,
            )
            savePlaceUseCase(place)
                .onSuccess {
                    _state.update { it.copy(addPlaceDialog = null) }
                    loadPlaces()
                    _events.emit(MapContract.Event.ShowSnackbar("Place saved"))
                }
                .onFailure {
                    _events.emit(MapContract.Event.ShowSnackbar("Failed to save place"))
                }
        }
    }

    private fun handleConfirmEditPlace() {
        val dialog = _state.value.editPlaceDialog ?: return
        if (!dialog.isValid) return

        viewModelScope.launch {
            val updated = dialog.originalPlace.copy(
                name = dialog.name.trim(),
                category = dialog.category,
                radiusMeters = dialog.radiusMeters.toDouble(),
                updatedAt = System.currentTimeMillis(),
            )
            updatePlaceUseCase(updated)
                .onSuccess {
                    _state.update { it.copy(editPlaceDialog = null) }
                    loadPlaces()
                    _events.emit(MapContract.Event.ShowSnackbar("Place updated"))
                }
                .onFailure {
                    _events.emit(MapContract.Event.ShowSnackbar("Failed to update place"))
                }
        }
    }

    private fun handleDeletePlace(placeId: Long) {
        viewModelScope.launch {
            deletePlaceUseCase(placeId)
                .onSuccess {
                    loadPlaces()
                    _events.emit(MapContract.Event.ShowSnackbar("Place removed"))
                }
                .onFailure {
                    _events.emit(MapContract.Event.ShowSnackbar("Failed to remove place"))
                }
        }
    }

    private fun handleSwitchMode(mode: MapContract.MapMode) {
        handleStopPlayback()
        _state.update { it.copy(mapMode = mode, selectedStay = null) }
        if (mode == MapContract.MapMode.PLACES) loadPlaces()
    }

    private fun loadPlaces() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaces = true) }
            getPlacesUseCase()
                .onSuccess { places ->
                    _state.update { it.copy(isLoadingPlaces = false, places = places) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingPlaces = false) }
                }
        }
    }

    /**
     * Starts the route-playback animation, advancing [MapContract.State.selectedStay]
     * through every stay in chronological order at 1.5-second intervals.
     * The playback stops automatically after the last stay.
     */
    private fun handleStartPlayback() {
        val stays = _state.value.summary?.stays ?: return
        if (stays.size < 2) return

        playbackJob?.cancel()
        _state.update { it.copy(isPlayingRoute = true, playbackIndex = 0, selectedStay = stays.first()) }

        playbackJob = viewModelScope.launch {
            stays.forEachIndexed { index, stay ->
                _state.update { it.copy(selectedStay = stay, playbackIndex = index) }
                delay(1_500L)
            }
            _state.update { it.copy(isPlayingRoute = false, playbackIndex = 0) }
        }
    }

    /** Cancels any running playback and resets playback state. */
    private fun handleStopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { it.copy(isPlayingRoute = false, playbackIndex = 0, selectedStay = null) }
    }

    private fun handlePreviousDay() {
        handleStopPlayback()
        _state.update {
            it.copy(
                selectedDate = offsetDate(it.selectedDate, -1),
                isLoading = true,
                selectedStay = null,
            )
        }
    }

    private fun handleNextDay() {
        handleStopPlayback()
        _state.update {
            it.copy(
                selectedDate = offsetDate(it.selectedDate, +1),
                isLoading = true,
                selectedStay = null,
            )
        }
    }

    private fun offsetDate(date: String, days: Int): String {
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(date) ?: Date()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return dateFormat.format(cal.time)
    }

    private fun dayBoundsMs(date: String): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(date) ?: Date()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
