package com.blackbox.android.collector.base

import com.blackbox.domain.collector.DataCollector
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.settings.CollectionProfile
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Abstract base class for Android data collectors.
 *
 * Provides:
 * - Coroutine scope management with [SupervisorJob] + [Dispatchers.Default]
 * - Periodic collection loop with configurable base interval
 * - Battery-adaptive interval adjustment via [CollectionProfile]
 * - Start/stop lifecycle with logging
 * - Per-cycle error handling (one failed cycle does not crash the collector)
 * - Cycle count tracking
 *
 * Subclasses implement [collectData] to perform the actual data capture.
 * For event-driven collectors (baseIntervalMs = 0), override
 * [onCollectorStarted]/[onCollectorStopped] to register listeners
 * and call [emitRecords] when events occur.
 *
 * @property baseIntervalMs The default polling interval in milliseconds.
 *   A value of 0 indicates event-driven collection (no polling loop).
 * @property logger Logger for lifecycle and error events.
 */
abstract class BaseCollector(
    private val baseIntervalMs: Long,
    protected val logger: BlackBoxLogger,
) : DataCollector {

    private var collectorJob: Job? = null
    private var collectorScope: CoroutineScope? = null
    private val _isRunning = MutableStateFlow(false)

    override val isRunning: Boolean
        get() = _isRunning.value

    /** Observable running state for the orchestrator. */
    val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Current battery-adaptive collection profile. Set by [CollectorOrchestrator]. */
    var collectionProfile: CollectionProfile = CollectionProfile.NORMAL

    /** Number of completed collection cycles since last start. */
    var cycleCount: Long = 0L
        private set

    /** Tag for logging. Defaults to the simple class name. */
    protected open val tag: String
        get() = this::class.simpleName ?: "BaseCollector"

    private var recordCallback: (suspend (List<CollectedRecord>) -> Unit)? = null

    override suspend fun start() {
        if (_isRunning.value) {
            logger.d(tag, "Already running, ignoring start()")
            return
        }

        logger.i(tag, "Starting collector")
        cycleCount = 0L

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        collectorScope = scope

        onCollectorStarted()
        _isRunning.value = true

        if (baseIntervalMs > 0) {
            collectorJob = scope.launch {
                while (isActive) {
                    try {
                        val records = collectData()
                        if (records.isNotEmpty()) {
                            onRecordsCollected(records)
                        }
                        cycleCount++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e(tag, "Error in collection cycle $cycleCount", e)
                    }

                    val adjustedInterval = computeAdjustedInterval()
                    delay(adjustedInterval)
                }
            }
        }

        logger.i(tag, "Collector started (interval=${baseIntervalMs}ms, event-driven=${baseIntervalMs == 0L})")
    }

    override suspend fun stop() {
        if (!_isRunning.value) {
            logger.d(tag, "Already stopped, ignoring stop()")
            return
        }

        logger.i(tag, "Stopping collector (completed $cycleCount cycles)")
        collectorJob?.cancel()
        collectorJob = null
        collectorScope?.cancel()
        collectorScope = null

        onCollectorStopped()
        _isRunning.value = false
    }

    /**
     * Performs a single data collection cycle.
     *
     * Implementations should capture whatever data is available right now
     * and return it as a list of [CollectedRecord] instances.
     * Return an empty list if no data is available in this cycle.
     *
     * For event-driven collectors (baseIntervalMs = 0), this is never
     * called by the base class — use [emitRecords] instead.
     */
    protected abstract suspend fun collectData(): List<CollectedRecord>

    /**
     * Called when the collector is about to start.
     * Override to register BroadcastReceivers, listeners, etc.
     */
    protected open fun onCollectorStarted() {}

    /**
     * Called when the collector is about to stop.
     * Override to unregister BroadcastReceivers, listeners, etc.
     */
    protected open fun onCollectorStopped() {}

    /**
     * Sets the callback that receives collected records.
     * Called by [CollectorOrchestrator] during setup.
     *
     * @param callback Function to invoke with collected records.
     */
    fun setRecordCallback(callback: suspend (List<CollectedRecord>) -> Unit) {
        recordCallback = callback
    }

    /**
     * For event-driven collectors: call this to emit records outside the polling loop.
     *
     * @param records Records captured from an event.
     */
    protected suspend fun emitRecords(records: List<CollectedRecord>) {
        if (records.isEmpty()) return
        cycleCount++
        onRecordsCollected(records)
    }

    private suspend fun onRecordsCollected(records: List<CollectedRecord>) {
        val callback = recordCallback
        if (callback != null) {
            callback(records)
        } else {
            logger.w(tag, "No record callback set, dropping ${records.size} records")
        }
    }

    private fun computeAdjustedInterval(): Long {
        return (baseIntervalMs * collectionProfile.intervalMultiplier).toLong()
    }
}
