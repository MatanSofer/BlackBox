package com.blackbox.domain.model.timeline

import com.blackbox.domain.model.place.KnownPlace

/**
 * A single entry in the user's daily timeline.
 *
 * Built by the timeline use case from raw records and derived events.
 * Represents a time segment such as a location stay, transit,
 * activity period, or a point event.
 *
 * @property id Unique identifier for this timeline entry.
 * @property startTimestamp Start of this entry (epoch ms).
 * @property endTimestamp End of this entry (epoch ms), null for point events.
 * @property type Classification of this timeline segment.
 * @property title Primary display text (e.g., "At Office", "Driving").
 * @property subtitle Secondary text with details (e.g., "Duration: 3h 45m").
 * @property iconType Icon hint for the UI layer.
 * @property place Associated known place, if applicable.
 * @property metadata Additional key-value data for display.
 */
data class TimelineEntry(
    val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val type: TimelineEntryType,
    val title: String,
    val subtitle: String? = null,
    val iconType: String? = null,
    val place: KnownPlace? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Classification of timeline entry segments.
 */
enum class TimelineEntryType {
    /** User stayed at a known or unknown location. */
    LOCATION_STAY,
    /** User was in transit (driving, walking, cycling, etc.). */
    TRANSIT,
    /** A point-in-time event (arrival, departure, pickup). */
    EVENT,
    /** A physical activity period (walking, running). */
    ACTIVITY,
    /** Sleep period. */
    SLEEP,
}
