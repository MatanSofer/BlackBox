package com.blackbox.domain.util

/**
 * Platform-agnostic logger interface.
 *
 * Provides structured logging at various severity levels.
 * Android implementation wraps [android.util.Log].
 * Logs are for development and debugging only — never log user data
 * (locations, app names, WiFi SSIDs) in production builds.
 */
interface BlackBoxLogger {

    /** Verbose logging for detailed tracing. */
    fun v(tag: String, message: String)

    /** Debug logging for development information. */
    fun d(tag: String, message: String)

    /** Informational logging for significant lifecycle events. */
    fun i(tag: String, message: String)

    /** Warning logging for recoverable issues. */
    fun w(tag: String, message: String, throwable: Throwable? = null)

    /** Error logging for failures requiring attention. */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
