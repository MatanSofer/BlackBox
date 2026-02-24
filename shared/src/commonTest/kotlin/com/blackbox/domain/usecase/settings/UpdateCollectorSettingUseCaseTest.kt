package com.blackbox.domain.usecase.settings

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCollectorSettingUseCaseTest {

    private val repository = FakeSettingsRepository()
    private val useCase = UpdateCollectorSettingUseCase(repository, FakeLogger())

    @Test
    fun `invoke — toggles collector enabled state`() = runTest {
        repository.settings[CollectorType.LOCATION] = CollectorSetting(
            collectorType = CollectorType.LOCATION,
            isEnabled = true,
            collectionIntervalMs = 300_000L,
        )

        val result = useCase(CollectorType.LOCATION, false)
        assertTrue(result.isSuccess)
        assertFalse(repository.settings[CollectorType.LOCATION]!!.isEnabled)
    }

    @Test
    fun `invoke — enables disabled collector`() = runTest {
        repository.settings[CollectorType.WIFI] = CollectorSetting(
            collectorType = CollectorType.WIFI,
            isEnabled = false,
            collectionIntervalMs = 600_000L,
        )

        useCase(CollectorType.WIFI, true)
        assertTrue(repository.settings[CollectorType.WIFI]!!.isEnabled)
    }

    @Test
    fun `updateInterval — changes collection interval`() = runTest {
        repository.settings[CollectorType.BATTERY] = CollectorSetting(
            collectorType = CollectorType.BATTERY,
            isEnabled = true,
            collectionIntervalMs = 60_000L,
        )

        val result = useCase.updateInterval(CollectorType.BATTERY, 120_000L)
        assertTrue(result.isSuccess)
        assertEquals(120_000L, repository.settings[CollectorType.BATTERY]!!.collectionIntervalMs)
    }

    @Test
    fun `updateInterval with negative value — returns failure`() = runTest {
        val result = useCase.updateInterval(CollectorType.BATTERY, -1L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `updateInterval with zero — succeeds for event-driven`() = runTest {
        repository.settings[CollectorType.LOCATION] = CollectorSetting(
            collectorType = CollectorType.LOCATION,
            isEnabled = true,
            collectionIntervalMs = 300_000L,
        )

        val result = useCase.updateInterval(CollectorType.LOCATION, 0L)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `updateFull — persists complete setting`() = runTest {
        val setting = CollectorSetting(
            collectorType = CollectorType.LIGHT,
            isEnabled = false,
            collectionIntervalMs = 30_000L,
            customConfigJson = """{"sensitivity": "high"}""",
        )

        val result = useCase.updateFull(setting)
        assertTrue(result.isSuccess)
        assertEquals(setting, repository.settings[CollectorType.LIGHT])
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        repository.shouldThrow = RuntimeException("DB error")
        val result = useCase(CollectorType.LOCATION, true)
        assertTrue(result.isFailure)
    }
}
