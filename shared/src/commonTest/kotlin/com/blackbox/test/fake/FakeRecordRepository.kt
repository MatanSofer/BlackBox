package com.blackbox.test.fake

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.repository.RecordRepository

class FakeRecordRepository : RecordRepository {

    val savedRecords = mutableListOf<CollectedRecord>()
    var recordsToReturn: List<CollectedRecord> = emptyList()
    var nextId: Long = 1L
    var shouldThrow: Throwable? = null

    override suspend fun saveRecord(record: CollectedRecord) {
        shouldThrow?.let { throw it }
        savedRecords.add(record)
    }

    override suspend fun saveRecords(records: List<CollectedRecord>) {
        shouldThrow?.let { throw it }
        savedRecords.addAll(records)
    }

    override suspend fun getRecordsInRange(startTime: Long, endTime: Long): List<CollectedRecord> {
        shouldThrow?.let { throw it }
        return recordsToReturn.filter { it.timestamp in startTime..endTime }
    }

    override suspend fun getRecordsByTypeInRange(
        collectorType: CollectorType,
        startTime: Long,
        endTime: Long,
    ): List<CollectedRecord> {
        shouldThrow?.let { throw it }
        return recordsToReturn.filter {
            it.collectorType == collectorType && it.timestamp in startTime..endTime
        }
    }

    override suspend fun getRecordCount(): Long {
        return savedRecords.size.toLong() + recordsToReturn.size.toLong()
    }

    override suspend fun getCountByCollector(): Map<CollectorType, Long> {
        return (savedRecords + recordsToReturn).groupBy { it.collectorType }
            .mapValues { it.value.size.toLong() }
    }

    override suspend fun saveRecordAndGetId(record: CollectedRecord): Long {
        shouldThrow?.let { throw it }
        savedRecords.add(record)
        return nextId++
    }

    override suspend fun deleteRecordsOlderThan(cutoffTimestamp: Long) {
        savedRecords.removeAll { it.timestamp < cutoffTimestamp }
    }
}
