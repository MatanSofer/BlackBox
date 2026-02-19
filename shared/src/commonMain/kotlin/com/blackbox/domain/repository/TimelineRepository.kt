package com.blackbox.domain.repository

import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.model.timeline.DerivedEvent
import com.blackbox.domain.model.timeline.EventType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for timeline data including daily summaries and derived events.
 */
interface TimelineRepository {

    // ── Daily Summaries ──

    /** Retrieves the pre-computed summary for a specific date (ISO format). */
    suspend fun getSummaryForDate(date: String): DailySummary?

    /** Retrieves summaries for a date range (ISO format), ordered by date. */
    suspend fun getSummariesInRange(startDate: String, endDate: String): List<DailySummary>

    /** Saves or updates a daily summary. */
    suspend fun saveSummary(summary: DailySummary)

    /** Observes the summary for a specific date reactively. */
    fun observeSummary(date: String): Flow<DailySummary?>

    // ── Derived Events ──

    /** Retrieves all derived events within a time range. */
    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<DerivedEvent>

    /** Retrieves arrival/departure events for a specific place. */
    suspend fun getPlaceEvents(placeId: Long, limit: Int = 20): List<DerivedEvent>

    /** Returns the most recent event of a specific type. */
    suspend fun getLatestEventOfType(eventType: EventType): DerivedEvent?

    /** Retrieves sleep events within a time range. */
    suspend fun getSleepEvents(startTime: Long, endTime: Long): List<DerivedEvent>

    /** Saves a new derived event. */
    suspend fun saveEvent(event: DerivedEvent)

    /** Saves multiple derived events in a batch. */
    suspend fun saveEvents(events: List<DerivedEvent>)
}
