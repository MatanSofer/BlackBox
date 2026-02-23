package com.blackbox.domain.usecase.timeline

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.timeline.AppUsageSummary
import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.repository.TimelineRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Generates a pre-computed daily summary from raw record data.
 *
 * Aggregates location, activity, app usage, screen state, and
 * sensor data into a [DailySummary] for fast query responses.
 * Typically run by the DailySummaryWorker at end-of-day.
 *
 * @property recordRepository Repository for master records.
 * @property locationRepository Repository for location data.
 * @property timelineRepository Repository for saving summaries.
 * @property logger Logger for operation tracking.
 */
class GenerateDailySummaryUseCase(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val timelineRepository: TimelineRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Generates and saves a daily summary for the given date.
     *
     * @param date ISO date string (e.g., "2026-02-23").
     * @param dayStartMs Start of the day in epoch ms.
     * @param dayEndMs End of the day in epoch ms.
     * @return [Result] containing the generated [DailySummary].
     */
    suspend operator fun invoke(
        date: String,
        dayStartMs: Long,
        dayEndMs: Long,
    ): Result<DailySummary> {
        return runCatching {
            logger.d(TAG, "Generating daily summary for $date")

            val allRecords = recordRepository.getRecordsInRange(dayStartMs, dayEndMs)
            val locations = locationRepository.getLocationsInRange(dayStartMs, dayEndMs)

            // Steps
            val activityRecords = allRecords.filter { it.collectorType == CollectorType.ACTIVITY }
            val totalSteps = activityRecords
                .mapNotNull { (it.data as? RecordData.Activity)?.activityData?.stepCountDelta }
                .sum()

            // Distance from location points
            val totalDistance = computeTotalDistance(locations)

            // Screen stats
            val screenRecords = allRecords.filter { it.collectorType == CollectorType.SCREEN_STATE }
            val screenOnCount = screenRecords.count { record ->
                val data = (record.data as? RecordData.ScreenState)?.screenStateData
                data?.state?.name == "ON"
            }
            val firstScreenOn = screenRecords
                .filter { (it.data as? RecordData.ScreenState)?.screenStateData?.state?.name == "ON" }
                .minByOrNull { it.timestamp }?.timestamp
            val lastScreenOff = screenRecords
                .filter { (it.data as? RecordData.ScreenState)?.screenStateData?.state?.name == "OFF" }
                .maxByOrNull { it.timestamp }?.timestamp

            // App usage
            val appRecords = allRecords.filter { it.collectorType == CollectorType.APP_USAGE }
            val mostUsedApps = appRecords
                .mapNotNull { (it.data as? RecordData.AppUsage)?.appUsageData }
                .groupBy { it.foregroundApp }
                .map { (pkg, usages) ->
                    AppUsageSummary(
                        packageName = pkg,
                        displayName = usages.first().displayName,
                        usageMinutes = (usages.sumOf { it.sessionDurationMs } / 60_000).toInt(),
                    )
                }
                .sortedByDescending { it.usageMinutes }
                .take(10)

            // Activity breakdown
            val activityBreakdown = activityRecords
                .mapNotNull { (it.data as? RecordData.Activity)?.activityData?.detectedActivity?.name }
                .groupingBy { it }
                .eachCount()

            // Audio average
            val audioRecords = allRecords.filter { it.collectorType == CollectorType.AUDIO_LEVEL }
            val noiseAvgDb = audioRecords
                .mapNotNull { (it.data as? RecordData.AudioLevel)?.audioLevelData?.dbLevel }
                .takeIf { it.isNotEmpty() }
                ?.average()

            val now = System.currentTimeMillis()
            val summary = DailySummary(
                date = date,
                totalSteps = totalSteps,
                totalDistanceMeters = totalDistance,
                screenOnCount = screenOnCount,
                totalScreenTimeMinutes = 0, // Would require paired on/off analysis
                mostUsedApps = mostUsedApps,
                activityBreakdown = activityBreakdown,
                noiseAvgDb = noiseAvgDb,
                firstScreenOn = firstScreenOn,
                lastScreenOff = lastScreenOff,
                createdAt = now,
                updatedAt = now,
            )

            timelineRepository.saveSummary(summary)
            logger.d(TAG, "Summary saved for $date: ${allRecords.size} records processed")
            summary
        }
    }

    private fun computeTotalDistance(
        locations: List<com.blackbox.domain.repository.LocationEntry>,
    ): Double {
        if (locations.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until locations.size) {
            val prev = locations[i - 1]
            val curr = locations[i]
            total += haversineDistance(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        }
        return total
    }

    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "GenerateDailySummaryUseCase"
    }
}
