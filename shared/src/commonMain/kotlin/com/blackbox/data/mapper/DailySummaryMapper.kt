package com.blackbox.data.mapper

import com.blackbox.domain.model.timeline.AppUsageSummary
import com.blackbox.domain.model.timeline.VisitedLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.blackbox.data.database.DailySummary as DbSummary
import com.blackbox.domain.model.timeline.DailySummary as DomainSummary

/**
 * Maps between SQLDelight [DbSummary] and domain [DomainSummary].
 *
 * Handles JSON serialization for `most_used_apps`, `locations_visited`,
 * and `activity_breakdown` columns.
 */
object DailySummaryMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts a SQLDelight [DbSummary] row to a domain [DomainSummary].
     */
    fun toDomain(db: DbSummary): DomainSummary = DomainSummary(
        id = db.id,
        date = db.date,
        totalSteps = db.total_steps.toInt(),
        totalDistanceMeters = db.total_distance_meters,
        timeAtHomeMinutes = db.time_at_home_minutes.toInt(),
        timeAtWorkMinutes = db.time_at_work_minutes.toInt(),
        timeInTransitMinutes = db.time_in_transit_minutes.toInt(),
        screenOnCount = db.screen_on_count.toInt(),
        totalScreenTimeMinutes = db.total_screen_time_minutes.toInt(),
        mostUsedApps = deserializeApps(db.most_used_apps),
        locationsVisited = deserializeLocations(db.locations_visited),
        activityBreakdown = deserializeActivityBreakdown(db.activity_breakdown),
        noiseAvgDb = db.noise_avg_db,
        firstScreenOn = db.first_screen_on,
        lastScreenOff = db.last_screen_off,
        sleepStartEstimate = db.sleep_start_estimate,
        sleepEndEstimate = db.sleep_end_estimate,
        dayHash = db.day_hash,
        previousDayHash = db.previous_day_hash,
        summaryText = db.summary_text,
        createdAt = db.created_at,
        updatedAt = db.updated_at,
    )

    /**
     * Serializes a list of [AppUsageSummary] to JSON for database storage.
     */
    fun serializeApps(apps: List<AppUsageSummary>): String =
        json.encodeToString(apps)

    /**
     * Serializes a list of [VisitedLocation] to JSON for database storage.
     */
    fun serializeLocations(locations: List<VisitedLocation>): String =
        json.encodeToString(locations)

    /**
     * Serializes the activity breakdown map to JSON for database storage.
     */
    fun serializeActivityBreakdown(breakdown: Map<String, Int>): String =
        json.encodeToString(breakdown)

    private fun deserializeApps(jsonStr: String): List<AppUsageSummary> = try {
        json.decodeFromString(jsonStr)
    } catch (_: Exception) {
        emptyList()
    }

    private fun deserializeLocations(jsonStr: String): List<VisitedLocation> = try {
        json.decodeFromString(jsonStr)
    } catch (_: Exception) {
        emptyList()
    }

    private fun deserializeActivityBreakdown(jsonStr: String): Map<String, Int> = try {
        json.decodeFromString(jsonStr)
    } catch (_: Exception) {
        emptyMap()
    }
}
