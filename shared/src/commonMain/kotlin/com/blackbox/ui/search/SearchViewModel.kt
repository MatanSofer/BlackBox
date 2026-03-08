package com.blackbox.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.query.GetRecentQueriesUseCase
import com.blackbox.domain.usecase.query.ProcessQueryUseCase
import com.blackbox.domain.usecase.query.ProcessQueryWithAiUseCase
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
 * Implements a two-phase query flow:
 * 1. **Phase 1 (fast):** The local [ProcessQueryUseCase] runs the rule-based engine
 *    and returns in ~100 ms. The result is shown immediately.
 * 2. **Phase 2 (enriched):** [ProcessQueryWithAiUseCase] sends the already-fetched
 *    records + query to the AI model. When the response arrives, it replaces the
 *    answer area while the local result remains visible as a secondary reference.
 *    If the AI call fails (no key, network error, rate limit), a subtle fallback
 *    banner is shown instead.
 *
 * @property processQueryUseCase Local rule-based query engine.
 * @property getRecentQueriesUseCase Fetches query history for the recents list.
 * @property processQueryWithAiUseCase AI enrichment use case.
 */
class SearchViewModel(
    private val processQueryUseCase: ProcessQueryUseCase,
    private val getRecentQueriesUseCase: GetRecentQueriesUseCase,
    private val processQueryWithAiUseCase: ProcessQueryWithAiUseCase,
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
            // Reset all result state before starting.
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    result = null,
                    aiResponse = null,
                    isAiMode = false,
                    isAiLoading = false,
                    aiFallbackReason = null,
                )
            }

            // ── Phase 1: Fast local engine (~100 ms) ──────────────────────────
            processQueryUseCase(query)
                .onSuccess { localResult ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            result = localResult,
                            suggestedFollowUps = localResult.suggestedFollowUps,
                            isAiLoading = true, // signal Phase 2 starting
                        )
                    }
                    loadRecentQueries()

                    // ── Phase 2: AI enrichment (2-5 s, non-blocking) ──────────
                    processQueryWithAiUseCase(query, localResult)
                        .onSuccess { aiText ->
                            _state.update {
                                it.copy(isAiLoading = false, aiResponse = aiText, isAiMode = true)
                            }
                        }
                        .onFailure { aiError ->
                            _state.update {
                                it.copy(
                                    isAiLoading = false,
                                    isAiMode = false,
                                    aiFallbackReason = aiError.message ?: "AI unavailable",
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Query failed")
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
                aiResponse = null,
                isAiMode = false,
                isAiLoading = false,
                aiFallbackReason = null,
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
