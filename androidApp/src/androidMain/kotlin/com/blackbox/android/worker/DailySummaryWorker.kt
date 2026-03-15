package com.blackbox.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackbox.domain.usecase.place.DetectKnownPlacesUseCase
import com.blackbox.domain.usecase.timeline.GenerateDailySummaryUseCase
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that runs nightly maintenance tasks.
 *
 * Scheduled once every 24 hours near midnight. Performs two jobs:
 *
 * 1. **Hash-chain integrity** — [GenerateDailySummaryUseCase] aggregates the previous
 *    day's raw records into a [DailySummary] row that contains a SHA-256 day hash
 *    and a pointer to the previous day's hash. This tamper-detection chain remains
 *    in the [DailySummary] table even though the Insights screen now derives all its
 *    charts directly from raw [CollectedRecord] data (see [GetInsightsBriefUseCase]).
 *
 * 2. **Place detection** — [DetectKnownPlacesUseCase] clusters yesterday's location
 *    data to auto-detect new or updated [KnownPlace] entries.
 *
 * > **Note:** The [DailySummary] rows are no longer the primary data source for
 * > Insights charts. `GetInsightsBriefUseCase` reads raw records directly to guarantee
 * > exactly 7 trend entries even if this worker was skipped on a given night.
 *
 * Uses Koin for dependency injection via [KoinComponent].
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val generateDailySummaryUseCase: GenerateDailySummaryUseCase by inject()
    private val detectKnownPlacesUseCase: DetectKnownPlacesUseCase by inject()
    private val logger: BlackBoxLogger by inject()

    override suspend fun doWork(): Result {
        logger.d(TAG, "Starting daily summary generation")

        val cal = Calendar.getInstance()
        // Generate summary for yesterday
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStartMs = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEndMs = cal.timeInMillis

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        cal.add(Calendar.DAY_OF_YEAR, -1) // back to yesterday
        val dateStr = dateFormat.format(cal.time)

        val summaryResult = generateDailySummaryUseCase(dateStr, dayStartMs, dayEndMs)

        if (summaryResult.isFailure) {
            val error = summaryResult.exceptionOrNull()
            logger.e(TAG, "Failed to generate daily summary: ${error?.message}", error)
            return Result.retry()
        }

        logger.d(TAG, "Daily summary generated for $dateStr")

        // Run place detection on yesterday's location data
        detectKnownPlacesUseCase(dayStartMs, dayEndMs)
            .onSuccess { places -> logger.d(TAG, "Place detection complete: ${places.size} places updated") }
            .onFailure { error -> logger.w(TAG, "Place detection failed: ${error.message}") }

        return Result.success()
    }

    companion object {
        private const val TAG = "DailySummaryWorker"

        /** Unique work name for periodic scheduling. */
        const val WORK_NAME = "daily_summary"

        /**
         * Schedules the daily summary worker to run every 24 hours.
         *
         * Uses [ExistingPeriodicWorkPolicy.KEEP] to avoid rescheduling
         * if a schedule already exists.
         *
         * @param context Application context.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
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
