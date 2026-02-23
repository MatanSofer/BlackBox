package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Retrieves known places with optional category filtering.
 *
 * @property placeRepository Repository for known places.
 * @property logger Logger for operation tracking.
 */
class GetPlacesUseCase(
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Retrieves all visible known places.
     *
     * @return [Result] containing the list of known places.
     */
    suspend operator fun invoke(): Result<List<KnownPlace>> {
        return runCatching {
            logger.d(TAG, "Fetching all known places")
            placeRepository.getAllPlaces()
        }
    }

    /**
     * Retrieves places filtered by category.
     *
     * @param category The place category to filter by.
     * @return [Result] containing the filtered list of places.
     */
    suspend fun byCategory(category: PlaceCategory): Result<List<KnownPlace>> {
        return runCatching {
            logger.d(TAG, "Fetching places by category: $category")
            placeRepository.getPlacesByCategory(category)
        }
    }

    companion object {
        private const val TAG = "GetPlacesUseCase"
    }
}
