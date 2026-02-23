package com.blackbox.domain.repository

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType

/**
 * Repository for managing collected data records.
 *
 * Provides CRUD operations on the BlackBoxRecord table,
 * which is the master table for all data points from all collectors.
 */
interface RecordRepository {

    /** Persists a new record to the database. */
    suspend fun saveRecord(record: CollectedRecord)

    /** Saves multiple records in a single transaction (batch write). */
    suspend fun saveRecords(records: List<CollectedRecord>)

    /** Retrieves all records within a time range, ordered by timestamp. */
    suspend fun getRecordsInRange(startTime: Long, endTime: Long): List<CollectedRecord>

    /** Retrieves records of a specific type within a time range. */
    suspend fun getRecordsByTypeInRange(
        collectorType: CollectorType,
        startTime: Long,
        endTime: Long,
    ): List<CollectedRecord>

    /** Returns the total number of records in the database. */
    suspend fun getRecordCount(): Long

    /** Returns record counts grouped by collector type. */
    suspend fun getCountByCollector(): Map<CollectorType, Long>

    /**
     * Persists a new record and returns its auto-generated database ID.
     *
     * Used when a denormalized entry (e.g., LocationRecord) needs to
     * reference the parent BlackBoxRecord via a foreign key.
     *
     * @param record The record to persist.
     * @return The auto-generated primary key of the inserted record.
     */
    suspend fun saveRecordAndGetId(record: CollectedRecord): Long

    /** Deletes all records older than the given timestamp. */
    suspend fun deleteRecordsOlderThan(cutoffTimestamp: Long)
}
