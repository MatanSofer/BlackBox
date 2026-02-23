package com.blackbox.ui.insights

import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount

/**
 * MVI contract for the Insights screen.
 * Defines all possible states, user actions, and one-time events.
 */
object InsightsContract {

    /**
     * Single immutable UI state for the Insights screen.
     *
     * @property isLoading Whether insights data is being loaded.
     * @property stepTrend Daily step counts for the trend chart.
     * @property screenTimeTrend Daily screen time for the trend chart.
     * @property averageSteps Average daily steps in the period.
     * @property averageScreenMinutes Average daily screen time in minutes.
     * @property averageWakeTimeMs Average wake time as epoch ms (time of day).
     * @property averageSleepTimeMs Average sleep time as epoch ms (time of day).
     * @property selectedPeriodDays Number of days in the selected analysis period.
     * @property error Error message to display, if any.
     */
    data class State(
        val isLoading: Boolean = false,
        val stepTrend: List<DailyStepCount> = emptyList(),
        val screenTimeTrend: List<DailyScreenTime> = emptyList(),
        val averageSteps: Int = 0,
        val averageScreenMinutes: Int = 0,
        val averageWakeTimeMs: Long? = null,
        val averageSleepTimeMs: Long? = null,
        val selectedPeriodDays: Int = 7,
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User selected a different time period for analysis. */
        data class PeriodSelected(val days: Int) : Action
        /** User pulled to refresh. */
        data object Refresh : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Show a snackbar with a message. */
        data class ShowSnackbar(val message: String) : Event
    }
}
