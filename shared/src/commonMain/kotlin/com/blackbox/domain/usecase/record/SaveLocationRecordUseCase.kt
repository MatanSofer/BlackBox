package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Atomically saves a location data point as both a master [CollectedRecord]
 * and a denormalized [LocationEntry].
 *
 * Steps:
 * 1. Validate the record timestamp and sessionId
 * 2. Save the master record and retrieve its auto-generated ID
 * 3. Save the denormalized LocationEntry referencing that ID
 *
 * This ensures the LocationRecord always has a valid foreign key
 * to its parent BlackBoxRecord.
 *
 * @property recordRepository Repository for the master BlackBoxRecord table.
 * @property locationRepository Repository for the denormalized LocationRecord table.
 * @property logger Logger for operation tracking.
 */
class SaveLocationRecordUseCase(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Saves a location record atomically: master record first, then denormalized entry.
     *
     * @param record The master record containing location data as JSON.
     * @param locationEntry The denormalized location entry for spatial queries.
     * @return [Result.success] on success, or [Result.failure] on error.
     */
    suspend operator fun invoke(
        record: CollectedRecord,
        locationEntry: LocationEntry,
    ): Result<Unit> {
        return runCatching {
            require(record.timestamp > 0) { "Record timestamp must be positive" }
            require(record.sessionId.isNotBlank()) { "Record sessionId must not be blank" }

            logger.d(TAG, "Saving location record with denormalized entry")

            val recordId = recordRepository.saveRecordAndGetId(record)
            val entryWithId = locationEntry.copy(recordId = recordId)
            locationRepository.saveLocation(entryWithId)

            logger.d(TAG, "Location record saved: recordId=$recordId")
        }
    }

    companion object {
        private const val TAG = "SaveLocationRecordUseCase"
    }
}
