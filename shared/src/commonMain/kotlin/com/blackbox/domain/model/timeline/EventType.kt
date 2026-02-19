package com.blackbox.domain.model.timeline

/**
 * Types of significant events derived from raw data analysis.
 *
 * These events are detected by analyzing patterns across multiple
 * data sources and represent meaningful moments in the user's day.
 */
enum class EventType {
    /** User arrived at a known place. */
    ARRIVED,
    /** User departed from a known place. */
    DEPARTED,
    /** User began driving (detected via activity + speed). */
    STARTED_DRIVING,
    /** User stopped driving. */
    STOPPED_DRIVING,
    /** User began a walking session. */
    STARTED_WALKING,
    /** Estimated start of sleep period. */
    SLEEP_START,
    /** Estimated end of sleep period. */
    SLEEP_END,
    /** User picked up and unlocked the phone. */
    PHONE_PICKUP,
    /** User entered a transit mode (bus, train, etc.). */
    ENTERED_TRANSIT,
    /** User exited transit. */
    EXITED_TRANSIT,
    /** Significant location change detected. */
    SIGNIFICANT_MOVEMENT,
    /** User has been stationary for an unusually long period. */
    LONG_STATIONARY,
    /** Unusual activity pattern detected (e.g., phone used during sleep hours). */
    ANOMALOUS_ACTIVITY,
}
