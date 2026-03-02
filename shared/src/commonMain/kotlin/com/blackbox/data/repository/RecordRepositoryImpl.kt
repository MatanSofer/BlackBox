package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.RecordMapper
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [RecordRepository].
 *
 * Manages the master BlackBoxRecord table for all collected data points.
 * All database operations run on [Dispatchers.IO].
 */
class RecordRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : RecordRepository {

    override suspend fun saveRecord(record: CollectedRecord) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving record: type=${record.collectorType}")
            database.blackBoxDatabaseQueries.insertRecord(
                timestamp = record.timestamp,
                collector_type = record.collectorType.name,
                data_json = RecordMapper.serializeData(record.data),
                accuracy_score = record.accuracyScore.toDouble(),
                session_id = record.sessionId,
                created_at = record.createdAt,
            )
        }
    }

    override suspend fun saveRecords(records: List<CollectedRecord>) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Batch saving ${records.size} records")
            database.blackBoxDatabaseQueries.transaction {
                records.forEach { record ->
                    database.blackBoxDatabaseQueries.insertRecord(
                        timestamp = record.timestamp,
                        collector_type = record.collectorType.name,
                        data_json = RecordMapper.serializeData(record.data),
                        accuracy_score = record.accuracyScore.toDouble(),
                        session_id = record.sessionId,
                        created_at = record.createdAt,
                    )
                }
            }
        }
    }

    override suspend fun saveRecordAndGetId(record: CollectedRecord): Long {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving record and retrieving ID: type=${record.collectorType}")
            database.blackBoxDatabaseQueries.transactionWithResult {
                database.blackBoxDatabaseQueries.insertRecord(
                    timestamp = record.timestamp,
                    collector_type = record.collectorType.name,
                    data_json = RecordMapper.serializeData(record.data),
                    accuracy_score = record.accuracyScore.toDouble(),
                    session_id = record.sessionId,
                    created_at = record.createdAt,
                )
                database.blackBoxDatabaseQueries.lastInsertRecordId().executeAsOne()
            }
        }
    }

    override suspend fun getRecordsInRange(startTime: Long, endTime: Long): List<CollectedRecord> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching records in range: $startTime..$endTime")
            database.blackBoxDatabaseQueries
                .getAllRecordsInRange(startTime, endTime)
                .executeAsList()
                .map(RecordMapper::toDomain)
        }
    }

    override fun observeRecordsInRange(startTime: Long, endTime: Long): Flow<List<CollectedRecord>> {
        return database.blackBoxDatabaseQueries
            .getAllRecordsInRange(startTime, endTime)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map(RecordMapper::toDomain) }
    }

    override suspend fun getRecordsByTypeInRange(
        collectorType: CollectorType,
        startTime: Long,
        endTime: Long,
    ): List<CollectedRecord> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching ${collectorType.name} records: $startTime..$endTime")
            database.blackBoxDatabaseQueries
                .getRecordsByTypeInRange(collectorType.name, startTime, endTime)
                .executeAsList()
                .map(RecordMapper::toDomain)
        }
    }

    override suspend fun getRecordCount(): Long {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getTotalRecordCount()
                .executeAsOne()
        }
    }

    override suspend fun getCountByCollector(): Map<CollectorType, Long> {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .countByCollector()
                .executeAsList()
                .associate { row ->
                    CollectorType.valueOf(row.collector_type) to row.record_count
                }
        }
    }

    override suspend fun deleteRecordsOlderThan(cutoffTimestamp: Long) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Deleting records older than $cutoffTimestamp")
            database.blackBoxDatabaseQueries.deleteOlderThan(cutoffTimestamp)
        }
    }

    companion object {
        private const val TAG = "RecordRepository"
    }
}
