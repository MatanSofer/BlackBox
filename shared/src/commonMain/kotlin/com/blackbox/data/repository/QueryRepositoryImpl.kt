package com.blackbox.data.repository

import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.repository.RecentQuery
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [QueryRepository].
 *
 * Manages the user's recent search queries for auto-complete
 * and query history features. All database operations run
 * on [Dispatchers.IO].
 */
class QueryRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : QueryRepository {

    override suspend fun getRecentQueries(limit: Int): List<RecentQuery> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching recent queries: limit=$limit")
            database.blackBoxDatabaseQueries
                .getRecentQueries(limit.toLong())
                .executeAsList()
                .map { row ->
                    RecentQuery(
                        id = row.id,
                        queryText = row.query_text,
                        intentType = row.intent_type,
                        executedAt = row.executed_at,
                    )
                }
        }
    }

    override suspend fun saveQuery(queryText: String, intentType: String?, executedAt: Long) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving query: ${queryText.take(30)}")
            database.blackBoxDatabaseQueries.insertQuery(
                query_text = queryText,
                intent_type = intentType,
                executed_at = executedAt,
            )
        }
    }

    override suspend fun cleanupOldQueries(keepCount: Int) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Cleaning up queries, keeping $keepCount")
            database.blackBoxDatabaseQueries.cleanupOldQueries(keepCount.toLong())
        }
    }

    companion object {
        private const val TAG = "QueryRepository"
    }
}
