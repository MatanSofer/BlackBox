package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Saves a new known place created manually by the user.
 *
 * @property placeRepository Storage for known places.
 * @property logger Logger for operation tracking.
 */
class SavePlaceUseCase(
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {
    /**
     * Persists [place] to the database.
     *
     * @param place The [KnownPlace] to save.
     * @return [Result] containing the generated place ID on success.
     */
    suspend operator fun invoke(place: KnownPlace): Result<Long> = runCatching {
        logger.d(TAG, "Saving place '${place.name}' at (${place.latitude}, ${place.longitude})")
        placeRepository.savePlace(place)
    }

    companion object {
        private const val TAG = "SavePlaceUseCase"
    }
}
