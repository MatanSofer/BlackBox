package com.blackbox.domain.repository

import com.blackbox.domain.model.timeline.DailySummary

/**
 * Repository for aggregated insights and pattern data derived from [DailySummary] rows.
 *
 * ## Current usage
 * - [getSummariesForAnalysis] — still used by proof-report and future analytics paths.
 * - [getAverageWakeTime] / [getAverageSleepTime] — available for external callers.
 *
 * ## Deprecated methods
 * [getStepTrend] and [getScreenTimeTrend] are no longer called by [GetInsightsBriefUseCase];
 * weekly trend data is now derived directly from raw `CollectedRecord` entries so charts always
 * show exactly 7 bars regardless of whether `DailySummaryWorker` ran every night.
 * These methods remain in the interface for backwards compatibility but should not be used
 * for new feature work.
 */
interface InsightRepository {

    /**
     * Returns step count per day for a date range (ISO format).
     *
     * @deprecated Insights now computes step trends from raw ACTIVITY records.
     * Use [GetInsightsBriefUseCase] instead, which always returns exactly 7 entries.
     */
    @Deprecated(
        message = "Insights computes step trends from raw ACTIVITY records. Use GetInsightsBriefUseCase.",
        level = DeprecationLevel.WARNING,
    )
    suspend fun getStepTrend(startDate: String, endDate: String): List<DailyStepCount>

    /**
     * Returns screen time per day for a date range.
     *
     * @deprecated Insights now computes screen-time trends from raw SCREEN_STATE records.
     * Use [GetInsightsBriefUseCase] instead, which always returns exactly 7 entries.
     */
    @Deprecated(
        message = "Insights computes screen-time trends from raw SCREEN_STATE records. Use GetInsightsBriefUseCase.",
        level = DeprecationLevel.WARNING,
    )
    suspend fun getScreenTimeTrend(startDate: String, endDate: String): List<DailyScreenTime>

    /** Returns the average wake-up time across a date range (epoch ms). */
    suspend fun getAverageWakeTime(startDate: String, endDate: String): Long?

    /** Returns the average sleep time across a date range (epoch ms). */
    suspend fun getAverageSleepTime(startDate: String, endDate: String): Long?

    /** Returns summaries for the given date range for pattern analysis. */
    suspend fun getSummariesForAnalysis(startDate: String, endDate: String): List<DailySummary>
}

/**
 * Step count for a single day.
 *
 * @property date ISO date string.
 * @property steps Total step count.
 */
data class DailyStepCount(
    val date: String,
    val steps: Int,
)

/**
 * Screen time data for a single day.
 *
 * @property date ISO date string.
 * @property totalMinutes Total screen-on minutes.
 * @property pickupCount Number of screen-on events.
 */
data class DailyScreenTime(
    val date: String,
    val totalMinutes: Int,
    val pickupCount: Int,
)
