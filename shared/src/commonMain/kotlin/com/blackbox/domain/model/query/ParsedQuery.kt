package com.blackbox.domain.model.query

/**
 * A fully parsed natural language query.
 *
 * Produced by the query engine pipeline after preprocessing,
 * time expression parsing, intent classification, and entity extraction.
 * Contains all information needed to build and execute database queries.
 *
 * @property originalText The raw query text as entered by the user.
 * @property normalizedText Cleaned and normalized query text.
 * @property language Detected query language.
 * @property intent Classified query intent.
 * @property timeRange Extracted time range (defaults to today if unspecified).
 * @property entities Extracted entities (places, activities, apps).
 */
data class ParsedQuery(
    val originalText: String,
    val normalizedText: String,
    val language: Language,
    val intent: QueryIntent,
    val timeRange: TimeRange,
    val entities: List<QueryEntity> = emptyList(),
)
