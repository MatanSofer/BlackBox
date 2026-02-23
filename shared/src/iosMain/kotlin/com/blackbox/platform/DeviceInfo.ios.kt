package com.blackbox.platform

import platform.UIKit.UIDevice

/**
 * iOS implementation of [DeviceInfo].
 *
 * Uses [UIDevice] for device metadata.
 */
actual class DeviceInfo actual constructor() {

    /** Returns a stable device identifier. */
    actual fun deviceId(): String =
        UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown"

    /** Returns the device model name. */
    actual fun deviceModel(): String = UIDevice.currentDevice.model

    /** Returns the iOS version string. */
    actual fun osVersion(): String =
        "iOS ${UIDevice.currentDevice.systemVersion}"

    /** Returns the app version name. */
    actual fun appVersion(): String = "1.0.0"
}
