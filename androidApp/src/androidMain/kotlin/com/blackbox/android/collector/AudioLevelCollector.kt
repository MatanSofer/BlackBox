package com.blackbox.android.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.AudioLevelData
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.NoiseClassification
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Measures ambient noise level without recording audio content.
 *
 * Polling-based collector that takes brief audio samples to compute
 * the ambient decibel level. Only captures volume level — never stores
 * or transmits raw audio data. Disabled by default due to microphone
 * usage sensitivity.
 *
 * @property context Android context for checking permissions.
 * @property logger Logger for lifecycle and error events.
 */
class AudioLevelCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.AUDIO_LEVEL

    private var sessionId: String = ""

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting audio level collector")
        sessionId = UUID.randomUUID().toString()
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping audio level collector")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logger.w(TAG, "RECORD_AUDIO permission not granted, skipping")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val dbLevel = measureAmbientDb() ?: return emptyList()
        val classification = classifyNoise(dbLevel)

        val audioData = AudioLevelData(
            dbLevel = dbLevel,
            classification = classification,
            sampleDurationMs = SAMPLE_DURATION_MS,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.AUDIO_LEVEL,
            data = RecordData.AudioLevel(audioData),
            accuracyScore = 0.8f,
            sessionId = sessionId,
            createdAt = now,
        )

        logger.d(TAG, "Audio level collected: ${dbLevel}dB ($classification)")
        return listOf(record)
    }

    /**
     * Takes a brief audio sample and computes the RMS decibel level.
     *
     * @return Ambient dB level, or null if recording failed.
     */
    private fun measureAmbientDb(): Float? {
        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            logger.w(TAG, "Invalid AudioRecord buffer size: $bufferSize")
            return null
        }

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                logger.w(TAG, "AudioRecord failed to initialize")
                return null
            }

            audioRecord.startRecording()

            val samplesToRead = (sampleRate * SAMPLE_DURATION_MS / 1000)
            val buffer = ShortArray(samplesToRead)
            val readCount = audioRecord.read(buffer, 0, samplesToRead)

            audioRecord.stop()

            if (readCount <= 0) return null

            // Compute RMS
            var sumSquares = 0.0
            for (i in 0 until readCount) {
                val sample = buffer[i].toDouble()
                sumSquares += sample * sample
            }
            val rms = sqrt(sumSquares / readCount)

            // Convert to dB (reference: 1.0 = max 16-bit amplitude)
            return if (rms > 0) {
                (20 * log10(rms / Short.MAX_VALUE) + REFERENCE_DB_OFFSET).toFloat()
                    .coerceIn(0f, 130f)
            } else {
                0f
            }
        } catch (e: SecurityException) {
            logger.e(TAG, "Security exception during audio recording", e)
            return null
        } catch (e: Exception) {
            logger.e(TAG, "Error measuring audio level", e)
            return null
        } finally {
            try {
                audioRecord?.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    /** Classifies noise level based on dB thresholds. */
    private fun classifyNoise(dbLevel: Float): NoiseClassification {
        return when {
            dbLevel < 30f -> NoiseClassification.SILENT
            dbLevel < 45f -> NoiseClassification.QUIET
            dbLevel < 65f -> NoiseClassification.CONVERSATION
            dbLevel < 80f -> NoiseClassification.LOUD
            else -> NoiseClassification.VERY_LOUD
        }
    }

    companion object {
        private const val TAG = "AudioLevelCollector"

        /** Default polling interval (10 minutes). */
        private const val DEFAULT_INTERVAL_MS = 10L * 60 * 1000

        /** Duration of each audio sample in milliseconds. */
        private const val SAMPLE_DURATION_MS = 3000

        /** dB offset to convert from digital full-scale to approximate SPL. */
        private const val REFERENCE_DB_OFFSET = 90f
    }
}
