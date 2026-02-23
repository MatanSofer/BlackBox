package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Retrieves collected records within a time range, optionally filtered by type.
 *
 * @property recordRepository Repository for accessing records.
 * @property logger Logger for operation tracking.
 */
class GetRecordsUseCase(
    private val recordRepository: RecordRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Retrieves all records within a time range.
     *
     * @param startTime Start of the range in epoch ms.
     * @param endTime End of the range in epoch ms.
     * @return [Result] containing the list of matching records.
     */
    suspend operator fun invoke(startTime: Long, endTime: Long): Result<List<CollectedRecord>> {
        return runCatching {
            logger.d(TAG, "Fetching records: $startTime..$endTime")
            recordRepository.getRecordsInRange(startTime, endTime)
        }
    }

    /**
     * Retrieves records of a specific collector type within a time range.
     *
     * @param collectorType The type of records to retrieve.
     * @param startTime Start of the range in epoch ms.
     * @param endTime End of the range in epoch ms.
     * @return [Result] containing the list of matching records.
     */
    suspend fun byType(
        collectorType: CollectorType,
        startTime: Long,
        endTime: Long,
    ): Result<List<CollectedRecord>> {
        return runCatching {
            logger.d(TAG, "Fetching ${collectorType.name} records: $startTime..$endTime")
            recordRepository.getRecordsByTypeInRange(collectorType, startTime, endTime)
        }
    }

    companion object {
        private const val TAG = "GetRecordsUseCase"
    }
}
