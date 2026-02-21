package com.blackbox.domain.model.record

import kotlinx.serialization.Serializable

/**
 * Screen state data captured by the screen state collector.
 *
 * Tracks screen on/off/unlock events and display properties.
 * Event-driven collection with zero battery impact.
 *
 * @property state Current screen state.
 * @property brightness Screen brightness level (0-255).
 * @property brightnessMode How brightness is controlled.
 * @property orientation Current device orientation.
 * @property isInteractive Whether the screen is currently interactive.
 */
@Serializable
data class ScreenStateData(
    val state: ScreenState,
    val brightness: Int? = null,
    val brightnessMode: BrightnessMode? = null,
    val orientation: DeviceOrientation? = null,
    val isInteractive: Boolean = false,
)

/**
 * Screen power and lock states.
 */
enum class ScreenState {
    /** Screen turned on. */
    ON,
    /** Screen turned off. */
    OFF,
    /** Device unlocked by user. */
    UNLOCKED,
    /** Device locked. */
    LOCKED,
}

/**
 * Screen brightness control mode.
 */
enum class BrightnessMode {
    /** Brightness set manually by user. */
    MANUAL,
    /** Automatic brightness based on ambient light. */
    AUTO,
}

/**
 * Device physical orientation.
 */
enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
    FACE_UP,
    FACE_DOWN,
}
