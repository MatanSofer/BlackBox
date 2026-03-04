package com.blackbox.android.collector

import android.content.Context
import android.media.AudioManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.AudioOutputType
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.MediaPlaybackData
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Detects audio playback state changes and the current output device.
 *
 * Uses [AudioManager.isMusicActive] to detect whether audio is playing.
 * A record is only emitted when the playing state transitions, keeping
 * storage minimal (no polling noise during continuous playback or silence).
 *
 * No special permissions are required — [AudioManager] is freely accessible.
 *
 * @property context Android context for accessing [AudioManager].
 * @property logger Logger for lifecycle and error events.
 */
class MediaPlaybackCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = POLL_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.MEDIA_PLAYBACK

    private var audioManager: AudioManager? = null
    private var sessionId: String = ""
    private var lastIsPlaying: Boolean? = null

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting media playback collector")
        sessionId = UUID.randomUUID().toString()
        lastIsPlaying = null
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            logger.w(TAG, "AudioManager not available on this device")
        }
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping media playback collector")
        audioManager = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val am = audioManager ?: return emptyList()

        val isPlaying = am.isMusicActive
        if (isPlaying == lastIsPlaying) return emptyList()

        lastIsPlaying = isPlaying

        val now = System.currentTimeMillis()
        val volumePercent = computeVolumePercent(am)
        val outputType = detectOutputType(am)

        val data = MediaPlaybackData(
            isPlaying = isPlaying,
            volumePercent = volumePercent,
            outputType = outputType,
        )

        logger.d(TAG, "Playback state → ${if (isPlaying) "PLAYING" else "STOPPED"} vol=$volumePercent% $outputType")

        return listOf(
            CollectedRecord(
                timestamp = now,
                collectorType = CollectorType.MEDIA_PLAYBACK,
                data = RecordData.MediaPlayback(data),
                accuracyScore = 1.0f,
                sessionId = sessionId,
                createdAt = now,
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun detectOutputType(am: AudioManager): AudioOutputType = when {
        am.isBluetoothA2dpOn -> AudioOutputType.BLUETOOTH
        am.isWiredHeadsetOn -> AudioOutputType.WIRED_HEADSET
        else -> AudioOutputType.SPEAKER
    }

    private fun computeVolumePercent(am: AudioManager): Int {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100) / max
    }

    companion object {
        private const val TAG = "MediaPlaybackCollector"

        /** Poll every 30 seconds. */
        private const val POLL_INTERVAL_MS = 30L * 1_000
    }
}
