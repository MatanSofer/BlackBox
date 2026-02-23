package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Validates and persists one or more collected records to the database.
 *
 * Steps:
 * 1. Validate that each record has a positive timestamp and non-blank sessionId
 * 2. Delegate to [RecordRepository.saveRecord] or [RecordRepository.saveRecords]
 *
 * @property recordRepository Repository for persisting records.
 * @property logger Logger for operation tracking.
 */
class SaveRecordUseCase(
    private val recordRepository: RecordRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Saves a single collected record.
     *
     * @param record The record to persist.
     * @return [Result.success] with [Unit] on success, or [Result.failure] on error.
     */
    suspend operator fun invoke(record: CollectedRecord): Result<Unit> {
        return runCatching {
            require(record.timestamp > 0) { "Record timestamp must be positive" }
            require(record.sessionId.isNotBlank()) { "Record sessionId must not be blank" }
            logger.d(TAG, "Saving record: type=${record.collectorType}")
            recordRepository.saveRecord(record)
        }
    }

    /**
     * Saves multiple collected records in a single batch transaction.
     *
     * @param records The list of records to persist.
     * @return [Result.success] with [Unit] on success, or [Result.failure] on error.
     */
    suspend fun saveBatch(records: List<CollectedRecord>): Result<Unit> {
        return runCatching {
            if (records.isEmpty()) return@runCatching
            records.forEach { record ->
                require(record.timestamp > 0) { "Record timestamp must be positive" }
                require(record.sessionId.isNotBlank()) { "Record sessionId must not be blank" }
            }
            logger.d(TAG, "Batch saving ${records.size} records")
            recordRepository.saveRecords(records)
        }
    }

    companion object {
        private const val TAG = "SaveRecordUseCase"
    }
}
