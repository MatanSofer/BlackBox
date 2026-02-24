package com.blackbox.test.fake

import com.blackbox.domain.model.timeline.DailySummary
import com.blackbox.domain.model.timeline.DerivedEvent
import com.blackbox.domain.model.timeline.EventType
import com.blackbox.domain.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeTimelineRepository : TimelineRepository {

    val savedSummaries = mutableListOf<DailySummary>()
    val savedEvents = mutableListOf<DerivedEvent>()
    var eventsToReturn: List<DerivedEvent> = emptyList()
    var summariesToReturn: Map<String, DailySummary> = emptyMap()
    var shouldThrow: Throwable? = null

    override suspend fun getSummaryForDate(date: String): DailySummary? {
        return summariesToReturn[date]
    }

    override suspend fun getSummariesInRange(startDate: String, endDate: String): List<DailySummary> {
        return summariesToReturn.values.toList()
    }

    override suspend fun saveSummary(summary: DailySummary) {
        shouldThrow?.let { throw it }
        savedSummaries.add(summary)
    }

    override fun observeSummary(date: String): Flow<DailySummary?> {
        return flowOf(summariesToReturn[date])
    }

    override suspend fun getEventsInRange(startTime: Long, endTime: Long): List<DerivedEvent> {
        return eventsToReturn.filter { it.timestamp in startTime..endTime }
    }

    override suspend fun getPlaceEvents(placeId: Long, limit: Int): List<DerivedEvent> {
        return eventsToReturn.filter { it.placeId == placeId }.take(limit)
    }

    override suspend fun getLatestEventOfType(eventType: EventType): DerivedEvent? {
        return eventsToReturn.filter { it.eventType == eventType }.maxByOrNull { it.timestamp }
    }

    override suspend fun getSleepEvents(startTime: Long, endTime: Long): List<DerivedEvent> {
        return eventsToReturn.filter {
            it.eventType == EventType.SLEEP_START || it.eventType == EventType.SLEEP_END
        }
    }

    override suspend fun saveEvent(event: DerivedEvent) {
        savedEvents.add(event)
    }

    override suspend fun saveEvents(events: List<DerivedEvent>) {
        savedEvents.addAll(events)
    }
}
