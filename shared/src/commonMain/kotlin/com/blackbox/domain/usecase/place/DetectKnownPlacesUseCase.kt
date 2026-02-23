package com.blackbox.domain.usecase.place

import com.blackbox.domain.model.place.KnownPlace
import com.blackbox.domain.model.place.PlaceCategory
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.PlaceRepository
import com.blackbox.domain.util.BlackBoxLogger

/**
 * Detects known places by clustering frequently visited locations.
 *
 * Analyzes location history within a time range, groups nearby
 * points into clusters, and creates or updates [KnownPlace] entries
 * for frequently visited locations.
 *
 * @property locationRepository Source of raw location records.
 * @property placeRepository Storage for known places.
 * @property logger Logger for operation tracking.
 */
class DetectKnownPlacesUseCase(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Runs place detection on location data from the specified time range.
     *
     * @param startTimestamp Beginning of the analysis window (epoch ms).
     * @param endTimestamp End of the analysis window (epoch ms).
     * @return [Result] containing newly detected or updated [KnownPlace] entries.
     */
    suspend operator fun invoke(
        startTimestamp: Long,
        endTimestamp: Long,
    ): Result<List<KnownPlace>> {
        return runCatching {
            logger.d(TAG, "Detecting places: $startTimestamp..$endTimestamp")

            val locations = locationRepository.getLocationsInRange(startTimestamp, endTimestamp)
            if (locations.size < MIN_POINTS_FOR_CLUSTERING) {
                logger.d(TAG, "Insufficient location points (${locations.size}) for clustering")
                return@runCatching emptyList()
            }

            val clusters = clusterLocations(locations)
            logger.d(TAG, "Found ${clusters.size} location clusters")

            val results = mutableListOf<KnownPlace>()

            for (cluster in clusters) {
                if (cluster.size < MIN_VISITS_FOR_PLACE) continue

                val centerLat = cluster.map { it.latitude }.average()
                val centerLng = cluster.map { it.longitude }.average()

                val existing = placeRepository.findNearestPlace(centerLat, centerLng)

                if (existing != null && distanceMeters(
                        centerLat, centerLng,
                        existing.latitude, existing.longitude,
                    ) < existing.radiusMeters
                ) {
                    // Update existing place
                    placeRepository.incrementVisitCount(existing.id, System.currentTimeMillis())
                    results.add(existing)
                } else {
                    // Create new auto-detected place
                    val place = KnownPlace(
                        name = "Place #${results.size + 1}",
                        latitude = centerLat,
                        longitude = centerLng,
                        radiusMeters = CLUSTER_RADIUS_METERS,
                        category = PlaceCategory.OTHER,
                        visitCount = cluster.size,
                        isAutoDetected = true,
                        firstVisit = cluster.minOf { it.timestamp },
                        lastVisit = cluster.maxOf { it.timestamp },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    placeRepository.savePlace(place)
                    results.add(place)
                    logger.d(TAG, "New place detected at ($centerLat, $centerLng) with ${cluster.size} visits")
                }
            }

            logger.d(TAG, "Place detection complete: ${results.size} places")
            results
        }
    }

    /**
     * Simple distance-based clustering.
     *
     * Groups location points that are within [CLUSTER_RADIUS_METERS] of
     * each other. Uses a greedy approach — not optimal but fast.
     */
    private fun clusterLocations(locations: List<LocationEntry>): List<List<LocationEntry>> {
        val clusters = mutableListOf<MutableList<LocationEntry>>()
        val assigned = BooleanArray(locations.size)

        for (i in locations.indices) {
            if (assigned[i]) continue

            val cluster = mutableListOf(locations[i])
            assigned[i] = true

            for (j in i + 1 until locations.size) {
                if (assigned[j]) continue
                val dist = distanceMeters(
                    locations[i].latitude, locations[i].longitude,
                    locations[j].latitude, locations[j].longitude,
                )
                if (dist < CLUSTER_RADIUS_METERS) {
                    cluster.add(locations[j])
                    assigned[j] = true
                }
            }

            clusters.add(cluster)
        }

        return clusters
    }

    private fun distanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "DetectKnownPlacesUseCase"

        /** Minimum location points needed to attempt clustering. */
        private const val MIN_POINTS_FOR_CLUSTERING = 5

        /** Minimum cluster size to qualify as a known place. */
        private const val MIN_VISITS_FOR_PLACE = 3

        /** Radius for grouping points into clusters (meters). */
        private const val CLUSTER_RADIUS_METERS = 100.0
    }
}
