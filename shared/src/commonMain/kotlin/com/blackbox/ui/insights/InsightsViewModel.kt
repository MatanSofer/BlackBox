package com.blackbox.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.insight.GetInsightsUseCase
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
import java.util.Locale

/**
 * ViewModel for the Insights screen.
 *
 * Loads aggregated trend data for a configurable time period
 * and computes averages for display.
 *
 * @property getInsightsUseCase Use case for fetching insight data.
 */
class InsightsViewModel(
    private val getInsightsUseCase: GetInsightsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsContract.State())

    /** Observable UI state for the Insights screen. */
    val state: StateFlow<InsightsContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InsightsContract.Event>()

    /** One-time events for the Insights screen. */
    val events: SharedFlow<InsightsContract.Event> = _events.asSharedFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadInsights(_state.value.selectedPeriodDays)
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: InsightsContract.Action) {
        when (action) {
            is InsightsContract.Action.PeriodSelected -> {
                _state.update { it.copy(selectedPeriodDays = action.days) }
                loadInsights(action.days)
            }
            is InsightsContract.Action.Refresh -> loadInsights(_state.value.selectedPeriodDays)
        }
    }

    private fun loadInsights(days: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val cal = Calendar.getInstance()
            val endDate = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -days)
            val startDate = dateFormat.format(cal.time)

            getInsightsUseCase(startDate, endDate)
                .onSuccess { data ->
                    val avgSteps = if (data.stepTrend.isNotEmpty()) {
                        data.stepTrend.map { it.steps }.average().toInt()
                    } else 0

                    val avgScreen = if (data.screenTimeTrend.isNotEmpty()) {
                        data.screenTimeTrend.map { it.totalMinutes }.average().toInt()
                    } else 0

                    _state.update {
                        it.copy(
                            isLoading = false,
                            stepTrend = data.stepTrend,
                            screenTimeTrend = data.screenTimeTrend,
                            averageSteps = avgSteps,
                            averageScreenMinutes = avgScreen,
                            averageWakeTimeMs = data.averageWakeTimeMs,
                            averageSleepTimeMs = data.averageSleepTimeMs,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Failed to load insights")
                    }
                }
        }
    }
}
