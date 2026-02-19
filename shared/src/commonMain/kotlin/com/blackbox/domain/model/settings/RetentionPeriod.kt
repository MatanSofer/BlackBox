package com.blackbox.domain.model.settings

/**
 * Data retention period options.
 *
 * Controls how long raw records are kept before the
 * [CleanupWorker] deletes them. Daily summaries and derived
 * events are kept indefinitely regardless of this setting.
 *
 * @property days Number of days to retain raw records. -1 means unlimited.
 */
enum class RetentionPeriod(val days: Int) {
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    /** Default retention period. */
    ONE_YEAR(365),
    TWO_YEARS(730),
    UNLIMITED(-1),
}
