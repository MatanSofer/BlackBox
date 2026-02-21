package com.blackbox.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.blackbox.data.database.BlackBoxDatabase
import com.blackbox.data.mapper.LocationMapper
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [LocationRepository].
 *
 * Operates on the denormalized LocationRecord table for fast
 * spatial and temporal location queries. All database operations
 * run on [Dispatchers.IO].
 */
class LocationRepositoryImpl(
    private val database: BlackBoxDatabase,
    private val logger: BlackBoxLogger,
) : LocationRepository {

    override suspend fun getLocationsInRange(startTime: Long, endTime: Long): List<LocationEntry> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching locations: $startTime..$endTime")
            database.blackBoxDatabaseQueries
                .getLocationsInRange(startTime, endTime)
                .executeAsList()
                .map(LocationMapper::toDomain)
        }
    }

    override suspend fun getLastKnownLocation(): LocationEntry? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getLastKnownLocation()
                .executeAsOneOrNull()
                ?.let(LocationMapper::toDomain)
        }
    }

    override suspend fun getLocationAt(timestamp: Long, toleranceMs: Long): LocationEntry? {
        return withContext(Dispatchers.IO) {
            database.blackBoxDatabaseQueries
                .getLocationAt(
                    timestamp,   // ? - ?  (first ?)
                    toleranceMs, // ? - ?  (second ?)
                    timestamp,   // ? + ?  (first ?)
                    toleranceMs, // ? + ?  (second ?)
                    timestamp,   // ABS(timestamp - ?)
                )
                .executeAsOneOrNull()
                ?.let(LocationMapper::toDomain)
        }
    }

    override suspend fun getLocationsInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        startTime: Long,
        endTime: Long,
    ): List<LocationEntry> {
        return withContext(Dispatchers.IO) {
            logger.d(TAG, "Fetching locations in bounding box")
            database.blackBoxDatabaseQueries
                .getLocationsInBoundingBox(minLat, maxLat, minLng, maxLng, startTime, endTime)
                .executeAsList()
                .map(LocationMapper::toDomain)
        }
    }

    override fun observeLocations(startTime: Long, endTime: Long): Flow<List<LocationEntry>> {
        return database.blackBoxDatabaseQueries
            .getLocationsInRange(startTime, endTime)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map(LocationMapper::toDomain) }
    }

    override suspend fun saveLocation(location: LocationEntry) {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Saving location record")
            database.blackBoxDatabaseQueries.insertLocation(
                record_id = location.recordId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy_meters = location.accuracyMeters?.toDouble(),
                speed = location.speed?.toDouble(),
                bearing = location.bearing?.toDouble(),
                source = location.source,
                timestamp = location.timestamp,
            )
        }
    }

    override suspend fun deleteLocationsInRange(startTime: Long, endTime: Long) {
        withContext(Dispatchers.IO) {
            logger.i(TAG, "Deleting locations: $startTime..$endTime")
            database.blackBoxDatabaseQueries.deleteLocationsInRange(startTime, endTime)
        }
    }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
