package com.blackbox.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.query.GetRecentQueriesUseCase
import com.blackbox.domain.usecase.query.ProcessQueryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Search screen.
 *
 * Processes natural language queries through the domain layer
 * and manages search state, recent queries, and results.
 *
 * @property processQueryUseCase Use case for executing NLP queries.
 * @property getRecentQueriesUseCase Use case for fetching query history.
 */
class SearchViewModel(
    private val processQueryUseCase: ProcessQueryUseCase,
    private val getRecentQueriesUseCase: GetRecentQueriesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchContract.State())

    /** Observable UI state for the Search screen. */
    val state: StateFlow<SearchContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SearchContract.Event>()

    /** One-time events for the Search screen. */
    val events: SharedFlow<SearchContract.Event> = _events.asSharedFlow()

    init {
        loadRecentQueries()
    }

    /**
     * Single entry point for all UI actions.
     * Maps each action to the appropriate handler.
     */
    fun onAction(action: SearchContract.Action) {
        when (action) {
            is SearchContract.Action.QueryChanged -> handleQueryChanged(action.text)
            is SearchContract.Action.SubmitQuery -> handleSubmitQuery()
            is SearchContract.Action.RecentQueryClicked -> handleRecentQuery(action.query)
            is SearchContract.Action.SuggestionClicked -> handleSuggestion(action.query)
            is SearchContract.Action.ClearResults -> handleClearResults()
            is SearchContract.Action.DismissError -> handleDismissError()
        }
    }

    private fun handleQueryChanged(text: String) {
        _state.update { it.copy(query = text) }
    }

    private fun handleSubmitQuery() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            processQueryUseCase(query)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            result = result,
                            suggestedFollowUps = result.suggestedFollowUps,
                        )
                    }
                    loadRecentQueries()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Query failed",
                        )
                    }
                }
        }
    }

    private fun handleRecentQuery(query: String) {
        _state.update { it.copy(query = query) }
        handleSubmitQuery()
    }

    private fun handleSuggestion(query: String) {
        _state.update { it.copy(query = query) }
        handleSubmitQuery()
    }

    private fun handleClearResults() {
        _state.update {
            it.copy(
                query = "",
                result = null,
                suggestedFollowUps = emptyList(),
                error = null,
            )
        }
    }

    private fun handleDismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun loadRecentQueries() {
        viewModelScope.launch {
            getRecentQueriesUseCase(limit = 10)
                .onSuccess { queries ->
                    _state.update { it.copy(recentQueries = queries.map { q -> q.queryText }) }
                }
        }
    }
}
