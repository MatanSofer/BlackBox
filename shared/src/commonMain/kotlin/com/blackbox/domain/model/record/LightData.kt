package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Ambient light level data captured by the light collector.
 *
 * Measures environmental brightness to help determine whether
 * the user is indoors, outdoors, or in darkness (sleeping).
 * Disabled by default.
 *
 * @property lux Light intensity in lux.
 * @property classification Categorized light level.
 */
@Serializable
data class LightData(
    val lux: Float,
    val classification: LightClassification,
)

/**
 * Ambient light level classification based on lux thresholds.
 */
enum class LightClassification {
    /** Below 10 lux. */
    DARK,
    /** 10-50 lux. */
    DIM,
    /** 50-500 lux. */
    INDOOR,
    /** 500-10,000 lux. */
    OUTDOOR_SHADE,
    /** Above 10,000 lux. */
    DIRECT_SUNLIGHT,
}
