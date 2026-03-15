package com.blackbox.ui.insights

import com.blackbox.domain.usecase.insight.InsightsBrief

/**
 * MVI contract for the redesigned Insights screen ("Intel Briefing").
 *
 * The screen loads in two phases:
 * 1. Data phase ([isLoadingData]) — fetches steps, screen time, apps, places.
 * 2. Observations phase ([isLoadingObservations]) — calls the LLM for insights.
 *
 * This lets the user see concrete numbers immediately while observations
 * stream in asynchronously.
 */
object InsightsContract {

    /**
     * Single immutable UI state for the Insights screen.
     *
     * @property isLoadingData Whether the main data brief is being fetched.
     * @property isLoadingObservations Whether the LLM is generating observations.
     * @property brief The aggregated weekly data snapshot. Null while loading.
     * @property observations AI-generated (or rule-based fallback) insight sentences.
     * @property error Error message shown when the data fetch fails entirely.
     */
    data class State(
        val isLoadingData: Boolean = true,
        val isLoadingObservations: Boolean = false,
        val brief: InsightsBrief? = null,
        val observations: List<String> = emptyList(),
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     */
    sealed interface Action {
        /** User pulled to refresh all data and observations. */
        data object Refresh : Action
        /** User tapped retry on the observations card after an LLM failure. */
        data object RetryObservations : Action
        /** Screen came back into focus (e.g. navigated back from another screen). */
        data object ScreenResumed : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     */
    sealed interface Event {
        /** Show a transient snackbar message. */
        data class ShowSnackbar(val message: String) : Event
    }
}
