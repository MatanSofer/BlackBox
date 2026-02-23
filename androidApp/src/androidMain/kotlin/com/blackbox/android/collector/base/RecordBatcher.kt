package com.blackbox.android.collector.base

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Buffers collected records and flushes them in batches to the database.
 *
 * Reduces the number of database transactions by accumulating records
 * and writing them in a single batch. Flushes occur either when the
 * batch size threshold is reached or when the flush interval expires,
 * whichever comes first.
 *
 * Thread-safe: uses a [Mutex] to protect the internal buffer.
 *
 * @property saveRecordUseCase Use case for persisting records.
 * @property logger Logger for operation tracking.
 * @property maxBatchSize Maximum records to accumulate before auto-flush.
 * @property flushIntervalMs Time-based flush interval in milliseconds.
 */
class RecordBatcher(
    private val saveRecordUseCase: SaveRecordUseCase,
    private val logger: BlackBoxLogger,
    private val maxBatchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
) {

    private val buffer = mutableListOf<CollectedRecord>()
    private val mutex = Mutex()
    private var flushJob: Job? = null
    private var batcherScope: CoroutineScope? = null

    /**
     * Starts the periodic flush timer.
     * Must be called before adding records.
     */
    fun start() {
        logger.d(TAG, "Starting batcher (batchSize=$maxBatchSize, flushInterval=${flushIntervalMs}ms)")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        batcherScope = scope

        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    /**
     * Stops the periodic flush timer and flushes any remaining records.
     */
    suspend fun stop() {
        logger.d(TAG, "Stopping batcher")
        flushJob?.cancel()
        flushJob = null
        flush()
        batcherScope?.cancel()
        batcherScope = null
    }

    /**
     * Adds records to the buffer. Triggers an immediate flush if the
     * buffer exceeds [maxBatchSize].
     *
     * @param records Records to buffer for batch writing.
     */
    suspend fun add(records: List<CollectedRecord>) {
        val shouldFlush: Boolean
        mutex.withLock {
            buffer.addAll(records)
            shouldFlush = buffer.size >= maxBatchSize
        }
        if (shouldFlush) {
            flush()
        }
    }

    /**
     * Flushes all buffered records to the database.
     * Safe to call even when the buffer is empty.
     */
    suspend fun flush() {
        val batch: List<CollectedRecord>
        mutex.withLock {
            if (buffer.isEmpty()) return
            batch = buffer.toList()
            buffer.clear()
        }

        logger.d(TAG, "Flushing ${batch.size} records")
        saveRecordUseCase.saveBatch(batch)
            .onFailure { error ->
                logger.e(TAG, "Failed to flush ${batch.size} records", error)
                mutex.withLock {
                    buffer.addAll(0, batch)
                }
            }
    }

    companion object {
        private const val TAG = "RecordBatcher"
        /** Default maximum batch size before auto-flush. */
        const val DEFAULT_BATCH_SIZE = 20
        /** Default time-based flush interval (30 seconds). */
        const val DEFAULT_FLUSH_INTERVAL_MS = 30_000L
    }
}
