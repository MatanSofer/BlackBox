package com.blackbox.domain.usecase.map

import com.blackbox.domain.model.map.DayLocationSummary
import com.blackbox.domain.model.map.LocationStay
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds a [DayLocationSummary] for a given day by clustering raw GPS fixes
 * into meaningful stays and enriching them with known place names.
 *
 * Algorithm:
 * 1. Fetch all [LocationEntry] records for the day from [locationRepository].
 * 2. Walk the list chronologically; accumulate a cluster until the next fix is
 *    more than [STAY_RADIUS_METERS] from the current cluster centroid.
 * 3. Each completed cluster becomes a [LocationStay] whose centroid is the mean
 *    of all fix coordinates. Arrival = earliest fix, departure = latest fix.
 * 4. Attempt to match each stay centroid to a [com.blackbox.domain.model.place.KnownPlace]
 *    via [placeRepository].
 * 5. Compute total displacement as the sum of haversine distances between
 *    consecutive stay centroids.
 *
 * @property locationRepository Source of raw GPS fixes.
 * @property placeRepository Source of known place definitions for labelling stays.
 * @property logger Logger for operation tracking.
 */
class GetDayLocationSummaryUseCase(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Builds the day summary for the given time window.
     *
     * @param date Human-readable date label stored in the result ("yyyy-MM-dd").
     * @param startTime Start of the day in epoch ms.
     * @param endTime End of the day in epoch ms.
     * @return [Result] containing a [DayLocationSummary] (may have empty stays).
     */
    suspend operator fun invoke(
        date: String,
        startTime: Long,
        endTime: Long,
    ): Result<DayLocationSummary> = runCatching {
        logger.d(TAG, "Building day location summary for $date")

        val locations = locationRepository.getLocationsInRange(startTime, endTime)

        if (locations.isEmpty()) {
            logger.d(TAG, "No locations for $date")
            return@runCatching DayLocationSummary(
                date = date,
                stays = emptyList(),
                totalDistanceMeters = 0.0,
                firstFixTime = null,
                lastFixTime = null,
            )
        }

        val stays = clusterIntoStays(locations)
        val totalDistance = computeTotalDistance(stays)

        logger.d(TAG, "Built ${stays.size} stays, total distance=${totalDistance.toInt()}m")

        DayLocationSummary(
            date = date,
            stays = stays,
            totalDistanceMeters = totalDistance,
            firstFixTime = locations.minOf { it.timestamp },
            lastFixTime = locations.maxOf { it.timestamp },
        )
    }

    /**
     * Returns a [Flow] that re-emits a fresh [DayLocationSummary] whenever the
     * underlying location data changes (e.g. the background service writes new fixes).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        date: String,
        startTime: Long,
        endTime: Long,
    ): Flow<Result<DayLocationSummary>> =
        locationRepository.observeLocations(startTime, endTime)
            .flatMapLatest { flow { emit(invoke(date, startTime, endTime)) } }

    // ── Clustering ────────────────────────────────────────────────────────────

    private suspend fun clusterIntoStays(locations: List<LocationEntry>): List<LocationStay> {
        val stays = mutableListOf<LocationStay>()
        var cluster = mutableListOf(locations.first())

        for (i in 1 until locations.size) {
            val current = locations[i]
            val (cLat, cLng) = cluster.centroid()
            val dist = haversineDistance(cLat, cLng, current.latitude, current.longitude)

            if (dist < STAY_RADIUS_METERS) {
                cluster.add(current)
            } else {
                stays.add(buildStay(cluster))
                cluster = mutableListOf(current)
            }
        }
        stays.add(buildStay(cluster))

        return stays
    }

    private suspend fun buildStay(points: List<LocationEntry>): LocationStay {
        val (lat, lng) = points.centroid()
        val knownPlace = placeRepository.findNearestPlace(lat, lng)
        val avgAccuracy = points.mapNotNull { it.accuracyMeters }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat()

        return LocationStay(
            latitude = lat,
            longitude = lng,
            arrivalTime = points.minOf { it.timestamp },
            departureTime = points.maxOf { it.timestamp },
            pointCount = points.size,
            averageAccuracyMeters = avgAccuracy,
            knownPlace = knownPlace,
        )
    }

    // ── Distance ──────────────────────────────────────────────────────────────

    private fun computeTotalDistance(stays: List<LocationStay>): Double {
        if (stays.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until stays.size) {
            total += haversineDistance(
                stays[i - 1].latitude, stays[i - 1].longitude,
                stays[i].latitude, stays[i].longitude,
            )
        }
        return total
    }

    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun List<LocationEntry>.centroid(): Pair<Double, Double> =
        (sumOf { it.latitude } / size) to (sumOf { it.longitude } / size)

    companion object {
        private const val TAG = "GetDayLocationSummaryUseCase"

        /** Fixes within this radius are merged into the same stay cluster. */
        private const val STAY_RADIUS_METERS = 100.0
    }
}
