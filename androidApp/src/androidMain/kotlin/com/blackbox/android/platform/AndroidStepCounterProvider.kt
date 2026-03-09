package com.blackbox.android.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.blackbox.domain.platform.StepCounterProvider
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reads today's step count directly from the hardware step counter sensor.
 *
 * [Sensor.TYPE_STEP_COUNTER] gives cumulative steps since the last device
 * reboot. To isolate today's steps we store a daily baseline:
 * - On first read of a new calendar day, we save the current counter value
 *   as today's baseline in SharedPreferences.
 * - Today's steps = current sensor value − baseline.
 * - If the device was rebooted today the counter may be below the stored
 *   baseline; in that case we return the raw counter value (steps since boot
 *   ≈ steps today).
 *
 * The sensor delivers its current value almost immediately after registration
 * (usually < 200 ms). A 5-second timeout is used as a safety net.
 *
 * @property context Application context for sensor and SharedPreferences access.
 * @property logger Logger for diagnostics.
 */
class AndroidStepCounterProvider(
    private val context: Context,
    private val logger: BlackBoxLogger,
) : StepCounterProvider {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun getTodaySteps(): Int? {
        val current = readSensorNow() ?: run {
            logger.w(TAG, "Step counter sensor unavailable")
            return null
        }

        val today = dateFormat.format(Date())
        val storedDate = prefs.getString(KEY_DATE, "")
        val baseline: Long

        if (storedDate == today) {
            baseline = prefs.getLong(KEY_BASELINE, current)
        } else {
            // New calendar day — reset baseline to current counter value
            baseline = current
            prefs.edit()
                .putString(KEY_DATE, today)
                .putLong(KEY_BASELINE, current)
                .apply()
            logger.d(TAG, "New day baseline set: $current steps at midnight")
        }

        val delta = current - baseline
        val todaySteps = if (delta >= 0) delta.toInt() else {
            // Counter is below baseline — device was rebooted today.
            // The counter restarted from 0 (or near 0), so the raw value
            // is a good approximation of today's steps.
            logger.d(TAG, "Step counter reset detected (reboot?), using raw value: $current")
            current.toInt()
        }

        logger.d(TAG, "Steps today: $todaySteps (current=$current, baseline=$baseline)")
        return todaySteps
    }

    /**
     * Registers a one-shot listener on [Sensor.TYPE_STEP_COUNTER] and returns
     * the first value delivered, or null if the sensor is absent or times out.
     */
    private suspend fun readSensorNow(): Long? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return null

        return withTimeoutOrNull(SENSOR_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                val registered = sensorManager.registerListener(
                    listener,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )

                if (!registered) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AndroidStepCounterProvider"
        private const val PREFS_NAME = "blackbox_step_counter"
        private const val KEY_DATE = "baseline_date"
        private const val KEY_BASELINE = "baseline_count"
        private const val SENSOR_TIMEOUT_MS = 5_000L
    }
}
