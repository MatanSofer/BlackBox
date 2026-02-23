package com.blackbox.android.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.BarometerData
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Collects atmospheric pressure data for altitude and floor detection.
 *
 * Polling-based collector that reads the barometer sensor. Disabled
 * by default as not all devices have a barometer sensor.
 *
 * @property context Android context for accessing SensorManager.
 * @property saveRecordUseCase Use case for persisting records.
 * @property logger Logger for lifecycle and error events.
 */
class BarometerCollector(
    private val context: Context,
    private val saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.BAROMETER

    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var sessionId: String = ""
    private var lastPressureHpa: Float? = null
    private var baselinePressureHpa: Float? = null

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting barometer collector")
        sessionId = UUID.randomUUID().toString()
        lastPressureHpa = null
        baselinePressureHpa = null

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm

        val pressureSensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressureSensor == null) {
            logger.w(TAG, "Pressure sensor not available")
            return
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lastPressureHpa = event.values[0]
                if (baselinePressureHpa == null) {
                    baselinePressureHpa = event.values[0]
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No action needed
            }
        }
        sensorListener = listener

        sm.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        logger.d(TAG, "Pressure sensor registered")
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping barometer collector")
        sensorListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        sensorManager = null
        sensorListener = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val pressure = lastPressureHpa ?: return emptyList()
        val baseline = baselinePressureHpa ?: pressure
        val now = System.currentTimeMillis()

        val altitudeRelative = SensorManager.getAltitude(
            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
            pressure,
        ) - SensorManager.getAltitude(
            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
            baseline,
        )

        val floorChange = (altitudeRelative / METERS_PER_FLOOR).roundToInt()

        val barometerData = BarometerData(
            pressureHpa = pressure,
            relativeAltitudeMeters = altitudeRelative,
            altitudeChangeSinceLast = altitudeRelative,
            estimatedFloorChange = floorChange,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.BAROMETER,
            data = RecordData.Barometer(barometerData),
            accuracyScore = 0.9f,
            sessionId = sessionId,
            createdAt = now,
        )

        saveRecordUseCase(record)
            .onSuccess {
                logger.d(TAG, "Barometer saved: ${pressure}hPa, floor=$floorChange")
            }
            .onFailure { e ->
                logger.e(TAG, "Failed to save barometer record", e)
            }

        return listOf(record)
    }

    companion object {
        private const val TAG = "BarometerCollector"

        /** Default polling interval (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000

        /** Approximate meters per floor for floor-change estimation. */
        private const val METERS_PER_FLOOR = 3.0f
    }
}
