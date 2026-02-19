package com.blackbox.domain.repository

/**
 * Repository for managing the user's recent search queries.
 */
interface QueryRepository {

    /** Retrieves recent queries ordered by most recent first. */
    suspend fun getRecentQueries(limit: Int = 20): List<RecentQuery>

    /** Saves a new query to the recent queries list. */
    suspend fun saveQuery(queryText: String, intentType: String?, executedAt: Long)

    /** Removes old queries, keeping only the most recent [keepCount]. */
    suspend fun cleanupOldQueries(keepCount: Int = 50)
}

/**
 * A previously executed search query.
 *
 * @property id Database primary key.
 * @property queryText The raw query text as typed by the user.
 * @property intentType The parsed intent type for categorizing suggestions.
 * @property executedAt When this query was last executed (epoch ms).
 */
data class RecentQuery(
    val id: Long = 0,
    val queryText: String,
    val intentType: String? = null,
    val executedAt: Long,
)
