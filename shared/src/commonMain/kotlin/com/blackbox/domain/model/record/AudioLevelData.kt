package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Ambient audio level data captured by the audio level collector.
 *
 * Measures environmental noise level in decibels without recording
 * any audio content. Only captures volume level and frequency profile.
 * Disabled by default due to microphone usage sensitivity.
 *
 * @property dbLevel Ambient noise level in decibels.
 * @property classification Categorized noise level.
 * @property lowHz Normalized energy in low frequency band (0.0-1.0).
 * @property midHz Normalized energy in mid frequency band (0.0-1.0).
 * @property highHz Normalized energy in high frequency band (0.0-1.0).
 * @property sampleDurationMs Duration of the audio sample in milliseconds.
 */
@Serializable
data class AudioLevelData(
    val dbLevel: Float,
    val classification: NoiseClassification,
    val lowHz: Float? = null,
    val midHz: Float? = null,
    val highHz: Float? = null,
    val sampleDurationMs: Int = 3000,
)

/**
 * Ambient noise level classification based on decibel thresholds.
 */
enum class NoiseClassification {
    /** Below 30 dB. */
    SILENT,
    /** 30-45 dB. */
    QUIET,
    /** 45-65 dB. */
    CONVERSATION,
    /** 65-80 dB. */
    LOUD,
    /** Above 80 dB. */
    VERY_LOUD,
}
