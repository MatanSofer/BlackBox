package com.blackbox.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of [DateTimeProvider].
 *
 * Uses Foundation [NSDate] for current time and [NSDateFormatter]
 * for ISO date formatting.
 */
actual class DateTimeProvider actual constructor() {

    private val dateFormatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
    }

    /** Returns the current time in Unix epoch milliseconds. */
    actual fun currentTimeMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()

    /** Returns today's date in ISO format (yyyy-MM-dd). */
    actual fun todayIsoDate(): String =
        dateFormatter.stringFromDate(NSDate())
}
