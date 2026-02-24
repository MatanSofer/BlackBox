package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakePlaceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetPlacesUseCaseTest {

    private val repository = FakePlaceRepository()
    private val useCase = GetPlacesUseCase(repository, FakeLogger())

    private val home = KnownPlace(
        id = 1, name = "Home", latitude = 32.0, longitude = 34.0,
        category = PlaceCategory.HOME,
    )
    private val office = KnownPlace(
        id = 2, name = "Office", latitude = 32.1, longitude = 34.1,
        category = PlaceCategory.WORK,
    )
    private val gym = KnownPlace(
        id = 3, name = "Gym", latitude = 32.2, longitude = 34.2,
        category = PlaceCategory.GYM,
    )

    @Test
    fun `invoke — returns all places`() = runTest {
        repository.placesToReturn = listOf(home, office, gym)
        val result = useCase()
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().size)
    }

    @Test
    fun `invoke with empty places — returns empty list`() = runTest {
        repository.placesToReturn = emptyList()
        val result = useCase()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `byCategory — returns only matching category`() = runTest {
        repository.placesToReturn = listOf(home, office, gym)
        val result = useCase.byCategory(PlaceCategory.WORK)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("Office", result.getOrThrow().first().name)
    }

    @Test
    fun `byCategory with no matches — returns empty list`() = runTest {
        repository.placesToReturn = listOf(home)
        val result = useCase.byCategory(PlaceCategory.ENTERTAINMENT)
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
