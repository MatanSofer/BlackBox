package com.blackbox.domain.usecase.record

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.LocationData
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.test.fake.FakeLocationRepository
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakeRecordRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveLocationRecordUseCaseTest {

    private val recordRepo = FakeRecordRepository()
    private val locationRepo = FakeLocationRepository()
    private val useCase = SaveLocationRecordUseCase(recordRepo, locationRepo, FakeLogger())

    private val testRecord = CollectedRecord(
        timestamp = 1000L,
        collectorType = CollectorType.LOCATION,
        data = RecordData.Location(LocationData(latitude = 32.0, longitude = 34.0)),
        sessionId = "session-1",
        createdAt = 1000L,
    )

    private val testEntry = LocationEntry(
        latitude = 32.0,
        longitude = 34.0,
        timestamp = 1000L,
    )

    @Test
    fun `invoke with valid data — saves both record and location entry`() = runTest {
        recordRepo.nextId = 42L
        val result = useCase(testRecord, testEntry)

        assertTrue(result.isSuccess)
        assertEquals(1, recordRepo.savedRecords.size)
        assertEquals(1, locationRepo.savedLocations.size)
        assertEquals(42L, locationRepo.savedLocations.first().recordId)
    }

    @Test
    fun `invoke — location entry gets the generated record ID`() = runTest {
        recordRepo.nextId = 99L
        useCase(testRecord, testEntry)
        assertEquals(99L, locationRepo.savedLocations.first().recordId)
    }

    @Test
    fun `invoke with zero timestamp — returns failure`() = runTest {
        val bad = testRecord.copy(timestamp = 0)
        val result = useCase(bad, testEntry)
        assertTrue(result.isFailure)
        assertEquals(0, recordRepo.savedRecords.size)
        assertEquals(0, locationRepo.savedLocations.size)
    }

    @Test
    fun `invoke with blank sessionId — returns failure`() = runTest {
        val bad = testRecord.copy(sessionId = "")
        val result = useCase(bad, testEntry)
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke when record repository fails — returns failure`() = runTest {
        recordRepo.shouldThrow = RuntimeException("DB error")
        val result = useCase(testRecord, testEntry)
        assertTrue(result.isFailure)
        assertEquals(0, locationRepo.savedLocations.size)
    }

    @Test
    fun `invoke when location repository fails — returns failure`() = runTest {
        locationRepo.shouldThrow = RuntimeException("DB error")
        val result = useCase(testRecord, testEntry)
        assertTrue(result.isFailure)
    }
}
