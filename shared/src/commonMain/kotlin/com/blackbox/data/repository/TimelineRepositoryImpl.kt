package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.DailySummaryMapper
import com.blackbox.data.mapper.DerivedEventMapper
import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.model.timeline.DerivedEvent
import com.blackbox.domain.model.timeline.EventType
import com.blackbox.domain.repository.TimelineRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [TimelineRepository].
 *
 * Manages daily summaries and derived events that form the user's
 * activity timeline. All database operations run on [Dispatchers.IO].
 */
class TimelineRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : TimelineRepository {

    // ── Daily Summaries ──

    override suspend fun getSummaryForDate(date: String): DailySummary? {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching summary for date: $date")
            database.blackBoxDatabaseQueries
                .getSummaryForDate(date)
                .executeAsOneOrNull()
                ?.let(DailySummaryMapper::toDomain)
        }
    }

    override suspend fun getSummariesInRange(startDate: String, endDate: String): List<DailySummary> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching summaries: $startDate..$endDate")
            database.blackBoxDatabaseQueries
                .getSummariesInRange(startDate, endDate)
                .executeAsList()
                .map(DailySummaryMapper::toDomain)
        }
    }

    override suspend fun saveSummary(summary: DailySummary) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving summary for date: ${summary.date}")
            val existing = database.blackBoxDatabaseQueries
                .getSummaryForDate(summary.date)
                .executeAsOneOrNull()

            if (existing != null) {
                database.blackBoxDatabaseQueries.updateSummary(
                    total_steps = summary.totalSteps.toLong(),
                    total_distance_meters = summary.totalDistanceMeters,
                    time_at_home_minutes = summary.timeAtHomeMinutes.toLong(),
                    time_at_work_minutes = summary.timeAtWorkMinutes.toLong(),
                    time_in_transit_minutes = summary.timeInTransitMinutes.toLong(),
                    screen_on_count = summary.screenOnCount.toLong(),
                    total_screen_time_minutes = summary.totalScreenTimeMinutes.toLong(),
                    most_used_apps = DailySummaryMapper.serializeApps(summary.mostUsedApps),
                    locations_visited = DailySummaryMapper.serializeLocations(summary.locationsVisited),
                    activity_breakdown = DailySummaryMapper.serializeActivityBreakdown(summary.activityBreakdown),
                    noise_avg_db = summary.noiseAvgDb,
                    first_screen_on = summary.firstScreenOn,
                    last_screen_off = summary.lastScreenOff,
                    sleep_start_estimate = summary.sleepStartEstimate,
                    sleep_end_estimate = summary.sleepEndEstimate,
                    day_hash = summary.dayHash,
                    previous_day_hash = summary.previousDayHash,
                    summary_text = summary.summaryText,
                    updated_at = summary.updatedAt,
                    date = summary.date,
                )
            } else {
                database.blackBoxDatabaseQueries.insertSummary(
                    date = summary.date,
                    total_steps = summary.totalSteps.toLong(),
                    total_distance_meters = summary.totalDistanceMeters,
                    time_at_home_minutes = summary.timeAtHomeMinutes.toLong(),
                    time_at_work_minutes = summary.timeAtWorkMinutes.toLong(),
                    time_in_transit_minutes = summary.timeInTransitMinutes.toLong(),
                    screen_on_count = summary.screenOnCount.toLong(),
                    total_screen_time_minutes = summary.totalScreenTimeMinutes.toLong(),
                    most_used_apps = DailySummaryMapper.serializeApps(summary.mostUsedApps),
                    locations_visited = DailySummaryMapper.serializeLocations(summary.locationsVisited),
                    activity_breakdown = DailySummaryMapper.serializeActivityBreakdown(summary.activityBreakdown),
                    noise_avg_db = summary.noiseAvgDb,
                    first_screen_on = summary.firstScreenOn,
                    last_screen_off = summary.lastScreenOff,
                    sleep_start_estimate = summary.sleepStartEstimate,
                    sleep_end_estimate = summary.sleepEndEstimate,
                    day_hash = summary.dayHash,
                    previous_day_hash = summary.previousDayHash,
                    summary_text = summary.summaryText,
                    created_at = summary.createdAt,
                    updated_at = summary.updatedAt,
                )
            }
        }
    }

    override fun observeSummary(date: String): Flow<DailySummary?> {
        return database.blackBoxDatabaseQueries
            .getSummaryForDate(date)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.let(DailySummaryMapper::toDomain) }
    }

    // ── Derived Events ──

    override suspend fun getEventsInRange(startTime: Long, endTime: Long): List<DerivedEvent> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching events: $startTime..$endTime")
            database.blackBoxDatabaseQueries
                .getEventsInRange(startTime, endTime)
                .executeAsList()
                .map(DerivedEventMapper::toDomain)
        }
    }

    override suspend fun getPlaceEvents(placeId: Long, limit: Int): List<DerivedEvent> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching place events: placeId=$placeId, limit=$limit")
            database.blackBoxDatabaseQueries
                .getPlaceEvents(placeId, limit.toLong())
                .executeAsList()
                .map(DerivedEventMapper::toDomain)
        }
    }

    override suspend fun getLatestEventOfType(eventType: EventType): DerivedEvent? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getLatestEventOfType(eventType.name)
                .executeAsOneOrNull()
                ?.let(DerivedEventMapper::toDomain)
        }
    }

    override suspend fun getSleepEvents(startTime: Long, endTime: Long): List<DerivedEvent> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching sleep events: $startTime..$endTime")
            database.blackBoxDatabaseQueries
                .getSleepEvents(startTime, endTime)
                .executeAsList()
                .map(DerivedEventMapper::toDomain)
        }
    }

    override suspend fun saveEvent(event: DerivedEvent) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving derived event: type=${event.eventType}")
            database.blackBoxDatabaseQueries.insertEvent(
                timestamp = event.timestamp,
                event_type = event.eventType.name,
                description = event.description,
                place_id = event.placeId,
                metadata_json = event.metadataJson,
                confidence = event.confidence.toDouble(),
                created_at = event.createdAt,
            )
        }
    }

    override suspend fun saveEvents(events: List<DerivedEvent>) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Batch saving ${events.size} derived events")
            database.blackBoxDatabaseQueries.transaction {
                events.forEach { event ->
                    database.blackBoxDatabaseQueries.insertEvent(
                        timestamp = event.timestamp,
                        event_type = event.eventType.name,
                        description = event.description,
                        place_id = event.placeId,
                        metadata_json = event.metadataJson,
                        confidence = event.confidence.toDouble(),
                        created_at = event.createdAt,
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TimelineRepository"
    }
}
