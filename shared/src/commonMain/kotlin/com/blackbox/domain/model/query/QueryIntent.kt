package com.blackbox.domain.model.query

/**
 * Classification of a user's query intent.
 *
 * Determined by the [IntentClassifier] based on keyword analysis.
 * Each intent maps to a different query execution plan and
 * response template.
 */
enum class QueryIntent {
    /** "Where was I?" — location-based queries. */
    LOCATION_QUERY,
    /** "What was I doing?" — activity and usage queries. */
    ACTIVITY_QUERY,
    /** "When did I...?" — time-focused queries. */
    TEMPORAL_QUERY,
    /** "Do I usually...?" — pattern and trend queries. */
    PATTERN_QUERY,
    /** "Prove I was..." — evidence and corroboration queries. */
    PROOF_QUERY,
    /** "Summarize my day" — overview and summary queries. */
    SUMMARY_QUERY,
    /** "How long was I...?" — duration calculation queries. */
    DURATION_QUERY,
    /** "How many times...?" — counting and frequency queries. */
    COUNT_QUERY,
}
