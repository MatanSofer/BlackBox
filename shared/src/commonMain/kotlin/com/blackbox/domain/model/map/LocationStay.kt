package com.blackbox.domain.model.map

import com.blackbox.domain.model.place.KnownPlace

/**
 * A cluster of GPS fixes that represents the user staying in one place.
 *
 * Raw location fixes within [STAY_RADIUS_METERS] of each other are merged
 * into a single stay. The centroid of all fixes becomes the display position.
 *
 * @property latitude Centroid latitude of the cluster (WGS84 degrees).
 * @property longitude Centroid longitude of the cluster (WGS84 degrees).
 * @property arrivalTime Epoch ms of the earliest fix in this cluster.
 * @property departureTime Epoch ms of the latest fix in this cluster.
 * @property pointCount Number of GPS fixes that make up this cluster.
 * @property averageAccuracyMeters Mean horizontal accuracy of all fixes, or null if unavailable.
 * @property knownPlace Matched [KnownPlace] entry if the centroid is within a known radius, else null.
 */
data class LocationStay(
    val latitude: Double,
    val longitude: Double,
    val arrivalTime: Long,
    val departureTime: Long,
    val pointCount: Int,
    val averageAccuracyMeters: Float?,
    val knownPlace: KnownPlace?,
) {
    /** How long the user spent at this stay in milliseconds. */
    val durationMs: Long get() = (departureTime - arrivalTime).coerceAtLeast(0L)
}
