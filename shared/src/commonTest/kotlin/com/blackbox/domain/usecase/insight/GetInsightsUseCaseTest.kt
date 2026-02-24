package com.blackbox.domain.usecase.insight

import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.test.fake.FakeInsightRepository
import com.blackbox.test.fake.FakeLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetInsightsUseCaseTest {

    private val repository = FakeInsightRepository()
    private val useCase = GetInsightsUseCase(repository, FakeLogger())

    @Test
    fun `invoke — returns aggregated insights`() = runTest {
        repository.stepTrend = listOf(
            DailyStepCount("2026-02-20", 8000),
            DailyStepCount("2026-02-21", 12000),
        )
        repository.screenTimeTrend = listOf(
            DailyScreenTime("2026-02-20", totalMinutes = 180, pickupCount = 42),
        )
        repository.averageWakeTime = 25_200_000L // 7:00 AM
        repository.averageSleepTime = 82_800_000L // 11:00 PM

        val result = useCase("2026-02-20", "2026-02-21")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(2, data.stepTrend.size)
        assertEquals(1, data.screenTimeTrend.size)
        assertEquals(25_200_000L, data.averageWakeTimeMs)
        assertEquals(82_800_000L, data.averageSleepTimeMs)
    }

    @Test
    fun `invoke with empty data — returns empty trends`() = runTest {
        val result = useCase("2026-02-01", "2026-02-28")
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertTrue(data.stepTrend.isEmpty())
        assertTrue(data.screenTimeTrend.isEmpty())
        assertNull(data.averageWakeTimeMs)
        assertNull(data.averageSleepTimeMs)
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        repository.shouldThrow = RuntimeException("DB error")
        val result = useCase("2026-02-01", "2026-02-28")
        assertTrue(result.isFailure)
    }
}
