package com.blackbox.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.usecase.timeline.GetCollectorGroupsUseCase
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
 * ViewModel for the Timeline screen.
 *
 * Manages day-based navigation and exposes a live-updating stream of
 * collector groups for the selected date.
 *
 * **Live updates:** Combines reactive flows from [RecordRepository],
 * [LocationRepository], and [SettingsRepository] so the UI re-renders
 * the moment the background service writes a new record or the user
 * changes a setting — no polling, no app restart required.
 *
 * @property getCollectorGroupsUseCase Use case for building grouped timeline data.
 * @property settingsRepository Repository for reading display preferences.
 */
class TimelineViewModel(
    private val getCollectorGroupsUseCase: GetCollectorGroupsUseCase,
    private val settingsRepository: SettingsRepository,
    private val recordRepository: RecordRepository,
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
        _state.update { it.copy(selectedDate = today, isLoading = true) }
        observeTimeline()
        loadAvailableDates()
    }

    private fun loadAvailableDates() {
        viewModelScope.launch {
            runCatching { recordRepository.getDatesWithData() }
                .onSuccess { dates -> _state.update { it.copy(availableDates = dates.toSet()) } }
        }
    }

    /**
     * Starts two coroutines that keep the Timeline screen live:
     *
     * 1. **Data flow** — combines [RecordRepository.observeRecordsInRange],
     *    [LocationRepository.observeLocations], and [SettingsRepository.observeRawDataViewEnabled]
     *    into a single stream. The moment the background service writes a new record,
     *    or the user flips the "show all collectors" toggle in Settings, the UI
     *    re-renders automatically — no polling, no app restart required.
     *    Restarts automatically when the selected date changes.
     *
     * 2. **Settings sync** — keeps [TimelineContract.State.showAllCollectors] up to date
     *    so the UI can reflect the toggle state independently of the data load.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTimeline() {
        // ── 1. Reactive data flow ─────────────────────────────────────────────
        viewModelScope.launch {
            _state
                .map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (start, end) = dayBoundsMs(date)
                    getCollectorGroupsUseCase.observe(
                        startTime = start,
                        endTime = end,
                        showAllFlow = settingsRepository.observeRawDataViewEnabled(),
                    )
                }
                .collect { result ->
                    result.onSuccess { freshGroups ->
                        // Preserve expanded/collapsed state of each card across updates.
                        val expandedTypes = _state.value.groups
                            .filter { it.isExpanded }
                            .map { it.collectorType }
                            .toSet()
                        val merged = freshGroups.map { group ->
                            group.copy(isExpanded = group.collectorType in expandedTypes)
                        }
                        _state.update { it.copy(isLoading = false, groups = merged, error = null) }
                    }
                    result.onFailure { error ->
                        _state.update {
                            it.copy(isLoading = false, error = error.message ?: "Failed to load timeline")
                        }
                    }
                }
        }

        // ── 2. Keep showAllCollectors state field in sync for the UI ──────────
        viewModelScope.launch {
            settingsRepository.observeRawDataViewEnabled()
                .distinctUntilChanged()
                .collect { showAll ->
                    _state.update { it.copy(showAllCollectors = showAll) }
                }
        }
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: TimelineContract.Action) {
        when (action) {
            is TimelineContract.Action.DateSelected -> handleDateSelected(action.date)
            is TimelineContract.Action.PreviousDay -> handlePreviousDay()
            is TimelineContract.Action.NextDay -> handleNextDay()
            is TimelineContract.Action.Refresh -> loadTimeline(
                _state.value.selectedDate, _state.value.showAllCollectors,
            )
            is TimelineContract.Action.GroupToggled -> handleGroupToggled(action.collectorType)
            is TimelineContract.Action.ShowDatePicker -> _state.update { it.copy(showDatePicker = true) }
            is TimelineContract.Action.DismissDatePicker -> _state.update { it.copy(showDatePicker = false) }
        }
    }

    private fun handleDateSelected(date: String) {
        // Updating selectedDate causes the flatMapLatest in observeTimeline() to
        // cancel the current observation and restart it for the new date.
        _state.update { it.copy(selectedDate = date, isLoading = true, showDatePicker = false) }
    }

    private fun handlePreviousDay() {
        val newDate = offsetDate(_state.value.selectedDate, -1)
        _state.update { it.copy(selectedDate = newDate, isLoading = true) }
    }

    private fun handleNextDay() {
        val newDate = offsetDate(_state.value.selectedDate, 1)
        _state.update { it.copy(selectedDate = newDate, isLoading = true) }
    }

    private fun handleGroupToggled(collectorType: CollectorType?) {
        _state.update { state ->
            val updated = state.groups.map { group ->
                if (group.collectorType == collectorType) {
                    group.copy(isExpanded = !group.isExpanded)
                } else {
                    group
                }
            }
            state.copy(groups = updated)
        }
    }

    private fun loadTimeline(date: String, showAll: Boolean) {
        viewModelScope.launch {
            // Only show the loading spinner on the initial load (no groups yet),
            // so periodic background refreshes don't flash the spinner.
            val initialLoad = _state.value.groups.isEmpty()
            if (initialLoad) _state.update { it.copy(isLoading = true, error = null) }

            val (start, end) = dayBoundsMs(date)
            getCollectorGroupsUseCase(start, end, showAll)
                .onSuccess { freshGroups ->
                    // Preserve the expanded state of each group across refreshes.
                    val expandedTypes = _state.value.groups
                        .filter { it.isExpanded }
                        .map { it.collectorType }
                        .toSet()
                    val merged = freshGroups.map { group ->
                        group.copy(isExpanded = group.collectorType in expandedTypes)
                    }
                    _state.update { it.copy(isLoading = false, groups = merged, error = null) }
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

    companion object {
        private const val TAG = "TimelineViewModel"
    }
}
