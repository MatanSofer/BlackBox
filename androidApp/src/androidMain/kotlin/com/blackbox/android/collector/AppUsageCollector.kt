package com.blackbox.android.collector

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import com.blackbox.android.collector.base.BaseCollector
import com.blackbox.domain.model.record.AppCategory
import com.blackbox.domain.model.record.AppUsageData
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.util.BlackBoxLogger
import java.util.UUID

/**
 * Collects foreground app usage events using [UsageEvents].
 *
 * On each poll cycle, queries all [UsageEvents.Event.ACTIVITY_RESUMED] events
 * since the last poll — so no app-switch is missed regardless of session length.
 * Saves one record per app-switch, capturing the package name, human-readable
 * display name, session start time, and duration of the previous session.
 *
 * Never captures screen content, typed text, or in-app actions.
 *
 * Requires the [AppOpsManager.OPSTR_GET_USAGE_STATS] special permission,
 * which the user must grant via Settings → Apps → Special app access → Usage access.
 * If the permission is absent the collector skips silently without crashing.
 *
 * @property context Android context for accessing system services.
 * @property logger Logger for lifecycle and error events.
 */
class AppUsageCollector(
    private val context: Context,
    logger: BlackBoxLogger,
) : BaseCollector(baseIntervalMs = POLL_INTERVAL_MS, logger) {

    override val collectorType: CollectorType = CollectorType.APP_USAGE

    private var usageStatsManager: UsageStatsManager? = null
    private var sessionId: String = ""
    private var lastQueryTime: Long = 0L
    private var lastForegroundPackage: String? = null
    private var lastForegroundStart: Long = 0L

    override fun onCollectorStarted() {
        logger.i(TAG, "Starting app usage collector")
        sessionId = UUID.randomUUID().toString()
        lastQueryTime = System.currentTimeMillis() - POLL_INTERVAL_MS
        lastForegroundPackage = null
        lastForegroundStart = 0L
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            logger.w(TAG, "UsageStatsManager not available on this device")
        }
    }

    override fun onCollectorStopped() {
        logger.i(TAG, "Stopping app usage collector")
        usageStatsManager = null
    }

    override suspend fun collectData(): List<CollectedRecord> {
        val usm = usageStatsManager ?: return emptyList()

        if (!hasUsageStatsPermission()) {
            logger.w(TAG, "PACKAGE_USAGE_STATS not granted — go to Settings > Special app access > Usage access")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val queryStart = lastQueryTime.coerceAtLeast(now - MAX_LOOKBACK_MS)

        val usageEvents = usm.queryEvents(queryStart, now)
        lastQueryTime = now

        val records = mutableListOf<CollectedRecord>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue

            val packageName = event.packageName
            val eventTime = event.timeStamp

            // Skip our own app to avoid noise
            if (packageName == context.packageName) continue

            val sessionDuration = if (lastForegroundStart > 0) {
                (eventTime - lastForegroundStart).coerceAtLeast(0L)
            } else {
                0L
            }

            val displayName = getAppDisplayName(packageName)
            val isSystemApp = isSystemApp(packageName)
            val category = categorizeApp(packageName, isSystemApp)

            val appUsageData = AppUsageData(
                foregroundApp = packageName,
                displayName = displayName,
                category = category,
                sessionStart = eventTime,
                sessionDurationMs = sessionDuration,
                isSystemApp = isSystemApp,
            )

            val record = CollectedRecord(
                timestamp = eventTime,
                collectorType = CollectorType.APP_USAGE,
                data = RecordData.AppUsage(appUsageData),
                accuracyScore = 1.0f,
                sessionId = sessionId,
                createdAt = now,
            )

            logger.d(TAG, "App switch collected: $displayName")
            records.add(record)
            lastForegroundPackage = packageName
            lastForegroundStart = eventTime
        }

        return records
    }

    /**
     * Checks whether the app has been granted [AppOpsManager.OPSTR_GET_USAGE_STATS].
     * This is a special permission that cannot be requested via [requestPermissions] —
     * the user must enable it manually in Settings.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Resolves a human-readable app name from a package name. */
    private fun getAppDisplayName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /** Returns true if the package belongs to a pre-installed system app. */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Heuristically categorises an app by matching known package prefixes.
     * Falls back to [AppCategory.TOOLS] for pre-installed system apps and
     * [AppCategory.OTHER] for everything else.
     */
    private fun categorizeApp(packageName: String, isSystemApp: Boolean): AppCategory {
        return when {
            SOCIAL_PREFIXES.any { packageName.startsWith(it) } -> AppCategory.SOCIAL
            MESSAGING_PREFIXES.any { packageName.startsWith(it) } -> AppCategory.COMMUNICATION
            BROWSER_PREFIXES.any { packageName.startsWith(it) } -> AppCategory.TOOLS
            MEDIA_PREFIXES.any { packageName.startsWith(it) } -> AppCategory.ENTERTAINMENT
            PRODUCTIVITY_PREFIXES.any { packageName.startsWith(it) } -> AppCategory.PRODUCTIVITY
            isSystemApp -> AppCategory.TOOLS
            else -> AppCategory.OTHER
        }
    }

    companion object {
        private const val TAG = "AppUsageCollector"

        /** Poll every 30 seconds — short enough to catch most sessions. */
        private const val POLL_INTERVAL_MS = 30L * 1_000

        /** Never look back more than 5 minutes to avoid re-processing old events. */
        private const val MAX_LOOKBACK_MS = 5L * 60 * 1_000

        private val SOCIAL_PREFIXES = listOf(
            "com.instagram", "com.facebook", "com.twitter", "com.x.android",
            "com.snapchat", "com.linkedin", "com.pinterest", "com.reddit",
            "com.tiktok", "com.zhiliaoapp",
        )
        private val MESSAGING_PREFIXES = listOf(
            "com.whatsapp", "org.telegram", "com.viber", "com.discord",
            "com.skype", "com.microsoft.teams", "com.google.android.gm",
            "com.slack", "com.signal",
        )
        private val BROWSER_PREFIXES = listOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera",
            "com.brave.browser", "com.microsoft.edge", "com.sec.android.app.sbrowser",
        )
        private val MEDIA_PREFIXES = listOf(
            "com.spotify", "com.netflix", "com.youtube", "com.google.android.youtube",
            "com.amazon.avod", "com.disney", "tv.twitch",
        )
        private val PRODUCTIVITY_PREFIXES = listOf(
            "com.google.android.apps.docs", "com.google.android.apps.sheets",
            "com.google.android.apps.slides", "com.microsoft.office",
            "com.adobe", "com.notion",
        )
    }
}
