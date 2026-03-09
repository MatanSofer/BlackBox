package com.blackbox.android.collector

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.LocationData
import com.blackbox.domain.model.record.LocationSource
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.usecase.record.SaveLocationRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Collects location data using Google's FusedLocationProviderClient.
 *
 * This is an event-driven collector (baseIntervalMs = 0) that registers
 * a location callback with configurable update intervals. On each location
 * update it saves both a master [CollectedRecord] and a denormalized
 * [LocationEntry] for fast spatial queries.
 *
 * The collector generates a session UUID on start that is shared across
 * all location records captured during that session.
 *
 * @property context Android context for accessing location services.
 * @property saveLocationRecordUseCase Use case for atomic record + location saves.
 * @property logger Logger for lifecycle and error events.
 */
class LocationCollector(
    private val context: Context,
    private val saveLocationRecordUseCase: SaveLocationRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = 0, logger) {

    override val collectorType: CollectorType = CollectorType.LOCATION

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var sessionId: String = ""
    private var callbackScope: CoroutineScope? = null

    /** Default location update interval in milliseconds. */
    private val intervalMs: Long = DEFAULT_INTERVAL_MS

    // ── Reverse-geocoding cache ────────────────────────────────────────────────
    // Avoid calling Geocoder on every fix; only re-geocode when the user
    // has moved more than MIN_GEOCODE_DISTANCE_METERS from the last call.
    private var lastGeocodedLat: Double = Double.NaN
    private var lastGeocodedLng: Double = Double.NaN
    private var lastGeocodedAddress: String? = null

    @SuppressLint("MissingPermission")
    override fun onCollectorStarted() {
        logger.i(TAG, "Starting location updates (interval=${intervalMs}ms)")
        sessionId = UUID.randomUUID().toString()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        callbackScope = scope

        val client = LocationServices.getFusedLocationProviderClient(context)
        fusedClient = client

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs,
        )
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                scope.launch {
                    handleLocationUpdate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        bearing = if (location.hasBearing()) location.bearing else null,
                        timestamp = location.time,
                    )
                }
            }
        }
        locationCallback = callback

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnSuccessListener {
                logger.i(TAG, "Location updates registered with sessionId=$sessionId")
            }
            .addOnFailureListener { e ->
                logger.e(TAG, "Failed to register location updates — GPS disabled or foreground service type missing: ${e.message}", e)
            }
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping location updates")
        locationCallback?.let { callback ->
            fusedClient?.removeLocationUpdates(callback)
        }
        callbackScope?.cancel()
        callbackScope = null
        fusedClient = null
        locationCallback = null
        logger.i(TAG, "Location updates removed")
    }

    override suspend fun collectData(): List<CollectedRecord> {
        // Event-driven collector — data arrives via location callback
        return emptyList()
    }

    /**
     * Handles a single location update from the fused provider.
     *
     * Builds both the master [CollectedRecord] and the denormalized
     * [LocationEntry], then delegates to [SaveLocationRecordUseCase]
     * for atomic persistence.
     */
    private suspend fun handleLocationUpdate(
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        accuracyMeters: Float?,
        speed: Float?,
        bearing: Float?,
        timestamp: Long,
    ) {
        val now = System.currentTimeMillis()

        // Reverse-geocode only when the user has moved enough to warrant a new lookup.
        val address = if (shouldGeocode(latitude, longitude)) {
            reverseGeocode(latitude, longitude).also { addr ->
                lastGeocodedLat = latitude
                lastGeocodedLng = longitude
                lastGeocodedAddress = addr
            }
        } else {
            lastGeocodedAddress
        }

        val locationData = LocationData(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracyMeters = accuracyMeters,
            speed = speed,
            bearing = bearing,
            source = LocationSource.FUSED,
            address = address,
        )

        val record = CollectedRecord(
            timestamp = timestamp,
            collectorType = CollectorType.LOCATION,
            data = RecordData.Location(locationData),
            accuracyScore = computeAccuracyScore(accuracyMeters),
            sessionId = sessionId,
            createdAt = now,
        )

        val locationEntry = LocationEntry(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracyMeters = accuracyMeters,
            speed = speed,
            bearing = bearing,
            source = LocationSource.FUSED.name,
            timestamp = timestamp,
            address = address,
        )

        saveLocationRecordUseCase(record, locationEntry)
            .onSuccess {
                logger.d(TAG, "Location saved: ($latitude, $longitude) accuracy=${accuracyMeters}m")
            }
            .onFailure { e ->
                logger.e(TAG, "Failed to save location record", e)
            }
    }

    /**
     * Computes a 0.0–1.0 accuracy score from GPS horizontal accuracy.
     *
     * <=10m → 1.0 (excellent), >=100m → 0.1 (poor), linearly interpolated between.
     */
    private fun computeAccuracyScore(accuracyMeters: Float?): Float {
        if (accuracyMeters == null) return 0.5f
        return when {
            accuracyMeters <= EXCELLENT_ACCURACY_METERS -> 1.0f
            accuracyMeters >= POOR_ACCURACY_METERS -> 0.1f
            else -> {
                val range = POOR_ACCURACY_METERS - EXCELLENT_ACCURACY_METERS
                val normalized = (accuracyMeters - EXCELLENT_ACCURACY_METERS) / range
                1.0f - (normalized * 0.9f)
            }
        }
    }

    /**
     * Returns true when the device has moved more than [MIN_GEOCODE_DISTANCE_METERS]
     * from the last geocoded position, or when geocoding has not been done yet.
     */
    private fun shouldGeocode(lat: Double, lng: Double): Boolean {
        if (lastGeocodedLat.isNaN() || lastGeocodedLng.isNaN()) return true
        return haversineDistance(lastGeocodedLat, lastGeocodedLng, lat, lng) >= MIN_GEOCODE_DISTANCE_METERS
    }

    /**
     * Reverse-geocodes the given coordinates on the IO dispatcher.
     *
     * Returns a formatted "street, city" string, or null if the Geocoder is
     * unavailable, returns no results, or the network call fails.
     */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            formatAddress(addresses?.firstOrNull())
        }.getOrNull()
    }

    /**
     * Formats an [android.location.Address] into a compact "street, city" string.
     * Returns null when no meaningful fields are present.
     */
    private fun formatAddress(addr: android.location.Address?): String? {
        addr ?: return null
        val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
            .joinToString(" ")
            .ifBlank { null }
        val city = addr.locality ?: addr.subAdminArea
        return listOfNotNull(street, city).joinToString(", ").ifBlank { null }
    }

    /**
     * Computes the great-circle distance in metres between two WGS84 coordinates
     * using the Haversine formula.
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        private const val TAG = "LocationCollector"

        /** Default interval between location updates (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000

        /** GPS accuracy considered excellent (meters). */
        private const val EXCELLENT_ACCURACY_METERS = 10f

        /** GPS accuracy considered poor (meters). */
        private const val POOR_ACCURACY_METERS = 100f

        /** Minimum movement in metres before triggering a new reverse-geocode call. */
        private const val MIN_GEOCODE_DISTANCE_METERS = 100.0
    }
}
