package com.blackbox.domain.usecase.place

import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Permanently deletes a known place by its ID.
 *
 * @property placeRepository Repository for known places.
 * @property logger Logger for operation tracking.
 */
class DeletePlaceUseCase(
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Deletes the place with the given [placeId].
     *
     * @param placeId The database ID of the place to delete.
     * @return [Result] containing Unit on success or an error.
     */
    suspend operator fun invoke(placeId: Long): Result<Unit> {
        return runCatching {
            logger.i(TAG, "Deleting place: placeId=$placeId")
            placeRepository.deletePlace(placeId)
        }
    }

    companion object {
        private const val TAG = "DeletePlaceUseCase"
    }
}
