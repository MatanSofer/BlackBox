package com.blackbox.domain.usecase.insight

import com.blackbox.domain.model.record.CallType
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.record.ScreenState
import com.blackbox.domain.model.sleep.SleepSession
import com.blackbox.domain.usecase.sleep.DetectSleepSessionsUseCase
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.platform.StepCounterProvider
import com.blackbox.domain.repository.LocationEntry
import com.blackbox.domain.repository.LocationRepository
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Aggregates all sensor data for the past 7 days into a single [InsightsBrief].
 *
 * All trend data is derived directly from raw [CollectedRecord] entries so that
 * every day in the 7-day window always has an entry — even if the nightly
 * [DailySummaryWorker] missed a day. This eliminates the bug where missing
 * DailySummary rows caused the weekly charts to show fewer than 7 bars.
 *
 * @property recordRepository Source of raw collected records.
 * @property locationRepository Source of location entries with resolved addresses.
 * @property stepCounterProvider Hardware step counter sensor (for today's live count).
 * @property detectSleepSessionsUseCase Sleep detection algorithm.
 * @property logger Logger for diagnostics.
 */
class GetInsightsBriefUseCase(
    private val recordRepository: RecordRepository,
    private val locationRepository: LocationRepository,
    private val stepCounterProvider: StepCounterProvider,
    private val detectSleepSessionsUseCase: DetectSleepSessionsUseCase,
    private val logger: BlackBoxLogger,
) {

    /**
     * Fetches and aggregates insights for the last 7 days ending today.
     *
     * @return [Result] containing an [InsightsBrief] on success, or an error.
     */
    suspend operator fun invoke(): Result<InsightsBrief> = runCatching {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val weekStart = today.minus(6, DateTimeUnit.DAY)

        val nowMs = now.toEpochMilliseconds()
        val todayStartMs = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val weekStartMs = weekStart.atStartOfDayIn(tz).toEpochMilliseconds()

        val todayStr = today.toString()
        val weekStartStr = weekStart.toString()

        // Ordered oldest-first list of the 7 days in the window.
        val weekDays = (6 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }

        logger.d(TAG, "Fetching brief: $weekStartStr → $todayStr")

        // ── Raw records for the full 7-day window ──────────────────────────────
        val weekActivityRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.ACTIVITY, weekStartMs, nowMs,
        )
        val weekScreenRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.SCREEN_STATE, weekStartMs, nowMs,
        )
        val weekAppRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.APP_USAGE, weekStartMs, nowMs,
        )
        val weekCallRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.CALL_LOG, weekStartMs, nowMs,
        )

        // ── Trend data — always exactly 7 entries ──────────────────────────────
        val stepTrend   = computeWeekStepTrend(weekDays, weekActivityRecords, tz)
        val screenTrend = computeWeekScreenTrend(weekDays, weekScreenRecords, tz, nowMs)

        // ── Today's records (sliced from the week batch) ───────────────────────
        val todayActivityRecords = weekActivityRecords.filter { it.timestamp >= todayStartMs }
        val todayScreenRecords   = weekScreenRecords.filter { it.timestamp >= todayStartMs }
        val todayAppRecords      = weekAppRecords.filter { it.timestamp >= todayStartMs }

        // ── Today's live stats ─────────────────────────────────────────────────
        val todaySteps = stepCounterProvider.getTodaySteps()
            ?: computeTodaySteps(todayActivityRecords)
        val todayScreenMinutes = computeScreenMinutes(todayScreenRecords, nowMs)

        // ── Call log aggregates ────────────────────────────────────────────────
        val weekTopContacts = aggregateTopContacts(weekCallRecords, limit = 5)

        // ── App usage aggregates ───────────────────────────────────────────────
        val weekTopApps  = aggregateTopApps(weekAppRecords, weekScreenRecords, nowMs, nowMs, limit = 5)
        val todayTopApp  = aggregateTopApps(todayAppRecords, todayScreenRecords, nowMs, nowMs, limit = 1).firstOrNull()
        val todayTopApps = aggregateTopApps(todayAppRecords, todayScreenRecords, nowMs, nowMs, limit = 5)

        // ── Location data ──────────────────────────────────────────────────────
        val weekLocations = locationRepository.getLocationsInRange(weekStartMs, nowMs)
        val weekTopPlaces = aggregateTopPlaces(weekLocations, limit = 5)
        val todayPlacesCount = weekLocations
            .filter { it.timestamp >= todayStartMs }
            .mapNotNull { it.address }
            .distinct()
            .size

        // ── Sleep detection for the last 7 nights (always 7 entries) ──────────
        val weekSleepTrend  = detectSleepSessionsUseCase.getWeekTrend(todayStr)
        val lastNightSleep  = weekSleepTrend.lastOrNull { it != null }
        val avgSleepMinutes = weekSleepTrend.filterNotNull()
            .takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()?.toInt() ?: 0

        // ── Derived aggregates ─────────────────────────────────────────────────
        val avgSteps    = stepTrend.map { it.steps }.average().takeIf { it.isFinite() }?.toInt() ?: 0
        val avgScreen   = screenTrend.map { it.totalMinutes }.average().takeIf { it.isFinite() }?.toInt() ?: 0
        val bestStepDay = stepTrend.maxByOrNull { it.steps }
        val stepsVsAvg  = if (avgSteps > 0) todaySteps.toFloat() / avgSteps else 1f

        logger.d(TAG, "Brief ready — today: $todaySteps steps, ${weekTopApps.size} apps, sleep: ${lastNightSleep?.durationMinutes ?: 0}min")

        InsightsBrief(
            todaySteps = todaySteps,
            todayScreenMinutes = todayScreenMinutes,
            todayPlacesCount = todayPlacesCount,
            todayTopApp = todayTopApp,
            todayTopApps = todayTopApps,
            todayStepsVsAvg = stepsVsAvg,
            weekStepTrend = stepTrend,
            weekScreenTrend = screenTrend,
            weekTopPlaces = weekTopPlaces,
            weekTopApps = weekTopApps,
            weekTopContacts = weekTopContacts,
            bestStepDay = bestStepDay,
            avgDailySteps = avgSteps,
            avgDailyScreenMinutes = avgScreen,
            lastNightSleep = lastNightSleep,
            weekSleepTrend = weekSleepTrend,
            avgSleepMinutes = avgSleepMinutes,
        )
    }

    // ── Weekly trend computation ───────────────────────────────────────────────

    /**
     * Computes daily step counts for [days] by grouping raw activity records by day.
     *
     * Uses the cumulative hardware pedometer counter: max − min per day eliminates
     * the overcounting that summing deltas would cause. Always returns exactly
     * [days] entries, with 0 steps for days that have no activity records.
     */
    private fun computeWeekStepTrend(
        days: List<LocalDate>,
        records: List<CollectedRecord>,
        tz: TimeZone,
    ): List<DailyStepCount> {
        val byDay = records.groupBy {
            Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date.toString()
        }
        return days.map { day ->
            val cumulatives = byDay[day.toString()]
                ?.mapNotNull { (it.data as? RecordData.Activity)?.activityData?.stepCountCumulative }
                ?.filter { it > 0 } ?: emptyList()
            val steps = if (cumulatives.size >= 2)
                (cumulatives.max() - cumulatives.min()).toInt().coerceAtLeast(0) else 0
            DailyStepCount(date = day.toString(), steps = steps)
        }
    }

    /**
     * Computes daily screen-on minutes for [days] by grouping raw screen-state records.
     *
     * For each day, screen minutes are bounded by the day boundary on the right
     * and by [nowMs] for the current in-progress day. Always returns exactly
     * [days] entries, with 0 minutes for days that have no screen records.
     */
    private fun computeWeekScreenTrend(
        days: List<LocalDate>,
        records: List<CollectedRecord>,
        tz: TimeZone,
        nowMs: Long,
    ): List<com.blackbox.domain.repository.DailyScreenTime> {
        val byDay = records.groupBy {
            Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date.toString()
        }
        return days.map { day ->
            val dayRecords  = byDay[day.toString()] ?: emptyList()
            val dayEndMs    = day.atStartOfDayIn(tz).toEpochMilliseconds() + DAY_MS
            val effectiveMs = minOf(dayEndMs, nowMs)
            val minutes     = computeScreenMinutes(dayRecords, effectiveMs)
            val pickups     = dayRecords.count {
                (it.data as? RecordData.ScreenState)?.screenStateData?.state == ScreenState.ON
            }
            com.blackbox.domain.repository.DailyScreenTime(
                date = day.toString(), totalMinutes = minutes, pickupCount = pickups,
            )
        }
    }

    // ── Per-day computation helpers ────────────────────────────────────────────

    /**
     * Computes today's step count from raw activity records.
     *
     * Uses the hardware pedometer's cumulative counter: max − min avoids the
     * overcounting bug that summing [ActivityData.stepCountDelta] would cause.
     */
    private fun computeTodaySteps(records: List<CollectedRecord>): Int {
        val cumulativeValues = records
            .mapNotNull { (it.data as? RecordData.Activity)?.activityData?.stepCountCumulative }
            .filter { it > 0 }
        if (cumulativeValues.isEmpty()) return 0
        return (cumulativeValues.max() - cumulativeValues.min()).toInt().coerceAtLeast(0)
    }

    /**
     * Computes screen-on minutes by pairing SCREEN ON → OFF events.
     *
     * An ON event without a matching OFF is closed against [nowMs] (phone still on).
     */
    private fun computeScreenMinutes(records: List<CollectedRecord>, nowMs: Long): Int {
        var totalMs = 0L
        var lastOnMs: Long? = null

        records
            .sortedBy { it.timestamp }
            .forEach { record ->
                val state = (record.data as? RecordData.ScreenState)?.screenStateData?.state
                when (state) {
                    ScreenState.ON, ScreenState.UNLOCKED -> {
                        if (lastOnMs == null) lastOnMs = record.timestamp
                    }
                    ScreenState.OFF, ScreenState.LOCKED -> {
                        lastOnMs?.let { onMs ->
                            totalMs += record.timestamp - onMs
                            lastOnMs = null
                        }
                    }
                    else -> Unit
                }
            }

        lastOnMs?.let { totalMs += nowMs - it }
        return (totalMs / 60_000L).toInt()
    }

    // ── App usage aggregation ──────────────────────────────────────────────────

    /**
     * Aggregates app usage from raw records into a ranked list of [AppUsageStat].
     *
     * Three phases:
     * 1. Base aggregation — sum [AppUsageData.sessionDurationMs] per app.
     * 2. Orphaned tail detection — extend the last app's time to the first
     *    screen-off event (capped at [ORPHAN_CAP_MS]) to capture the unrecorded
     *    session that ended after the last record was written.
     * 3. Gap fill — for completed screen-ON intervals with no corresponding app
     *    record, attribute the interval duration to the most recent preceding app
     *    (capped at [GAP_FILL_CAP_MS]) to recover long single-app sessions that
     *    UsageStatsManager split across reporting boundaries.
     *
     * @param appRecords App usage records for the time window.
     * @param screenRecords Screen state records for the same time window.
     * @param windowEndMs End boundary of the analysis window (usually nowMs).
     * @param nowMs Current epoch ms — used to determine completed intervals.
     * @param limit Maximum number of apps to return.
     */
    private fun aggregateTopApps(
        appRecords: List<CollectedRecord>,
        screenRecords: List<CollectedRecord>,
        windowEndMs: Long,
        nowMs: Long,
        limit: Int,
    ): List<AppUsageStat> {
        // Phase 1: base aggregation (duration in ms, converted to minutes at end)
        val appTotalsMs = mutableMapOf<String, Long>()

        val filteredAppData = appRecords
            .mapNotNull { r -> (r.data as? RecordData.AppUsage)?.appUsageData }
            .filter { !it.isSystemApp && it.displayName.isNotBlank() && !isBlockedPackage(it.foregroundApp) }
            .mapNotNull { data ->
                val label = cleanAppDisplayName(data.displayName)
                if (label.length < 2 || label.lowercase() in GENERIC_APP_SEGMENTS) null
                else label to data
            }

        for ((label, data) in filteredAppData) {
            appTotalsMs[label] = (appTotalsMs[label] ?: 0L) + data.sessionDurationMs
        }

        // Phase 2: Orphaned tail detection
        val sortedAppData = filteredAppData.sortedBy { (_, d) -> d.sessionStart }
        val lastApp = sortedAppData.lastOrNull()
        if (lastApp != null) {
            val (lastLabel, lastData) = lastApp
            val lastSessionEndMs = lastData.sessionStart + lastData.sessionDurationMs

            val firstScreenOffMs = screenRecords
                .filter { r ->
                    val state = (r.data as? RecordData.ScreenState)?.screenStateData?.state
                    (state == ScreenState.OFF || state == ScreenState.LOCKED) &&
                        r.timestamp > lastSessionEndMs
                }
                .minOfOrNull { it.timestamp }

            val orphanEndMs = minOf(
                firstScreenOffMs ?: (lastSessionEndMs + ORPHAN_CAP_MS),
                lastSessionEndMs + ORPHAN_CAP_MS,
            )
            val orphanMs = orphanEndMs - lastSessionEndMs

            if (orphanMs > MIN_ORPHAN_MS) {
                appTotalsMs[lastLabel] = (appTotalsMs[lastLabel] ?: 0L) + orphanMs
            }
        }

        // Phase 3: Gap fill for screen-on intervals with no app records
        // Only applies to intervals that completed >= 1 hour ago (not in-progress).
        val screenIntervals = buildScreenIntervals(screenRecords, windowEndMs)
        for ((intStart, intEnd) in screenIntervals) {
            if (intEnd >= nowMs - HOUR_MS) continue
            val intDurationMs = intEnd - intStart
            if (intDurationMs < GAP_FILL_MIN_MS) continue

            val hasAppRecord = appRecords.any { r -> r.timestamp in intStart..intEnd }
            if (hasAppRecord) continue

            val preceding = sortedAppData
                .filter { (_, d) ->
                    val sessionEnd = d.sessionStart + d.sessionDurationMs
                    sessionEnd <= intStart && sessionEnd >= intStart - GAP_FILL_LOOKBACK_MS
                }
                .maxByOrNull { (_, d) -> d.sessionStart }

            if (preceding != null) {
                val (precLabel, _) = preceding
                val gapMs = minOf(intDurationMs, GAP_FILL_CAP_MS)
                appTotalsMs[precLabel] = (appTotalsMs[precLabel] ?: 0L) + gapMs
            }
        }

        return appTotalsMs.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { AppUsageStat(displayName = it.key, totalMinutes = it.value / 60_000L) }
    }

    /**
     * Builds a list of screen-ON intervals from screen-state records by pairing
     * ON/UNLOCKED → OFF/LOCKED events. An unclosed ON interval is closed at [endMs].
     */
    private fun buildScreenIntervals(
        records: List<CollectedRecord>,
        endMs: Long,
    ): List<Pair<Long, Long>> {
        val intervals = mutableListOf<Pair<Long, Long>>()
        var lastOnMs: Long? = null

        records
            .sortedBy { it.timestamp }
            .forEach { record ->
                val state = (record.data as? RecordData.ScreenState)?.screenStateData?.state
                when (state) {
                    ScreenState.ON, ScreenState.UNLOCKED -> {
                        if (lastOnMs == null) lastOnMs = record.timestamp
                    }
                    ScreenState.OFF, ScreenState.LOCKED -> {
                        lastOnMs?.let { onMs ->
                            intervals.add(onMs to record.timestamp)
                            lastOnMs = null
                        }
                    }
                    else -> Unit
                }
            }

        lastOnMs?.let { intervals.add(it to endMs) }
        return intervals
    }

    /**
     * Resolves a display name that may be a raw package name stored as a fallback.
     *
     * Resolution order:
     * 1. Exact match in [KNOWN_PACKAGES] → use the canonical label.
     * 2. Prefix match (e.g. "com.whatsapp.debug") → use the canonical label.
     * 3. Real app name (contains space or uppercase) → return as-is.
     * 4. Walk segments from the right, skip [GENERIC_APP_SEGMENTS], capitalize the
     *    first non-generic segment of length ≥ 3.
     */
    private fun cleanAppDisplayName(name: String): String {
        KNOWN_PACKAGES[name]?.let { return it }

        val looksLikePackage = name.contains('.') &&
            name.none { it == ' ' } &&
            name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' } &&
            name.first().isLowerCase()
        if (!looksLikePackage) return name

        for ((pkg, label) in KNOWN_PACKAGES) {
            if (name.startsWith("$pkg.") || name == pkg) return label
        }

        val segments = name.split('.')
        for (segment in segments.asReversed()) {
            if (segment.length >= 3 && segment.lowercase() !in GENERIC_APP_SEGMENTS) {
                return segment.replaceFirstChar { it.uppercaseChar() }
            }
        }

        return segments.maxByOrNull { it.length }
            ?.replaceFirstChar { it.uppercaseChar() } ?: name
    }

    /**
     * Aggregates call log records into a ranked list of [ContactCallStat] for the week.
     *
     * Calls are grouped by [CallLogData.numberHash] so each unique phone number becomes
     * one entry regardless of whether a contact name is known. When a contact name is
     * available from any record for that hash, it is used; otherwise "Unknown" is shown.
     *
     * @param records All CALL_LOG records for the period.
     * @param limit Maximum number of contacts to return.
     */
    private fun aggregateTopContacts(
        records: List<CollectedRecord>,
        limit: Int,
    ): List<ContactCallStat> {
        data class Acc(
            var displayName: String,
            var total: Int = 0,
            var durationSec: Int = 0,
            var missed: Int = 0,
        )

        val byHash = mutableMapOf<String, Acc>()

        records.forEach { r ->
            val data = (r.data as? RecordData.CallLog)?.callLogData ?: return@forEach
            val acc = byHash.getOrPut(data.numberHash) {
                Acc(displayName = data.contactName ?: "Unknown")
            }
            // Upgrade to a real name if we encounter one for this hash
            if (acc.displayName == "Unknown" && data.contactName != null) {
                acc.displayName = data.contactName
            }
            acc.total++
            acc.durationSec += data.durationSeconds
            if (data.callType == CallType.MISSED || data.callType == CallType.REJECTED) acc.missed++
        }

        return byHash.entries
            .sortedByDescending { it.value.total }
            .take(limit)
            .map { (hash, acc) ->
                ContactCallStat(
                    displayName = acc.displayName,
                    numberHash = hash,
                    totalCalls = acc.total,
                    totalDurationSeconds = acc.durationSec,
                    missedCount = acc.missed,
                )
            }
    }

    /**
     * Returns true for package names that are known background system components
     * and should never appear in user-facing app usage lists.
     *
     * This supplements the [AppUsageData.isSystemApp] flag, which is not always
     * set correctly by [UsageStatsManager] on all Android versions and OEM ROMs.
     */
    private fun isBlockedPackage(packageName: String): Boolean =
        packageName in SYSTEM_PACKAGE_BLOCKLIST

    private fun aggregateTopPlaces(
        locations: List<LocationEntry>,
        limit: Int,
    ): List<PlaceVisit> =
        locations
            .mapNotNull { entry -> entry.address?.let { it to entry.timestamp } }
            .groupBy { (addr, _) -> addr }
            .mapValues { (_, pairs) ->
                pairs.map { (_, ts) -> ts / DAY_MS }.distinct().size
            }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { PlaceVisit(address = it.key, visitCount = it.value) }

    companion object {
        private const val TAG = "GetInsightsBriefUseCase"
        private const val DAY_MS  = 24 * 3600 * 1000L
        private const val HOUR_MS = 3600 * 1000L

        // Phase 2 (orphaned tail) constants
        /** Cap on how far past the last app record to attribute time. */
        private const val ORPHAN_CAP_MS  = 30 * 60_000L
        /** Minimum orphan duration worth attributing. */
        private const val MIN_ORPHAN_MS  = 60_000L

        // Phase 3 (gap fill) constants
        /** Minimum screen-on interval duration to attempt attribution. */
        private const val GAP_FILL_MIN_MS      = 5 * 60_000L
        /** Maximum time to attribute to a single gap attribution. */
        private const val GAP_FILL_CAP_MS      = 60 * 60_000L
        /** How far back to look for a preceding app record when filling a gap. */
        private const val GAP_FILL_LOOKBACK_MS = 15 * 60_000L

        /** Canonical labels for the most common apps whose package names are ambiguous. */
        private val KNOWN_PACKAGES = mapOf(
            "org.telegram.messenger" to "Telegram",
            "org.telegram.messenger.beta" to "Telegram Beta",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.facebook.lite" to "Facebook Lite",
            "com.twitter.android" to "Twitter",
            "com.x.android" to "X",
            "com.snapchat.android" to "Snapchat",
            "com.spotify.music" to "Spotify",
            "com.netflix.mediaclient" to "Netflix",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.gm" to "Gmail",
            "com.google.android.chrome" to "Chrome",
            "com.google.android.apps.maps" to "Google Maps",
            "com.google.android.apps.photos" to "Google Photos",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.discord" to "Discord",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.ss.android.ugc.trill" to "TikTok",
            "com.reddit.frontpage" to "Reddit",
            "com.linkedin.android" to "LinkedIn",
            "com.amazon.mShop.android.shopping" to "Amazon",
            "com.microsoft.teams" to "Microsoft Teams",
            "com.slack" to "Slack",
            "org.mozilla.firefox" to "Firefox",
            "com.brave.browser" to "Brave",
            "com.microsoft.edge" to "Edge",
            "com.amazon.kindle" to "Kindle",
            "com.google.android.apps.youtube.music" to "YouTube Music",
        )

        /**
         * Exact package names that are background system components and must never
         * appear in the "popular apps" list even when [AppUsageData.isSystemApp] is
         * incorrectly false (which happens on some OEM ROMs and Android versions).
         *
         * Examples of false negatives:
         * - `com.android.cellbroadcastreceiver` → shown as "Cellbroadcastreceiver"
         * - `com.samsung.android.app.clockpackage` → shown as "Clockpackage"
         */
        private val SYSTEM_PACKAGE_BLOCKLIST = setOf(
            // Emergency / carrier background receivers
            "com.android.cellbroadcastreceiver",
            "com.android.cellbroadcastservice",
            "com.google.android.cellbroadcastreceiver",
            // OEM clock/alarm background services (not the Clock UI app)
            "com.samsung.android.app.clockpackage",
            "com.huawei.android.clockpackage",
            // Core Android background components
            "com.android.phone",
            "com.android.systemui",
            "com.android.keyguard",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            // Google Play background services
            "com.google.android.gms",
            "com.google.android.gsf",
        )

        /**
         * Package name segments that are too generic to be used as an app label.
         */
        private val GENERIC_APP_SEGMENTS = setOf(
            "android", "messenger", "app", "apps", "mobile", "lite", "debug", "beta",
            "nh", "client", "main", "launcher", "home", "ui", "service", "services",
            "core", "framework", "system", "provider", "manager", "helper",
            "activity", "feature", "module", "lib", "library", "common", "base",
            "internal", "impl", "utils", "util", "data", "api",
        )
    }
}

// ── Output models ─────────────────────────────────────────────────────────────

/**
 * Full aggregated snapshot of the user's last 7 days, used by the Insights screen.
 *
 * @property todaySteps Live step count for today from raw activity records.
 * @property todayScreenMinutes Screen-on minutes today.
 * @property todayPlacesCount Distinct named places visited today.
 * @property todayTopApp App with the most foreground time today.
 * @property todayTopApps Top 5 apps by foreground time today, for the per-app breakdown.
 * @property todayStepsVsAvg Today's steps divided by the 7-day average (1.0 = on par).
 * @property weekStepTrend Daily step counts for the last 7 days. Always 7 entries.
 * @property weekScreenTrend Daily screen-time data for the last 7 days. Always 7 entries.
 * @property weekTopPlaces Most visited places this week, ranked by distinct days.
 * @property weekTopApps Most-used apps this week, ranked by total foreground time.
 * @property weekTopContacts Most-called contacts this week, ranked by call count.
 * @property bestStepDay The day with the highest step count in the period.
 * @property avgDailySteps Mean daily steps over the 7-day window.
 * @property avgDailyScreenMinutes Mean daily screen minutes over the 7-day window.
 * @property lastNightSleep Detected sleep session for the most recent night, or null.
 * @property weekSleepTrend Detected sleep sessions for the last 7 nights. Always 7 entries;
 *   null elements indicate nights where no session could be detected.
 * @property avgSleepMinutes Mean sleep duration in minutes over detected nights.
 */
data class InsightsBrief(
    val todaySteps: Int = 0,
    val todayScreenMinutes: Int = 0,
    val todayPlacesCount: Int = 0,
    val todayTopApp: AppUsageStat? = null,
    val todayTopApps: List<AppUsageStat> = emptyList(),
    val todayStepsVsAvg: Float = 1f,
    val weekStepTrend: List<DailyStepCount> = emptyList(),
    val weekScreenTrend: List<com.blackbox.domain.repository.DailyScreenTime> = emptyList(),
    val weekTopPlaces: List<PlaceVisit> = emptyList(),
    val weekTopApps: List<AppUsageStat> = emptyList(),
    val weekTopContacts: List<ContactCallStat> = emptyList(),
    val bestStepDay: DailyStepCount? = null,
    val avgDailySteps: Int = 0,
    val avgDailyScreenMinutes: Int = 0,
    val lastNightSleep: SleepSession? = null,
    val weekSleepTrend: List<SleepSession?> = emptyList(),
    val avgSleepMinutes: Int = 0,
)

/**
 * A place the user has visited, ranked by number of distinct days.
 *
 * @property address Human-readable address from reverse geocoding.
 * @property visitCount Number of distinct days this address appeared in the period.
 */
data class PlaceVisit(
    val address: String,
    val visitCount: Int,
)

/**
 * An app ranked by total foreground time.
 *
 * @property displayName Human-readable app name.
 * @property totalMinutes Total foreground minutes in the period.
 */
data class AppUsageStat(
    val displayName: String,
    val totalMinutes: Long,
)

/**
 * A contact ranked by total call count for the week.
 *
 * The phone number is never stored — only [numberHash] (non-reversible truncated SHA-256)
 * is kept for deduplication. [displayName] comes from the system call log's cached contact
 * name ([android.provider.CallLog.Calls.CACHED_NAME]) and requires no additional permissions.
 *
 * @property displayName Contact's display name, or "Unknown" if not in the contacts list.
 * @property numberHash Truncated SHA-256 of the phone number — used only for deduplication.
 * @property totalCalls Total number of calls (all directions) in the period.
 * @property totalDurationSeconds Sum of connected call durations in seconds.
 * @property missedCount Number of missed or rejected calls from this contact.
 */
data class ContactCallStat(
    val displayName: String,
    val numberHash: String,
    val totalCalls: Int,
    val totalDurationSeconds: Int,
    val missedCount: Int,
)
