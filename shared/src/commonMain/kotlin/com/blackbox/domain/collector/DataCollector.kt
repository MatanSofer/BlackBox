package com.blackbox.domain.collector

import com.blackbox.domain.model.record.CollectorType

/**
 * Platform-agnostic contract for a data collector.
 *
 * Each collector independently captures a specific category of contextual
 * metadata from the device. Collectors are managed by the platform-specific
 * orchestrator and can be individually started or stopped.
 *
 * Implementations must be safe to call [start] and [stop] multiple times.
 * Calling [start] on an already-running collector is a no-op.
 * Calling [stop] on an already-stopped collector is a no-op.
 */
interface DataCollector {

    /** The type of data this collector captures. */
    val collectorType: CollectorType

    /** Whether this collector is currently running and capturing data. */
    val isRunning: Boolean

    /**
     * Starts the collection process.
     *
     * For polling-based collectors, begins the periodic collection loop.
     * For event-driven collectors, registers the required listeners.
     * Does nothing if the collector is already running.
     */
    suspend fun start()

    /**
     * Stops the collection process and releases resources.
     *
     * Cancels any pending collection cycles and unregisters listeners.
     * Does nothing if the collector is already stopped.
     */
    suspend fun stop()
}
