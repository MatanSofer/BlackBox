package com.blackbox.domain.model.timeline

/**
 * A significant event derived from analyzing raw collected data.
 *
 * Derived events are computed by pattern detection algorithms
 * that analyze location transitions, activity changes, and
 * sensor patterns to identify meaningful moments.
 *
 * @property id Database primary key (0 for unsaved events).
 * @property timestamp When this event occurred (epoch ms).
 * @property eventType Classification of this event.
 * @property description Human-readable description (e.g., "Arrived at Office").
 * @property placeId Optional reference to the [KnownPlace] involved.
 * @property placeName Display name of the associated place (joined from KnownPlace).
 * @property placeCategory Category of the associated place.
 * @property metadataJson JSON object with event-specific context.
 * @property confidence Confidence that this event occurred (0.0 to 1.0).
 * @property createdAt When this event was generated (epoch ms).
 */
data class DerivedEvent(
    val id: Long = 0,
    val timestamp: Long,
    val eventType: EventType,
    val description: String,
    val placeId: Long? = null,
    val placeName: String? = null,
    val placeCategory: String? = null,
    val metadataJson: String? = null,
    val confidence: Float = 1.0f,
    val createdAt: Long = 0,
)
