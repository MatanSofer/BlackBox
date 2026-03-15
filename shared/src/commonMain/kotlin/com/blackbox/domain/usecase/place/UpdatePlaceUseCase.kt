package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Updates an existing known place (name, category, and radius).
 *
 * Coordinates are intentionally NOT changed here — moving a place requires
 * deleting and re-creating it to avoid invalidating historical visits.
 *
 * @property placeRepository Repository for known places.
 * @property logger Logger for operation tracking.
 */
class UpdatePlaceUseCase(
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Persists the updated [place] to the repository.
     *
     * @param place The place with updated fields (id must match an existing entry).
     * @return [Result] containing Unit on success, or an error on failure.
     */
    suspend operator fun invoke(place: KnownPlace): Result<Unit> = runCatching {
        logger.i(TAG, "Updating place: id=${place.id} name=${place.name}")
        placeRepository.updatePlace(place)
    }

    companion object {
        private const val TAG = "UpdatePlaceUseCase"
    }
}
