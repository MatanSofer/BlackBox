package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.test.fake.FakeLocationRepository
import com.blackbox.test.fake.FakeLogger
import com.blackbox.test.fake.FakePlaceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectKnownPlacesUseCaseTest {

    private val locationRepo = FakeLocationRepository()
    private val placeRepo = FakePlaceRepository()
    private val useCase = DetectKnownPlacesUseCase(locationRepo, placeRepo, FakeLogger())

    @Test
    fun `invoke with insufficient points — returns empty list`() = runTest {
        locationRepo.locationsToReturn = listOf(
            LocationEntry(latitude = 32.0, longitude = 34.0, timestamp = 100L),
            LocationEntry(latitude = 32.0, longitude = 34.0, timestamp = 200L),
        )
        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `invoke with clustered locations — detects place`() = runTest {
        // 5 points at same location (within 100m) = enough for detection
        locationRepo.locationsToReturn = (1..5).map {
            LocationEntry(
                latitude = 32.0800 + (it * 0.00001), // tiny offset, well within 100m
                longitude = 34.7800,
                timestamp = it.toLong() * 100,
            )
        }

        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertTrue(result.getOrThrow().first().isAutoDetected)
    }

    @Test
    fun `invoke with existing nearby place — increments visit count`() = runTest {
        locationRepo.locationsToReturn = (1..5).map {
            LocationEntry(
                latitude = 32.0800 + (it * 0.00001),
                longitude = 34.7800,
                timestamp = it.toLong() * 100,
            )
        }
        placeRepo.nearestPlace = KnownPlace(
            id = 1,
            name = "Office",
            latitude = 32.0800,
            longitude = 34.7800,
            radiusMeters = 200.0,
            category = PlaceCategory.WORK,
        )

        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        // Should increment, not create new
        assertEquals(1, placeRepo.visitIncrements.size)
        assertEquals(1L, placeRepo.visitIncrements.first().first)
    }

    @Test
    fun `invoke with no locations — returns empty list`() = runTest {
        locationRepo.locationsToReturn = emptyList()
        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `invoke with scattered locations — no cluster meets threshold`() = runTest {
        // 5 points but far apart (>100m each)
        locationRepo.locationsToReturn = listOf(
            LocationEntry(latitude = 32.0, longitude = 34.0, timestamp = 100L),
            LocationEntry(latitude = 32.1, longitude = 34.1, timestamp = 200L),
            LocationEntry(latitude = 32.2, longitude = 34.2, timestamp = 300L),
            LocationEntry(latitude = 32.3, longitude = 34.3, timestamp = 400L),
            LocationEntry(latitude = 32.4, longitude = 34.4, timestamp = 500L),
        )

        val result = useCase(0L, 1000L)
        assertTrue(result.isSuccess)
        // Each point is its own cluster of 1, none meets MIN_VISITS_FOR_PLACE (3)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
