package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Snapshot of the device's media playback state at the time of collection.
 *
 * Only state-change events are persisted — records are only emitted when
 * [isPlaying] transitions from its previous value, keeping storage minimal.
 *
 * @property isPlaying True if audio is actively playing (via [AudioManager.isMusicActive]).
 * @property volumePercent Current music stream volume, normalised to 0–100.
 * @property outputType The audio output device in use.
 */
@Serializable
data class MediaPlaybackData(
    val isPlaying: Boolean,
    val volumePercent: Int,
    val outputType: AudioOutputType,
)

/**
 * The audio output device route currently active on the device.
 */
enum class AudioOutputType {
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    UNKNOWN,
}
