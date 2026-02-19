package com.blackbox.domain.model.query

import com.blackbox.domain.model.record.CollectedRecord

/**
 * The result of processing a natural language query.
 *
 * Contains the parsed query, the retrieved data, a human-readable
 * response, and suggested follow-up queries.
 *
 * @property parsedQuery The parsed form of the original query.
 * @property responseText Human-readable answer to the query.
 * @property data Raw records matching the query criteria.
 * @property suggestedFollowUps Natural language follow-up query suggestions.
 * @property confidence Overall confidence score (0.0 to 1.0).
 * @property sourceCount Number of independent data sources corroborating the answer.
 */
data class QueryResult(
    val parsedQuery: ParsedQuery,
    val responseText: String,
    val data: List<CollectedRecord> = emptyList(),
    val suggestedFollowUps: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    val sourceCount: Int = 1,
)
