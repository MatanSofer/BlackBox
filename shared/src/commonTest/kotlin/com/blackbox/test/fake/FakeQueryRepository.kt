package com.blackbox.test.fake

import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.repository.RecentQuery

class FakeQueryRepository : QueryRepository {

    val savedQueries = mutableListOf<RecentQuery>()
    var queriesToReturn: List<RecentQuery> = emptyList()
    var shouldThrow: Throwable? = null

    override suspend fun getRecentQueries(limit: Int): List<RecentQuery> {
        shouldThrow?.let { throw it }
        return queriesToReturn.take(limit)
    }

    override suspend fun saveQuery(queryText: String, intentType: String?, executedAt: Long) {
        shouldThrow?.let { throw it }
        savedQueries.add(RecentQuery(queryText = queryText, intentType = intentType, executedAt = executedAt))
    }

    override suspend fun cleanupOldQueries(keepCount: Int) {
        // no-op in tests
    }
}
