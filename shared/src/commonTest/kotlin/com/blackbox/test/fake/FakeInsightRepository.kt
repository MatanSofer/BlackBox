package com.blackbox.test.fake

import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.repository.InsightRepository

class FakeInsightRepository : InsightRepository {

    var stepTrend: List<DailyStepCount> = emptyList()
    var screenTimeTrend: List<DailyScreenTime> = emptyList()
    var averageWakeTime: Long? = null
    var averageSleepTime: Long? = null
    var summaries: List<DailySummary> = emptyList()
    var shouldThrow: Throwable? = null

    override suspend fun getStepTrend(startDate: String, endDate: String): List<DailyStepCount> {
        shouldThrow?.let { throw it }
        return stepTrend
    }

    override suspend fun getScreenTimeTrend(startDate: String, endDate: String): List<DailyScreenTime> {
        shouldThrow?.let { throw it }
        return screenTimeTrend
    }

    override suspend fun getAverageWakeTime(startDate: String, endDate: String): Long? {
        return averageWakeTime
    }

    override suspend fun getAverageSleepTime(startDate: String, endDate: String): Long? {
        return averageSleepTime
    }

    override suspend fun getSummariesForAnalysis(startDate: String, endDate: String): List<DailySummary> {
        return summaries
    }
}
