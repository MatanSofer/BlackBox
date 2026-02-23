package com.blackbox.android.collector

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.AppCategory
import com.blackbox.domain.model.record.AppUsageData
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.usecase.record.SaveRecordUseCase
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Collects foreground app usage events.
 *
 * Polling-based collector that queries [UsageStatsManager] for the most
 * recent foreground app. Never captures screen content, typed text, or
 * in-app actions — only app identity and usage duration.
 *
 * Requires the `PACKAGE_USAGE_STATS` permission (granted via Settings).
 *
 * @property context Android context for accessing UsageStatsManager.
 * @property saveRecordUseCase Use case for persisting records.
 * @property logger Logger for lifecycle and error events.
 */
class AppUsageCollector(
    private val context: Context,
    private val saveRecordUseCase: SaveRecordUseCase,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = DEFAULT_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.APP_USAGE

    private var usageStatsManager: UsageStatsManager? = null
    private var sessionId: String = ""
    private var lastForegroundPackage: String? = null
    private var lastForegroundStart: Long = 0L

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting app usage collector")
        sessionId = UUID.randomUUID().toString()
        lastForegroundPackage = null
        lastForegroundStart = 0L
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            logger.w(TAG, "UsageStatsManager not available")
        }
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping app usage collector")
        usageStatsManager = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val usm = usageStatsManager ?: return emptyList()
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - QUERY_WINDOW_MS,
            now,
        )

        if (stats.isNullOrEmpty()) return emptyList()

        val currentForeground = stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?: return emptyList()

        val packageName = currentForeground.packageName

        // Only record when the foreground app changes
        if (packageName == lastForegroundPackage) return emptyList()

        val sessionDuration = if (lastForegroundStart > 0) {
            now - lastForegroundStart
        } else {
            0L
        }

        lastForegroundPackage = packageName
        lastForegroundStart = now

        val displayName = getAppDisplayName(packageName)
        val isSystemApp = isSystemApp(packageName)

        val appUsageData = AppUsageData(
            foregroundApp = packageName,
            displayName = displayName,
            category = AppCategory.OTHER,
            sessionStart = now,
            sessionDurationMs = sessionDuration,
            isSystemApp = isSystemApp,
        )

        val record = CollectedRecord(
            timestamp = now,
            collectorType = CollectorType.APP_USAGE,
            data = RecordData.AppUsage(appUsageData),
            accuracyScore = 1.0f,
            sessionId = sessionId,
            createdAt = now,
        )

        saveRecordUseCase(record)
            .onSuccess {
                logger.d(TAG, "App usage saved: $displayName ($packageName)")
            }
            .onFailure { e ->
                logger.e(TAG, "Failed to save app usage record", e)
            }

        return listOf(record)
    }

    /** Resolves a human-readable app name from a package name. */
    private fun getAppDisplayName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /** Checks if a package is a pre-installed system app. */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val TAG = "AppUsageCollector"

        /** Default polling interval (2 minutes). */
        private const val DEFAULT_INTERVAL_MS = 2L * 60 * 1000

        /** How far back to query for usage stats. */
        private const val QUERY_WINDOW_MS = 5L * 60 * 1000
    }
}
