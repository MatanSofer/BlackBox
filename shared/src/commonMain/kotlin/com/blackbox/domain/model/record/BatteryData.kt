package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Battery state data captured by the battery collector.
 *
 * Tracks battery level, charging state, and health metrics.
 * Event-driven for charging state changes, periodic for level updates.
 *
 * @property levelPercent Current battery percentage (0-100).
 * @property status Current charging status.
 * @property plugType How the device is connected to power.
 * @property temperatureCelsius Battery temperature in Celsius.
 * @property voltageMv Battery voltage in millivolts.
 * @property health Battery health status.
 */
@Serializable
data class BatteryData(
    val levelPercent: Int,
    val status: BatteryStatus,
    val plugType: PlugType = PlugType.NONE,
    val temperatureCelsius: Float? = null,
    val voltageMv: Int? = null,
    val health: BatteryHealth? = null,
)

/**
 * Battery charging status.
 */
enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
}

/**
 * Power source type.
 */
enum class PlugType {
    NONE,
    AC,
    USB,
    WIRELESS,
}

/**
 * Battery health state.
 */
enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    COLD,
    UNKNOWN,
}
