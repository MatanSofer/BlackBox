package com.blackbox.android.collector

import android.annotation.SuppressLint
import android.content.Context
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
import java.util.UUID

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

        val locationData = LocationData(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracyMeters = accuracyMeters,
            speed = speed,
            bearing = bearing,
            source = LocationSource.FUSED,
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

    companion object {
        private const val TAG = "LocationCollector"

        /** Default interval between location updates (5 minutes). */
        private const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000

        /** GPS accuracy considered excellent (meters). */
        private const val EXCELLENT_ACCURACY_METERS = 10f

        /** GPS accuracy considered poor (meters). */
        private const val POOR_ACCURACY_METERS = 100f
    }
}
