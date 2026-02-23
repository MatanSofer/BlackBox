package com.blackbox.domain.usecase.query

import com.blackbox.domain.model.query.QueryResult
import com.blackbox.domain.query.QueryEngine
import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Processes a natural language query and returns structured results.
 *
 * Steps:
 * 1. Delegate to [QueryEngine] for full NLP pipeline execution
 * 2. Save the query to recent history via [QueryRepository]
 * 3. Return the query result
 *
 * @property queryEngine The NLP query engine for parsing and executing.
 * @property queryRepository Repository for persisting query history.
 * @property logger Logger for operation tracking.
 */
class ProcessQueryUseCase(
    private val queryEngine: QueryEngine,
    private val queryRepository: QueryRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Processes a raw natural language query.
     *
     * @param query The raw query text as entered by the user.
     * @return [Result] containing [QueryResult] on success or an error.
     */
    suspend operator fun invoke(query: String): Result<QueryResult> {
        return runCatching {
            require(query.isNotBlank()) { "Query must not be blank" }
            logger.d(TAG, "Processing query: \"$query\"")

            val result = queryEngine.process(query)

            queryRepository.saveQuery(
                queryText = query,
                intentType = result.parsedQuery.intent.name,
                executedAt = System.currentTimeMillis(),
            )

            logger.d(TAG, "Query processed: intent=${result.parsedQuery.intent}, records=${result.data.size}")
            result
        }
    }

    companion object {
        private const val TAG = "ProcessQueryUseCase"
    }
}
