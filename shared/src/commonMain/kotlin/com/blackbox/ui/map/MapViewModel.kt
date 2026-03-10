package com.blackbox.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.usecase.map.GetDayLocationSummaryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Manages day-based navigation and a reactive location summary stream.
 * When the selected date changes, the [GetDayLocationSummaryUseCase.observe]
 * flow is restarted for the new day so the map updates automatically as the
 * background service writes new location fixes.
 *
 * @property getDayLocationSummaryUseCase Use case that clusters GPS fixes into stays.
 */
class MapViewModel(
    private val getDayLocationSummaryUseCase: GetDayLocationSummaryUseCase,
    private val recordRepository: RecordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MapContract.State())

    /** Observable UI state for the Map screen. */
    val state: StateFlow<MapContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MapContract.Event>()

    /** One-time events for the Map screen. */
    val events: SharedFlow<MapContract.Event> = _events.asSharedFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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

    private fun handlePreviousDay() {
        _state.update {
            it.copy(
                selectedDate = offsetDate(it.selectedDate, -1),
                isLoading = true,
                selectedStay = null,
            )
        }
    }

    private fun handleNextDay() {
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
}
