package com.blackbox.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackbox.domain.usecase.timeline.GenerateDailySummaryUseCase
import com.blackbox.domain.util.BlackBoxLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that generates daily summaries.
 *
 * Runs once per day (scheduled near midnight) to aggregate all raw
 * records from the previous day into a [DailySummary]. The summary
 * is stored for fast query responses and insight calculations.
 *
 * Uses Koin for dependency injection via [KoinComponent].
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val generateDailySummaryUseCase: GenerateDailySummaryUseCase by inject()
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

        return generateDailySummaryUseCase(dateStr, dayStartMs, dayEndMs).fold(
            onSuccess = {
                logger.d(TAG, "Daily summary generated for $dateStr")
                Result.success()
            },
            onFailure = { error ->
                logger.e(TAG, "Failed to generate daily summary: ${error.message}", error)
                Result.retry()
            },
        )
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
