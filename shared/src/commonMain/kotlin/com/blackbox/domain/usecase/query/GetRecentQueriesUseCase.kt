package com.blackbox.domain.usecase.query

import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.repository.RecentQuery
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Retrieves the user's recent search query history.
 *
 * @property queryRepository Repository for query history.
 * @property logger Logger for operation tracking.
 */
class GetRecentQueriesUseCase(
    private val queryRepository: QueryRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Retrieves recent queries ordered by most recent first.
     *
     * @param limit Maximum number of queries to return.
     * @return [Result] containing the list of recent queries.
     */
    suspend operator fun invoke(limit: Int = 20): Result<List<RecentQuery>> {
        return runCatching {
            logger.d(TAG, "Fetching recent queries (limit=$limit)")
            queryRepository.getRecentQueries(limit)
        }
    }

    companion object {
        private const val TAG = "GetRecentQueriesUseCase"
    }
}
