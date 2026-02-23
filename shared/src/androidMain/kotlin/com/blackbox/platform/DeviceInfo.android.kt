package com.blackbox.platform

import android.os.Build

/**
 * Android implementation of [DeviceInfo].
 *
 * Uses [Build] constants for device metadata. The device ID
 * returns the build fingerprint as a stable (but non-PII) identifier.
 */
actual class DeviceInfo actual constructor() {

    /** Returns a stable device identifier based on build fingerprint. */
    actual fun deviceId(): String = Build.FINGERPRINT

    /** Returns the device model name. */
    actual fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    /** Returns the Android version string. */
    actual fun osVersion(): String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    /** Returns the app version name. */
    actual fun appVersion(): String = "1.0.0"
}
