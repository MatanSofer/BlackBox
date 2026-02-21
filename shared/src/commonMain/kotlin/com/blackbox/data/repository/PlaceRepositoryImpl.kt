package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.PlaceMapper
import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [PlaceRepository].
 *
 * Manages known places detected via location clustering or
 * created manually by the user. All database operations run
 * on [Dispatchers.IO].
 */
class PlaceRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : PlaceRepository {

    override suspend fun getAllPlaces(): List<KnownPlace> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching all visible places")
            database.blackBoxDatabaseQueries
                .getAllPlaces()
                .executeAsList()
                .map(PlaceMapper::toDomain)
        }
    }

    override suspend fun findNearestPlace(latitude: Double, longitude: Double): KnownPlace? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .findNearestPlace(latitude, latitude, longitude, longitude)
                .executeAsOneOrNull()
                ?.let(PlaceMapper::toDomain)
        }
    }

    override suspend fun getPlacesByCategory(category: PlaceCategory): List<KnownPlace> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching places by category: ${category.name}")
            database.blackBoxDatabaseQueries
                .getPlacesByCategory(category.name)
                .executeAsList()
                .map(PlaceMapper::toDomain)
        }
    }

    override suspend fun getPlaceById(id: Long): KnownPlace? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getPlaceById(id)
                .executeAsOneOrNull()
                ?.let(PlaceMapper::toDomain)
        }
    }

    override suspend fun savePlace(place: KnownPlace): Long {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving place: ${place.name}")
            database.blackBoxDatabaseQueries.insertPlace(
                name = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                radius_meters = place.radiusMeters,
                wifi_fingerprint = PlaceMapper.serializeWifiFingerprint(place.wifiFingerprint),
                category = place.category.name,
                visit_count = place.visitCount.toLong(),
                total_time_minutes = place.totalTimeMinutes.toLong(),
                first_visit = place.firstVisit,
                last_visit = place.lastVisit,
                is_auto_detected = if (place.isAutoDetected) 1L else 0L,
                is_hidden = if (place.isHidden) 1L else 0L,
                created_at = place.createdAt,
                updated_at = place.updatedAt,
            )
            database.blackBoxDatabaseQueries
                .lastInsertPlaceId()
                .executeAsOne()
        }
    }

    override suspend fun updatePlace(place: KnownPlace) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Updating place: id=${place.id}, name=${place.name}")
            database.blackBoxDatabaseQueries.updatePlace(
                name = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                radius_meters = place.radiusMeters,
                wifi_fingerprint = PlaceMapper.serializeWifiFingerprint(place.wifiFingerprint),
                category = place.category.name,
                is_auto_detected = if (place.isAutoDetected) 1L else 0L,
                is_hidden = if (place.isHidden) 1L else 0L,
                updated_at = place.updatedAt,
                id = place.id,
            )
        }
    }

    override suspend fun incrementVisitCount(placeId: Long, visitTimestamp: Long) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Incrementing visit count: placeId=$placeId")
            database.blackBoxDatabaseQueries.incrementVisitCount(
                last_visit = visitTimestamp,
                updated_at = visitTimestamp,
                id = placeId,
            )
        }
    }

    override suspend fun hidePlace(placeId: Long) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Hiding place: placeId=$placeId")
            database.blackBoxDatabaseQueries.hidePlace(
                updated_at = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                id = placeId,
            )
        }
    }

    override suspend fun deletePlace(placeId: Long) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Deleting place: placeId=$placeId")
            database.blackBoxDatabaseQueries.deletePlace(placeId)
        }
    }

    override fun observePlaces(): Flow<List<KnownPlace>> {
        return database.blackBoxDatabaseQueries
            .getAllPlaces()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map(PlaceMapper::toDomain) }
    }

    companion object {
        private const val TAG = "PlaceRepository"
    }
}
