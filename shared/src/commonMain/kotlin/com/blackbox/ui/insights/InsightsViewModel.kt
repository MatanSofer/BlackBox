package com.blackbox.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.domain.usecase.insight.GenerateInsightObservationsUseCase
import com.blackbox.domain.usecase.insight.GetInsightsBriefUseCase
import com.blackbox.domain.usecase.insight.InsightsBrief
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ViewModel for the Insights screen.
 *
 * Loading happens in two sequential phases:
 * 1. [GetInsightsBriefUseCase] — fast, local DB query. Populates all data cards immediately.
 * 2. [GenerateInsightObservationsUseCase] — LLM call. Shows a loading indicator on the
 *    observations card, then fills it in. Falls back to rule-based observations on failure.
 *
 * On [InsightsContract.Action.ScreenResumed], data is refreshed only if the existing
 * snapshot is older than [STALE_THRESHOLD_MS] (5 minutes), avoiding unnecessary reloads
 * when quickly navigating back from a short sub-screen visit.
 *
 * @property getInsightsBriefUseCase Aggregates step, screen, app, and location data.
 * @property generateInsightObservationsUseCase Produces LLM or fallback observations.
 */
class InsightsViewModel(
    private val getInsightsBriefUseCase: GetInsightsBriefUseCase,
    private val generateInsightObservationsUseCase: GenerateInsightObservationsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsContract.State())

    /** Observable UI state for the Insights screen. */
    val state: StateFlow<InsightsContract.State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InsightsContract.Event>()

    /** One-time events for the Insights screen. */
    val events: SharedFlow<InsightsContract.Event> = _events.asSharedFlow()

    /** Epoch ms of the last completed data load. Zero means never loaded. */
    private var lastLoadedAtMs = 0L

    init {
        loadData()
    }

    /**
     * Single entry point for all UI actions.
     */
    fun onAction(action: InsightsContract.Action) {
        when (action) {
            is InsightsContract.Action.Refresh -> loadData()
            is InsightsContract.Action.RetryObservations ->
                _state.value.brief?.let { loadObservations(it) }
            is InsightsContract.Action.ScreenResumed -> onScreenResumed()
            is InsightsContract.Action.RankingPeriodChanged -> {
                _state.update { it.copy(rankingPeriod = action.period) }
                loadData(rankingDays = action.period.days)
            }
        }
    }

    /**
     * Refreshes data only when the current snapshot is stale (> [STALE_THRESHOLD_MS] old).
     * This prevents a visible reload flash on quick back-navigations.
     */
    private fun onScreenResumed() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (nowMs - lastLoadedAtMs > STALE_THRESHOLD_MS) {
            loadData()
        }
    }

    private fun loadData(rankingDays: Int = _state.value.rankingPeriod.days) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingData = true, error = null, observations = emptyList()) }
            getInsightsBriefUseCase(rankingDays)
                .onSuccess { brief ->
                    lastLoadedAtMs = Clock.System.now().toEpochMilliseconds()
                    _state.update { it.copy(isLoadingData = false, brief = brief) }
                    loadObservations(brief)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoadingData = false, error = error.message) }
                    _events.emit(InsightsContract.Event.ShowSnackbar("Failed to load insights"))
                }
        }
    }

    private fun loadObservations(brief: InsightsBrief) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingObservations = true) }
            generateInsightObservationsUseCase(brief)
                .onSuccess { observations ->
                    _state.update { it.copy(isLoadingObservations = false, observations = observations) }
                }
                .onFailure {
                    val fallback = generateInsightObservationsUseCase.generateLocalObservations(brief)
                    _state.update { it.copy(isLoadingObservations = false, observations = fallback) }
                }
        }
    }

    companion object {
        /** Refresh insights data if it is older than this threshold on screen resume. */
        private const val STALE_THRESHOLD_MS = 5 * 60_000L
    }
}
