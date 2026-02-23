package com.blackbox.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackbox.domain.model.settings.RetentionPeriod
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.QueryRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that enforces data retention policies.
 *
 * Runs once per day to delete raw records and location entries
 * older than the configured [RetentionPeriod]. Daily summaries
 * and derived events are preserved indefinitely.
 *
 * Uses Koin for dependency injection via [KoinComponent].
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val recordRepository: RecordRepository by inject()
    private val locationRepository: LocationRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val logger: BlackBoxLogger by inject()

    override suspend fun doWork(): Result {
        logger.d(TAG, "Starting data cleanup")

        return runCatching {
            val retentionDays = RetentionPeriod.ONE_YEAR.days
            if (retentionDays < 0) {
                logger.d(TAG, "Retention policy is UNLIMITED, skipping cleanup")
                return Result.success()
            }

            val cutoffMs = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
            logger.d(TAG, "Cleaning records older than $retentionDays days (cutoff: $cutoffMs)")

            // Delete old raw records
            recordRepository.deleteRecordsOlderThan(cutoffMs)

            // Delete old location entries
            locationRepository.deleteLocationsInRange(0, cutoffMs)

            // Clean up old query history
            queryRepository.cleanupOldQueries(keepCount = 50)

            logger.d(TAG, "Cleanup complete")
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                logger.e(TAG, "Cleanup failed: ${error.message}", error)
                Result.retry()
            },
        )
    }

    companion object {
        private const val TAG = "CleanupWorker"

        /** Unique work name for periodic scheduling. */
        const val WORK_NAME = "data_cleanup"

        /**
         * Schedules the cleanup worker to run every 24 hours.
         *
         * @param context Application context.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(
                24, TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
