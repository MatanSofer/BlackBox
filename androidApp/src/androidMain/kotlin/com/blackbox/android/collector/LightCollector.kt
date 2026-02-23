package com.blackbox.android.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.LightClassification
import com.blackbox.domain.model.record.LightData
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Measures ambient light level to determine indoor/outdoor context.
 *
 * Polling-based collector that reads the TYPE_LIGHT sensor.
 * Disabled by default. Helps determine whether the user is
 * indoors, outdoors, or in darkness (sleeping).
 *
 * @property context Android context for accessing SensorManager.
 * @property saveRecordUseCase Use case for persisting records.
 * @property logger Logger for lifecycle and error events.
 */
class LightCollector(
    private val context: Context,
    private val saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.LIGHT

    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var sessionId: String = ""
    private var lastLux: Float? = null

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting light collector")
        sessionId = UUID.randomUUID().toString()
        lastLux = null

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm

        val lightSensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            logger.w(TAG, "Light sensor not available")
            return
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lastLux = event.values[0]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No action needed
            }
        }
        sensorListener = listener

        sm.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        logger.d(TAG, "Light sensor registered")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping light collector")
        sensorListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        sensorManager = null
        sensorListener = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val lux = lastLux ?: return emptyList()
        val now = System.currentTimeMillis()
        val classification = classifyLight(lux)

        val lightData = LightData(
            lux = lux,
            classification = classification,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.LIGHT,
            data = RecordData.Light(lightData),
            accuracyScore = 0.9f,
            sessionId = sessionId,
            createdAt = now,
        )

        saveRecordUseCase(record)
            .onSuccess {
                logger.d(TAG, "Light saved: ${lux}lux ($classification)")
            }
            .onFailure { e ->
                logger.e(TAG, "Failed to save light record", e)
            }

        return listOf(record)
    }

    /** Classifies light level based on lux thresholds. */
    private fun classifyLight(lux: Float): LightClassification {
        return when {
            lux < 10f -> LightClassification.DARK
            lux < 50f -> LightClassification.DIM
            lux < 500f -> LightClassification.INDOOR
            lux < 10_000f -> LightClassification.OUTDOOR_SHADE
            else -> LightClassification.DIRECT_SUNLIGHT
        }
    }

    companion object {
        private const val TAG = "LightCollector"

        /** Default polling interval (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000
    }
}
