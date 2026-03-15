package com.blackbox.domain.repository

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing known (learned) places.
 */
interface PlaceRepository {

    /** Retrieves all visible known places, ordered by visit count. */
    suspend fun getAllPlaces(): List<KnownPlace>

    /** Finds the nearest known place to the given coordinates. */
    suspend fun findNearestPlace(latitude: Double, longitude: Double): KnownPlace?

    /**
     * Finds a known place whose WiFi fingerprint contains [bssid].
     *
     * Used as an indoor positioning fallback when GPS accuracy is insufficient
     * to distinguish between nearby places.
     */
    suspend fun findPlaceByWifiBssid(bssid: String): KnownPlace?

    /** Retrieves places by category. */
    suspend fun getPlacesByCategory(category: PlaceCategory): List<KnownPlace>

    /** Retrieves a single place by its ID. */
    suspend fun getPlaceById(id: Long): KnownPlace?

    /** Saves a new known place. Returns the generated ID. */
    suspend fun savePlace(place: KnownPlace): Long

    /** Updates an existing known place. */
    suspend fun updatePlace(place: KnownPlace)

    /** Increments the visit count and updates last visit timestamp. */
    suspend fun incrementVisitCount(placeId: Long, visitTimestamp: Long)

    /** Hides a place from the UI without deleting it. */
    suspend fun hidePlace(placeId: Long)

    /** Deletes a place permanently. */
    suspend fun deletePlace(placeId: Long)

    /** Observes all visible places as a reactive flow. */
    fun observePlaces(): Flow<List<KnownPlace>>
}
