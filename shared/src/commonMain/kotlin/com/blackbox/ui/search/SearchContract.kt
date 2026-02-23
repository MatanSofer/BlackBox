package com.blackbox.ui.search

import com.blackbox.domain.model.query.QueryResult

/**
 * MVI contract for the Search screen.
 * Defines all possible states, user actions, and one-time events.
 */
object SearchContract {

    /**
     * Single immutable UI state for the Search screen.
     *
     * @property query Current text in the search input.
     * @property isLoading Whether a query is being processed.
     * @property result The most recent query result, if any.
     * @property recentQueries List of recent query strings for quick access.
     * @property suggestedFollowUps Follow-up queries suggested by the engine.
     * @property error Error message to display, if any.
     */
    data class State(
        val query: String = "",
        val isLoading: Boolean = false,
        val result: QueryResult? = null,
        val recentQueries: List<String> = emptyList(),
        val suggestedFollowUps: List<String> = emptyList(),
        val error: String? = null,
    )

    /**
     * Actions dispatched from the UI to the ViewModel.
     * Every user interaction maps to exactly one Action.
     */
    sealed interface Action {
        /** User changed the query text. */
        data class QueryChanged(val text: String) : Action
        /** User submitted the current query. */
        data object SubmitQuery : Action
        /** User tapped a recent query chip. */
        data class RecentQueryClicked(val query: String) : Action
        /** User tapped a suggested follow-up query. */
        data class SuggestionClicked(val query: String) : Action
        /** User cleared the current results. */
        data object ClearResults : Action
        /** User dismissed the error. */
        data object DismissError : Action
    }

    /**
     * One-time events from the ViewModel to the UI.
     * Used for effects that should not persist in state.
     */
    sealed interface Event {
        /** Show a snackbar with a message. */
        data class ShowSnackbar(val message: String) : Event
    }
}
