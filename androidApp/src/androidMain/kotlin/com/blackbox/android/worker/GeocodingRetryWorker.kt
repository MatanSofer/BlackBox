package com.blackbox.android.worker

import android.content.Context
import android.location.Geocoder
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

/**
 * One-time WorkManager worker that backfills reverse-geocoded addresses for
 * location records that were saved without internet connectivity.
 *
 * Runs only when a network connection is available. On each location entry
 * whose [LocationEntry.address] is null it calls [Geocoder.getFromLocation],
 * then atomically updates both [LocationRecord.address] and the parent
 * [BlackBoxRecord.data_json] so the resolved name is visible everywhere:
 * timeline rows, map screen, and AI query context.
 *
 * A 200 ms delay between geocoding calls avoids hammering the system Geocoder.
 * If every call fails (e.g., network present but Geocoder service down) the
 * worker returns [Result.retry] so WorkManager reschedules it automatically.
 */
class GeocodingRetryWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val locationRepository: LocationRepository by inject()
    private val logger: BlackBoxLogger by inject()

    override suspend fun doWork(): Result {
        val pending = locationRepository.getLocationsWithoutAddress()
        if (pending.isEmpty()) {
            logger.d(TAG, "No unresolved locations — nothing to do")
            return Result.success()
        }

        logger.i(TAG, "Retrying geocoding for ${pending.size} location(s)")

        var successCount = 0
        var failCount = 0

        for (entry in pending) {
            val address = reverseGeocode(entry.latitude, entry.longitude)
            if (address != null) {
                locationRepository.updateLocationAddress(entry.id, entry.recordId, address)
                successCount++
                logger.d(TAG, "Resolved (${entry.latitude}, ${entry.longitude}) → $address")
            } else {
                failCount++
            }
            // Small pause to stay within Geocoder's internal rate limits.
            delay(200)
        }

        logger.i(TAG, "Geocoding retry done: $successCount resolved, $failCount still pending")

        // Retry the whole job if nothing resolved at all (Geocoder may be temporarily down).
        return if (successCount == 0 && failCount > 0) Result.retry() else Result.success()
    }

    /**
     * Calls the system [Geocoder] on the IO dispatcher and formats the first
     * result as "street, city". Returns null if the Geocoder is unavailable,
     * returns no results, or throws an exception.
     */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(appContext, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                formatAddress(addresses?.firstOrNull())
            }.getOrNull()
        }

    private fun formatAddress(addr: android.location.Address?): String? {
        addr ?: return null
        val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
            .joinToString(" ")
            .ifBlank { null }
        val city = addr.locality ?: addr.subAdminArea
        return listOfNotNull(street, city).joinToString(", ").ifBlank { null }
    }

    companion object {
        private const val TAG = "GeocodingRetryWorker"

        /** Unique name prevents duplicate enqueues on repeated app launches. */
        const val WORK_NAME = "geocoding_retry"

        /**
         * Schedules a one-time geocoding-retry job that runs as soon as the
         * device has any network connection.
         *
         * Uses [ExistingWorkPolicy.KEEP] so repeated calls (e.g., on every app
         * launch) do not cancel a job that is already queued or running.
         *
         * @param context Application context.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<GeocodingRetryWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
