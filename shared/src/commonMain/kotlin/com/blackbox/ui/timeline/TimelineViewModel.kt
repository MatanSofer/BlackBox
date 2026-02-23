package com.blackbox.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.timeline.GetTimelineUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Timeline screen.
 *
 * Manages day-based navigation and loads timeline entries
 * for the selected date via the domain layer.
 *
 * @property getTimelineUseCase Use case for building timeline entries.
 */
class TimelineViewModel(
    private val getTimelineUseCase: GetTimelineUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineContract.State())

    /** Observable UI state for the Timeline screen. */
    val state: StateFlow<TimelineContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimelineContract.Event>()

    /** One-time events for the Timeline screen. */
    val events: SharedFlow<TimelineContract.Event> = _events.asSharedFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        val today = dateFormat.format(Date())
        _state.update { it.copy(selectedDate = today) }
        loadTimeline(today)
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: TimelineContract.Action) {
        when (action) {
            is TimelineContract.Action.DateSelected -> handleDateSelected(action.date)
            is TimelineContract.Action.PreviousDay -> handlePreviousDay()
            is TimelineContract.Action.NextDay -> handleNextDay()
            is TimelineContract.Action.EntryClicked -> { /* Detail navigation — future */ }
            is TimelineContract.Action.Refresh -> loadTimeline(_state.value.selectedDate)
        }
    }

    private fun handleDateSelected(date: String) {
        _state.update { it.copy(selectedDate = date) }
        loadTimeline(date)
    }

    private fun handlePreviousDay() {
        val newDate = offsetDate(_state.value.selectedDate, -1)
        _state.update { it.copy(selectedDate = newDate) }
        loadTimeline(newDate)
    }

    private fun handleNextDay() {
        val newDate = offsetDate(_state.value.selectedDate, 1)
        _state.update { it.copy(selectedDate = newDate) }
        loadTimeline(newDate)
    }

    private fun loadTimeline(date: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val (start, end) = dayBoundsMs(date)
            getTimelineUseCase(start, end)
                .onSuccess { entries ->
                    _state.update { it.copy(isLoading = false, entries = entries) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load timeline")
                    }
                }
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
        val end = cal.timeInMillis
        return start to end
    }
}
