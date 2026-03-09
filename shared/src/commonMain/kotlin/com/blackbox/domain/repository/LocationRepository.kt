package com.blackbox.domain.repository

import com.blackbox.domain.model.record.LocationData
import kotlinx.coroutines.flow.Flow

/**
 * Repository for location-specific queries.
 *
 * Operates on the denormalized LocationRecord table for fast
 * spatial and temporal queries. Each location record corresponds
 * to a BlackBoxRecord with collector_type = LOCATION.
 */
interface LocationRepository {

    /** Retrieves all locations within a time range, ordered by timestamp. */
    suspend fun getLocationsInRange(startTime: Long, endTime: Long): List<LocationEntry>

    /** Returns the most recent location recorded. */
    suspend fun getLastKnownLocation(): LocationEntry?

    /**
     * Finds the closest location to a specific timestamp.
     *
     * @param timestamp Target time in epoch ms.
     * @param toleranceMs Maximum time difference to consider (default 5 minutes).
     */
    suspend fun getLocationAt(timestamp: Long, toleranceMs: Long = 300_000): LocationEntry?

    /**
     * Retrieves locations within a geographic bounding box and time range.
     * Used for map view rendering.
     */
    suspend fun getLocationsInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        startTime: Long,
        endTime: Long,
    ): List<LocationEntry>

    /** Observes location changes within a time range as a reactive flow. */
    fun observeLocations(startTime: Long, endTime: Long): Flow<List<LocationEntry>>

    /** Saves a location entry to the denormalized table. */
    suspend fun saveLocation(location: LocationEntry)

    /** Deletes all location entries within a time range. */
    suspend fun deleteLocationsInRange(startTime: Long, endTime: Long)

    /**
     * Returns all location entries that have no reverse-geocoded address yet.
     * Used by [GeocodingRetryWorker] to backfill addresses once internet is available.
     */
    suspend fun getLocationsWithoutAddress(): List<LocationEntry>

    /**
     * Updates the address for a location entry and keeps the master record's
     * data_json in sync so query results reflect the resolved name.
     *
     * @param id Primary key of the [LocationRecord] row to update.
     * @param recordId Primary key of the parent [BlackBoxRecord] row.
     * @param address The resolved human-readable address to store.
     */
    suspend fun updateLocationAddress(id: Long, recordId: Long, address: String)
}

/**
 * A denormalized location entry for fast spatial/temporal queries.
 *
 * Combines the location data with database metadata. Stored in
 * the LocationRecord table alongside the master BlackBoxRecord.
 *
 * @property id Database primary key.
 * @property recordId Reference to the parent BlackBoxRecord.
 * @property latitude WGS84 latitude.
 * @property longitude WGS84 longitude.
 * @property altitude Meters above sea level.
 * @property accuracyMeters GPS horizontal accuracy radius.
 * @property speed Speed in m/s.
 * @property bearing Compass bearing in degrees.
 * @property source How this location was determined.
 * @property timestamp When this location was captured (epoch ms).
 * @property address Reverse-geocoded human-readable address (null if unavailable).
 */
data class LocationEntry(
    val id: Long = 0,
    val recordId: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val source: String = "FUSED",
    val timestamp: Long,
    val address: String? = null,
)
