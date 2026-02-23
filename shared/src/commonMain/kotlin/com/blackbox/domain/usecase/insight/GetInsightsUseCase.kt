package com.blackbox.domain.usecase.insight

import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.repository.InsightRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Retrieves aggregated insights and trend data for the Insights screen.
 *
 * Combines step trends, screen time trends, and sleep patterns
 * into a unified insights result.
 *
 * @property insightRepository Repository for aggregated insight data.
 * @property logger Logger for operation tracking.
 */
class GetInsightsUseCase(
    private val insightRepository: InsightRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Retrieves all insights for a date range.
     *
     * @param startDate Start date in ISO format (e.g., "2026-02-01").
     * @param endDate End date in ISO format (e.g., "2026-02-23").
     * @return [Result] containing the [InsightsData].
     */
    suspend operator fun invoke(startDate: String, endDate: String): Result<InsightsData> {
        return runCatching {
            logger.d(TAG, "Fetching insights: $startDate..$endDate")

            val stepTrend = insightRepository.getStepTrend(startDate, endDate)
            val screenTimeTrend = insightRepository.getScreenTimeTrend(startDate, endDate)
            val avgWakeTime = insightRepository.getAverageWakeTime(startDate, endDate)
            val avgSleepTime = insightRepository.getAverageSleepTime(startDate, endDate)

            val result = InsightsData(
                stepTrend = stepTrend,
                screenTimeTrend = screenTimeTrend,
                averageWakeTimeMs = avgWakeTime,
                averageSleepTimeMs = avgSleepTime,
            )

            logger.d(TAG, "Insights fetched: ${stepTrend.size} days of data")
            result
        }
    }

    companion object {
        private const val TAG = "GetInsightsUseCase"
    }
}

/**
 * Aggregated insights data for the Insights screen.
 *
 * @property stepTrend Daily step counts over the date range.
 * @property screenTimeTrend Daily screen time and pickup counts.
 * @property averageWakeTimeMs Average wake time in epoch ms (time of day).
 * @property averageSleepTimeMs Average sleep time in epoch ms (time of day).
 */
data class InsightsData(
    val stepTrend: List<DailyStepCount>,
    val screenTimeTrend: List<DailyScreenTime>,
    val averageWakeTimeMs: Long?,
    val averageSleepTimeMs: Long?,
)
