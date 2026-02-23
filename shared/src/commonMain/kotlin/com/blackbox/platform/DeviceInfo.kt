package com.blackbox.platform

/**
 * Platform-agnostic device information provider.
 *
 * Provides device-specific identifiers and metadata used for
 * record tagging and analytics. No personally identifiable
 * information is exposed — only hardware/OS-level details.
 */
expect class DeviceInfo() {

    /** Returns a stable device identifier (e.g., Android ID). */
    fun deviceId(): String

    /** Returns the device model name (e.g., "Pixel 8 Pro"). */
    fun deviceModel(): String

    /** Returns the OS version string (e.g., "Android 14"). */
    fun osVersion(): String

    /** Returns the app version name (e.g., "1.0.0"). */
    fun appVersion(): String
}
