package com.blackbox.domain.usecase.insight

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.repository.InsightRepository
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Aggregates all sensor data for the past 7 days into a single [InsightsBrief].
 *
 * Combines pre-computed daily summaries (step trend, screen trend) with raw
 * records (app usage, locations) to build a rich snapshot of the user's week.
 * Today's live stats are derived from raw records since the daily summary worker
 * only runs at end of day.
 *
 * @property insightRepository Source of pre-computed daily trend data.
 * @property recordRepository Source of raw collected records (app usage, activity).
 * @property locationRepository Source of location entries with resolved addresses.
 * @property logger Logger for diagnostics.
 */
class GetInsightsBriefUseCase(
    private val insightRepository: InsightRepository,
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Fetches and aggregates insights for the last 7 days ending today.
     *
     * @return [Result] containing an [InsightsBrief] on success, or an error.
     */
    suspend operator fun invoke(): Result<InsightsBrief> = runCatching {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val weekStart = today.minus(6, DateTimeUnit.DAY)

        val nowMs = now.toEpochMilliseconds()
        val todayStartMs = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val weekStartMs = weekStart.atStartOfDayIn(tz).toEpochMilliseconds()

        val todayStr = today.toString()
        val weekStartStr = weekStart.toString()

        logger.d(TAG, "Fetching brief: $weekStartStr → $todayStr")

        // Pre-computed trend data from DailySummary
        val stepTrend = insightRepository.getStepTrend(weekStartStr, todayStr)
        val screenTrend = insightRepository.getScreenTimeTrend(weekStartStr, todayStr)

        // Raw app usage for the week — needed for top-app ranking
        val weekAppRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.APP_USAGE, weekStartMs, nowMs,
        )
        val weekTopApps = aggregateTopApps(weekAppRecords, limit = 5)

        // Today's app usage for "top app today"
        val todayAppRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.APP_USAGE, todayStartMs, nowMs,
        )
        val todayTopApp = aggregateTopApps(todayAppRecords, limit = 1).firstOrNull()

        // Location data for the week — needed for top-place ranking
        val weekLocations = locationRepository.getLocationsInRange(weekStartMs, nowMs)
        val weekTopPlaces = aggregateTopPlaces(weekLocations, limit = 5)
        val todayPlacesCount = weekLocations
            .filter { it.timestamp >= todayStartMs }
            .mapNotNull { it.address }
            .distinct()
            .size

        // Today's steps from raw activity records (daily summary may not exist yet)
        val todayActivityRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.ACTIVITY, todayStartMs, nowMs,
        )
        val todaySteps = todayActivityRecords
            .mapNotNull { (it.data as? RecordData.Activity)?.activityData?.stepCountDelta }
            .sum()

        // Derived aggregates
        val avgSteps = stepTrend.map { it.steps }.average().takeIf { it.isFinite() }?.toInt() ?: 0
        val avgScreen = screenTrend.map { it.totalMinutes }.average().takeIf { it.isFinite() }?.toInt() ?: 0
        val bestStepDay = stepTrend.maxByOrNull { it.steps }
        val todayScreenEntry = screenTrend.find { it.date == todayStr }
        val stepsVsAvg = if (avgSteps > 0) todaySteps.toFloat() / avgSteps else 1f

        logger.d(TAG, "Brief ready — today: $todaySteps steps, ${weekTopApps.size} apps, ${weekTopPlaces.size} places")

        InsightsBrief(
            todaySteps = todaySteps,
            todayScreenMinutes = todayScreenEntry?.totalMinutes ?: 0,
            todayPlacesCount = todayPlacesCount,
            todayTopApp = todayTopApp,
            todayStepsVsAvg = stepsVsAvg,
            weekStepTrend = stepTrend,
            weekScreenTrend = screenTrend,
            weekTopPlaces = weekTopPlaces,
            weekTopApps = weekTopApps,
            bestStepDay = bestStepDay,
            avgDailySteps = avgSteps,
            avgDailyScreenMinutes = avgScreen,
        )
    }

    private fun aggregateTopApps(
        records: List<CollectedRecord>,
        limit: Int,
    ): List<AppUsageStat> =
        records
            .mapNotNull { (it.data as? RecordData.AppUsage)?.appUsageData }
            .filter { !it.isSystemApp && it.displayName.isNotBlank() }
            .groupBy { it.displayName }
            .mapValues { (_, entries) -> entries.sumOf { it.sessionDurationMs } / 60_000L }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { AppUsageStat(displayName = it.key, totalMinutes = it.value) }

    private fun aggregateTopPlaces(
        locations: List<LocationEntry>,
        limit: Int,
    ): List<PlaceVisit> =
        locations
            .mapNotNull { entry -> entry.address?.let { it to entry.timestamp } }
            .groupBy { (addr, _) -> addr }
            .mapValues { (_, pairs) ->
                // Count distinct calendar days — one "visit" per day per place
                pairs.map { (_, ts) -> ts / DAY_MS }.distinct().size
            }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { PlaceVisit(address = it.key, visitCount = it.value) }

    companion object {
        private const val TAG = "GetInsightsBriefUseCase"
        private const val DAY_MS = 24 * 3600 * 1000L
    }
}

// ── Output models ─────────────────────────────────────────────────────────────

/**
 * Full aggregated snapshot of the user's last 7 days, used by the Insights screen.
 *
 * @property todaySteps Live step count for today from raw activity records.
 * @property todayScreenMinutes Screen-on minutes today (from DailySummary if available).
 * @property todayPlacesCount Distinct named places visited today.
 * @property todayTopApp App with the most foreground time today.
 * @property todayStepsVsAvg Today's steps divided by the 7-day average (1.0 = on par).
 * @property weekStepTrend Daily step counts for the last 7 days.
 * @property weekScreenTrend Daily screen-time data for the last 7 days.
 * @property weekTopPlaces Most visited places this week, ranked by distinct days.
 * @property weekTopApps Most-used apps this week, ranked by total foreground time.
 * @property bestStepDay The day with the highest step count in the period.
 * @property avgDailySteps Mean daily steps over the 7-day window.
 * @property avgDailyScreenMinutes Mean daily screen minutes over the 7-day window.
 */
data class InsightsBrief(
    val todaySteps: Int = 0,
    val todayScreenMinutes: Int = 0,
    val todayPlacesCount: Int = 0,
    val todayTopApp: AppUsageStat? = null,
    val todayStepsVsAvg: Float = 1f,
    val weekStepTrend: List<DailyStepCount> = emptyList(),
    val weekScreenTrend: List<com.blackbox.domain.repository.DailyScreenTime> = emptyList(),
    val weekTopPlaces: List<PlaceVisit> = emptyList(),
    val weekTopApps: List<AppUsageStat> = emptyList(),
    val bestStepDay: DailyStepCount? = null,
    val avgDailySteps: Int = 0,
    val avgDailyScreenMinutes: Int = 0,
)

/**
 * A place the user has visited, ranked by number of distinct days.
 *
 * @property address Human-readable address from reverse geocoding.
 * @property visitCount Number of distinct days this address appeared in the period.
 */
data class PlaceVisit(
    val address: String,
    val visitCount: Int,
)

/**
 * An app ranked by total foreground time.
 *
 * @property displayName Human-readable app name.
 * @property totalMinutes Total foreground minutes in the period.
 */
data class AppUsageStat(
    val displayName: String,
    val totalMinutes: Long,
)
