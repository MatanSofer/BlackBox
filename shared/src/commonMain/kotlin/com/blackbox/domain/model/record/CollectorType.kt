package com.blackbox.domain.model.record

/**
 * Identifies the type of data collector that produced a record.
 *
 * Each collector independently captures a specific category of
 * contextual metadata from the device. Collectors can be individually
 * enabled or disabled by the user.
 */
enum class CollectorType {
    /** GPS/network-based location coordinates. */
    LOCATION,
    /** Motion state and step counting via Activity Recognition API. */
    ACTIVITY,
    /** WiFi network environment scanning. */
    WIFI,
    /** Foreground app usage tracking. */
    APP_USAGE,
    /** Screen on/off/unlock events. */
    SCREEN_STATE,
    /** Ambient noise level measurement (disabled by default). */
    AUDIO_LEVEL,
    /** Battery level and charging state. */
    BATTERY,
    /** Network and Bluetooth connectivity state. */
    CONNECTIVITY,
    /** Atmospheric pressure sensor (disabled by default). */
    BAROMETER,
    /** Ambient light level sensor (disabled by default). */
    LIGHT,
    /** System call log entries (disabled by default — sensitive). */
    CALL_LOG,
    /** Audio playback state and output device. */
    MEDIA_PLAYBACK,
}
