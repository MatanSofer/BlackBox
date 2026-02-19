package com.blackbox.domain.model.query

/**
 * A time range extracted from a natural language query.
 *
 * Represents a contiguous time window used to scope database queries.
 * Both boundaries are inclusive.
 *
 * @property startEpochMs Start of the time range in Unix epoch milliseconds.
 * @property endEpochMs End of the time range in Unix epoch milliseconds.
 */
data class TimeRange(
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    /** Duration of this time range in milliseconds. */
    val durationMs: Long get() = endEpochMs - startEpochMs
}
