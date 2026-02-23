package com.blackbox.platform

/**
 * Platform-agnostic time provider.
 *
 * Abstracts [System.currentTimeMillis] behind an expect/actual
 * pattern so that domain logic and use cases can be tested with
 * a fixed clock instead of relying on wall time.
 */
expect class DateTimeProvider() {

    /** Returns the current time in Unix epoch milliseconds. */
    fun currentTimeMillis(): Long

    /** Returns today's date in ISO format (yyyy-MM-dd). */
    fun todayIsoDate(): String
}
