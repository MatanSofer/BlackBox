package com.blackbox.domain.usecase.query

import com.blackbox.domain.repository.RecentQuery
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakeQueryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecentQueriesUseCaseTest {

    private val repository = FakeQueryRepository()
    private val useCase = GetRecentQueriesUseCase(repository, FakeLogger())

    @Test
    fun `invoke — returns recent queries`() = runTest {
        repository.queriesToReturn = listOf(
            RecentQuery(id = 1, queryText = "where was I", executedAt = 100L),
            RecentQuery(id = 2, queryText = "what was I doing", executedAt = 200L),
        )

        val result = useCase()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `invoke with limit — respects limit`() = runTest {
        repository.queriesToReturn = List(30) {
            RecentQuery(id = it.toLong(), queryText = "query $it", executedAt = it.toLong())
        }

        val result = useCase(limit = 5)
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrThrow().size)
    }

    @Test
    fun `invoke with empty history — returns empty list`() = runTest {
        repository.queriesToReturn = emptyList()
        val result = useCase()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `invoke when repository throws — returns failure`() = runTest {
        repository.shouldThrow = RuntimeException("DB error")
        val result = useCase()
        assertTrue(result.isFailure)
    }
}
