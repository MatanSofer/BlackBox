package com.blackbox.domain.model.record

/**
 * Atmospheric pressure data captured by the barometer collector.
 *
 * Used for detecting floor changes and providing altitude context.
 * Disabled by default as not all devices have a barometer sensor.
 *
 * @property pressureHpa Atmospheric pressure in hectopascals (hPa).
 * @property relativeAltitudeMeters Estimated altitude relative to a baseline.
 * @property altitudeChangeSinceLast Altitude change since the previous reading.
 * @property estimatedFloorChange Estimated number of floors moved since last reading.
 */
data class BarometerData(
    val pressureHpa: Float,
    val relativeAltitudeMeters: Float? = null,
    val altitudeChangeSinceLast: Float? = null,
    val estimatedFloorChange: Int = 0,
)
