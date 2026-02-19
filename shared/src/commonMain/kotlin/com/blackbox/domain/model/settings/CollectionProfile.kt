package com.blackbox.domain.model.settings

/**
 * Battery-adaptive collection profile.
 *
 * The [CollectorOrchestrator] dynamically adjusts collection
 * intervals based on the current battery level. Higher profiles
 * collect more frequently at the cost of battery drain.
 *
 * @property intervalMultiplier Factor applied to base collection intervals.
 */
enum class CollectionProfile(val intervalMultiplier: Float) {
    /** Charging — maximum collection frequency. */
    MAXIMUM(0.5f),
    /** Battery > 50% — standard collection intervals. */
    NORMAL(1.0f),
    /** Battery 20-50% — longer intervals between collections. */
    REDUCED(2.0f),
    /** Battery 10-20% — essential collectors only. */
    MINIMAL(4.0f),
    /** Battery < 10% — location only at minimum frequency. */
    CRITICAL(8.0f),
}
