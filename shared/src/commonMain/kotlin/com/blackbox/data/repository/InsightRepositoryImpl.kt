package com.blackbox.data.repository

import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.DailySummaryMapper
import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.repository.InsightRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [InsightRepository].
 *
 * Provides aggregated trend data derived from [DailySummary] rows.
 * [getStepTrend] and [getScreenTimeTrend] are deprecated — Insights now derives
 * those trends directly from raw records. [getSummariesForAnalysis] is still active
 * for proof-report and analytics paths.
 * All database operations run on [Dispatchers.IO].
 */
class InsightRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : InsightRepository {

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun getStepTrend(startDate: String, endDate: String): List<DailyStepCount> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching step trend: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getStepTrend(startDate, endDate)
                .executeAsList()
                .map { row ->
                    DailyStepCount(
                        date = row.date,
                        steps = row.total_steps.toInt(),
                    )
                }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun getScreenTimeTrend(startDate: String, endDate: String): List<DailyScreenTime> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching screen time trend: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getScreenTimeTrend(startDate, endDate)
                .executeAsList()
                .map { row ->
                    DailyScreenTime(
                        date = row.date,
                        totalMinutes = row.total_screen_time_minutes.toInt(),
                        pickupCount = row.screen_on_count.toInt(),
                    )
                }
        }
    }

    override suspend fun getAverageWakeTime(startDate: String, endDate: String): Long? {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching average wake time: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getAverageWakeTime(startDate, endDate)
                .executeAsOne()
                .avg_wake_time
                ?.toLong()
        }
    }

    override suspend fun getAverageSleepTime(startDate: String, endDate: String): Long? {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching average sleep time: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getAverageSleepTime(startDate, endDate)
                .executeAsOne()
                .avg_sleep_time
                ?.toLong()
        }
    }

    override suspend fun getSummariesForAnalysis(startDate: String, endDate: String): List<DailySummary> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching summaries for analysis: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getSummariesInRange(startDate, endDate)
                .executeAsList()
                .map(DailySummaryMapper::toDomain)
        }
    }

    companion object {
        private const val TAG = "InsightRepository"
    }
}
