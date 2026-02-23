package com.blackbox.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android implementation of [DateTimeProvider].
 *
 * Delegates to [System.currentTimeMillis] and [SimpleDateFormat]
 * for real device time.
 */
actual class DateTimeProvider actual constructor() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Returns the current time in Unix epoch milliseconds. */
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    /** Returns today's date in ISO format (yyyy-MM-dd). */
    actual fun todayIsoDate(): String = dateFormat.format(Date())
}
