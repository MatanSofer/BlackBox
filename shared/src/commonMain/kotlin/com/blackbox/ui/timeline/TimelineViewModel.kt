package com.blackbox.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.usecase.timeline.GetCollectorGroupsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Timeline screen.
 *
 * Manages day-based navigation and loads collector groups for the selected
 * date via the domain layer.
 *
 * **Live updates:** A coroutine runs continuously while the ViewModel is alive,
 * refreshing the timeline every [LIVE_REFRESH_INTERVAL_MS]. This ensures new
 * collector records appear without the user needing to navigate away and back.
 *
 * **Immediate toggle response:** [SettingsRepository.observeRawDataViewEnabled] is
 * collected as a Flow. Any change made in the Settings screen propagates here
 * immediately — no app restart required.
 *
 * @property getCollectorGroupsUseCase Use case for building grouped timeline data.
 * @property settingsRepository Repository for reading display preferences.
 */
class TimelineViewModel(
    private val getCollectorGroupsUseCase: GetCollectorGroupsUseCase,
    private val settingsRepository: SettingsRepository,
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

        // Observe the raw data toggle. When the user changes it in Settings and then
        // navigates back to Timeline, the flow emits immediately and we reload.
        viewModelScope.launch {
            settingsRepository.observeRawDataViewEnabled()
                .distinctUntilChanged()
                .collect { showAll ->
                    _state.update { it.copy(showAllCollectors = showAll) }
                    loadTimeline(_state.value.selectedDate, showAll)
                }
        }

        // Periodic live refresh — emits new collector records as they arrive from the
        // background service without any user interaction.
        viewModelScope.launch {
            while (true) {
                delay(LIVE_REFRESH_INTERVAL_MS)
                if (!_state.value.isLoading) {
                    loadTimeline(_state.value.selectedDate, _state.value.showAllCollectors)
                }
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
        }
    }

    private fun handleDateSelected(date: String) {
        _state.update { it.copy(selectedDate = date) }
        loadTimeline(date, _state.value.showAllCollectors)
    }

    private fun handlePreviousDay() {
        val newDate = offsetDate(_state.value.selectedDate, -1)
        _state.update { it.copy(selectedDate = newDate) }
        loadTimeline(newDate, _state.value.showAllCollectors)
    }

    private fun handleNextDay() {
        val newDate = offsetDate(_state.value.selectedDate, 1)
        _state.update { it.copy(selectedDate = newDate) }
        loadTimeline(newDate, _state.value.showAllCollectors)
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
        /** How often the timeline auto-refreshes while the screen is active. */
        private const val LIVE_REFRESH_INTERVAL_MS = 30_000L
    }
}
