package com.blackbox.domain.model.query

/**
 * An entity extracted from a natural language query.
 *
 * Entities are specific nouns or references identified by the
 * [EntityExtractor] that help scope the query (e.g., a place name,
 * an activity type, or an app name).
 *
 * @property type The category of this entity.
 * @property value The raw text as it appeared in the query.
 * @property normalizedValue Normalized form for matching against stored data.
 */
data class QueryEntity(
    val type: EntityType,
    val value: String,
    val normalizedValue: String = value,
)

/**
 * Categories of entities that can be extracted from queries.
 */
enum class EntityType {
    /** A named location (e.g., "office", "home", "gym"). */
    PLACE_NAME,
    /** A physical activity (e.g., "walking", "driving"). */
    ACTIVITY_TYPE,
    /** An application name (e.g., "WhatsApp", "Chrome"). */
    APP_NAME,
    /** A time reference already parsed by the time expression parser. */
    TIME_REFERENCE,
}
