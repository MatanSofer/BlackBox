package com.blackbox.domain.platform

/**
 * Platform abstraction for reading today's step count directly from the
 * device's hardware pedometer.
 *
 * The Android implementation reads [android.hardware.Sensor.TYPE_STEP_COUNTER]
 * and stores a per-day baseline in SharedPreferences so that "today's steps"
 * is always relative to midnight, not to device boot.
 */
interface StepCounterProvider {

    /**
     * Returns the number of steps taken today, or null if the hardware
     * step counter is unavailable on this device.
     */
    suspend fun getTodaySteps(): Int?
}
