package com.blackbox.test.fake

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePlaceRepository : PlaceRepository {

    val savedPlaces = mutableListOf<KnownPlace>()
    var placesToReturn: List<KnownPlace> = emptyList()
    var nearestPlace: KnownPlace? = null
    var nextId: Long = 1L
    val visitIncrements = mutableListOf<Pair<Long, Long>>()
    var shouldThrow: Throwable? = null

    override suspend fun getAllPlaces(): List<KnownPlace> {
        shouldThrow?.let { throw it }
        return placesToReturn
    }

    override suspend fun findNearestPlace(latitude: Double, longitude: Double): KnownPlace? {
        return nearestPlace
    }

    override suspend fun getPlacesByCategory(category: PlaceCategory): List<KnownPlace> {
        shouldThrow?.let { throw it }
        return placesToReturn.filter { it.category == category }
    }

    override suspend fun getPlaceById(id: Long): KnownPlace? {
        return placesToReturn.find { it.id == id }
    }

    override suspend fun savePlace(place: KnownPlace): Long {
        shouldThrow?.let { throw it }
        savedPlaces.add(place)
        return nextId++
    }

    override suspend fun updatePlace(place: KnownPlace) {
        shouldThrow?.let { throw it }
    }

    override suspend fun incrementVisitCount(placeId: Long, visitTimestamp: Long) {
        visitIncrements.add(placeId to visitTimestamp)
    }

    override suspend fun hidePlace(placeId: Long) {}

    override suspend fun deletePlace(placeId: Long) {}

    override fun observePlaces(): Flow<List<KnownPlace>> {
        return flowOf(placesToReturn)
    }
}
