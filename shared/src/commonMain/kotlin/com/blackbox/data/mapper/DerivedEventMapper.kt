package com.blackbox.data.mapper

import com.blackbox.domain.model.timeline.EventType
import com.blackbox.data.database.DerivedEvent as DbEvent
import com.blackbox.data.database.GetEventsInRange
import com.blackbox.data.database.GetLatestEventOfType
import com.blackbox.domain.model.timeline.DerivedEvent as DomainEvent

/**
 * Maps between SQLDelight derived event rows and domain [DomainEvent].
 *
 * Handles the different result types from queries with/without JOINs.
 */
object DerivedEventMapper {

    /**
     * Converts a plain [DbEvent] row (no JOIN) to a domain [DomainEvent].
     */
    fun toDomain(db: DbEvent): DomainEvent = DomainEvent(
        id = db.id,
        timestamp = db.timestamp,
        eventType = EventType.valueOf(db.event_type),
        description = db.description,
        placeId = db.place_id,
        placeName = null,
        placeCategory = null,
        metadataJson = db.metadata_json,
        confidence = db.confidence.toFloat(),
        createdAt = db.created_at,
    )

    /**
     * Converts a [GetEventsInRange] row (with JOIN) to a domain [DomainEvent].
     */
    fun toDomain(db: GetEventsInRange): DomainEvent = DomainEvent(
        id = db.id,
        timestamp = db.timestamp,
        eventType = EventType.valueOf(db.event_type),
        description = db.description,
        placeId = db.place_id,
        placeName = db.place_name,
        placeCategory = db.place_category,
        metadataJson = db.metadata_json,
        confidence = db.confidence.toFloat(),
        createdAt = db.created_at,
    )

    /**
     * Converts a [GetLatestEventOfType] row (with JOIN) to a domain [DomainEvent].
     */
    fun toDomain(db: GetLatestEventOfType): DomainEvent = DomainEvent(
        id = db.id,
        timestamp = db.timestamp,
        eventType = EventType.valueOf(db.event_type),
        description = db.description,
        placeId = db.place_id,
        placeName = db.place_name,
        placeCategory = db.place_category,
        metadataJson = db.metadata_json,
        confidence = db.confidence.toFloat(),
        createdAt = db.created_at,
    )
}
