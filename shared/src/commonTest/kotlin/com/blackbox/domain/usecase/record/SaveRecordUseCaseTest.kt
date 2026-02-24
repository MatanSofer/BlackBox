package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.BatteryData
import com.blackbox.domain.model.record.BatteryStatus
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakeRecordRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveRecordUseCaseTest {

    private val repository = FakeRecordRepository()
    private val useCase = SaveRecordUseCase(repository, FakeLogger())

    private fun createRecord(
        timestamp: Long = 1000L,
        sessionId: String = "session-1",
    ) = CollectedRecord(
        timestamp = timestamp,
        collectorType = CollectorType.BATTERY,
        data = RecordData.Battery(BatteryData(levelPercent = 80, status = BatteryStatus.NOT_CHARGING)),
        sessionId = sessionId,
        createdAt = timestamp,
    )

    @Test
    fun `invoke with valid record — saves successfully`() = runTest {
        val record = createRecord()
        val result = useCase(record)
        assertTrue(result.isSuccess)
        assertEquals(1, repository.savedRecords.size)
        assertEquals(record, repository.savedRecords.first())
    }

    @Test
    fun `invoke with zero timestamp — returns failure`() = runTest {
        val record = createRecord(timestamp = 0)
        val result = useCase(record)
        assertTrue(result.isFailure)
        assertEquals(0, repository.savedRecords.size)
    }

    @Test
    fun `invoke with negative timestamp — returns failure`() = runTest {
        val record = createRecord(timestamp = -1)
        val result = useCase(record)
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke with blank sessionId — returns failure`() = runTest {
        val record = createRecord(sessionId = "")
        val result = useCase(record)
        assertTrue(result.isFailure)
        assertEquals(0, repository.savedRecords.size)
    }

    @Test
    fun `invoke with whitespace-only sessionId — returns failure`() = runTest {
        val record = createRecord(sessionId = "   ")
        val result = useCase(record)
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        repository.shouldThrow = RuntimeException("DB error")
        val record = createRecord()
        val result = useCase(record)
        assertTrue(result.isFailure)
    }

    @Test
    fun `saveBatch with valid records — saves all`() = runTest {
        val records = listOf(createRecord(timestamp = 1), createRecord(timestamp = 2))
        val result = useCase.saveBatch(records)
        assertTrue(result.isSuccess)
        assertEquals(2, repository.savedRecords.size)
    }

    @Test
    fun `saveBatch with empty list — succeeds without saving`() = runTest {
        val result = useCase.saveBatch(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, repository.savedRecords.size)
    }

    @Test
    fun `saveBatch with invalid record in list — returns failure`() = runTest {
        val records = listOf(createRecord(timestamp = 1), createRecord(timestamp = 0))
        val result = useCase.saveBatch(records)
        assertTrue(result.isFailure)
    }
}
