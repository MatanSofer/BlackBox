package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.BatteryData
import com.blackbox.domain.model.record.BatteryStatus
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.LocationData
import com.blackbox.domain.model.record.RecordData
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakeRecordRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecordsUseCaseTest {

    private val repository = FakeRecordRepository()
    private val useCase = GetRecordsUseCase(repository, FakeLogger())

    private val locationRecord = CollectedRecord(
        timestamp = 100L,
        collectorType = CollectorType.LOCATION,
        data = RecordData.Location(LocationData(32.0, 34.0)),
        sessionId = "s1",
        createdAt = 100L,
    )

    private val batteryRecord = CollectedRecord(
        timestamp = 200L,
        collectorType = CollectorType.BATTERY,
        data = RecordData.Battery(BatteryData(levelPercent = 80, status = BatteryStatus.NOT_CHARGING)),
        sessionId = "s1",
        createdAt = 200L,
    )

    @Test
    fun `invoke — returns records within range`() = runTest {
        repository.recordsToReturn = listOf(locationRecord, batteryRecord)
        val result = useCase(50L, 250L)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `invoke — filters out records outside range`() = runTest {
        repository.recordsToReturn = listOf(locationRecord, batteryRecord)
        val result = useCase(150L, 250L)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(CollectorType.BATTERY, result.getOrThrow().first().collectorType)
    }

    @Test
    fun `invoke with empty result — returns empty list`() = runTest {
        repository.recordsToReturn = emptyList()
        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `byType — returns only matching collector type`() = runTest {
        repository.recordsToReturn = listOf(locationRecord, batteryRecord)
        val result = useCase.byType(CollectorType.LOCATION, 0L, 300L)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(CollectorType.LOCATION, result.getOrThrow().first().collectorType)
    }

    @Test
    fun `byType with no matches — returns empty list`() = runTest {
        repository.recordsToReturn = listOf(locationRecord)
        val result = useCase.byType(CollectorType.BATTERY, 0L, 300L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        repository.shouldThrow = RuntimeException("DB error")
        val result = useCase(0L, 1000L)
        assertTrue(result.isFailure)
    }
}
