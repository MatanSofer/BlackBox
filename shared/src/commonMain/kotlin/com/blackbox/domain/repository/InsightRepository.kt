package com.blackbox.domain.repository

import com.blackbox.domain.model.timeline.DailySummary

/**
 * Repository for aggregated insights and pattern data.
 *
 * Provides access to trend data derived from daily summaries,
 * used by the Insights screen for charts and pattern analysis.
 */
interface InsightRepository {

    /** Returns step count per day for a date range (ISO format). */
    suspend fun getStepTrend(startDate: String, endDate: String): List<DailyStepCount>

    /** Returns screen time per day for a date range. */
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
