package com.blackbox.domain.model.record

/**
 * Location data captured by the location collector.
 *
 * Contains WGS84 coordinates and metadata about the fix quality.
 * Stored as JSON in the BlackBoxRecord table and denormalized
 * into the LocationRecord table for fast spatial queries.
 *
 * @property latitude WGS84 latitude in degrees.
 * @property longitude WGS84 longitude in degrees.
 * @property altitude Meters above sea level (null if unavailable).
 * @property accuracyMeters GPS horizontal accuracy radius in meters.
 * @property speed Speed in meters per second (null when stationary).
 * @property bearing Compass bearing in degrees 0-360 (null if unavailable).
 * @property source How this location was determined.
 * @property providerDetails Additional provider info (e.g., "gps+wifi").
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val source: LocationSource = LocationSource.FUSED,
    val providerDetails: String? = null,
)

/**
 * How a location fix was determined.
 */
enum class LocationSource {
    /** Satellite-based GPS fix. */
    GPS,
    /** WiFi-based positioning. */
    WIFI,
    /** Cell tower triangulation. */
    CELL,
    /** Android fused location provider (combines multiple sources). */
    FUSED,
    /** Generic network-based location. */
    NETWORK,
}
