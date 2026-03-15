package com.blackbox.domain.usecase.sleep

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.LightClassification
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.model.record.ScreenState
import com.blackbox.domain.model.sleep.SleepQuality
import com.blackbox.domain.model.sleep.SleepSession
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/**
 * Detects a sleep session for a given night from screen-state records.
 *
 * Algorithm:
 * 1. Build a 16-hour detection window: previous day 20:00 → morning 12:00.
 * 2. Fetch [CollectorType.SCREEN_STATE] records in that window.
 * 3. Collect all timestamps when the screen became active (ON or UNLOCKED).
 *    These are the "awake anchor" points. The gaps between them are dark periods.
 * 4. Find the longest gap between consecutive active timestamps that:
 *    - is ≥ [MIN_SLEEP_MS] (3 hours), and
 *    - ends after 04:00 (the [MORNING_CUTOFF_HOUR]) to exclude idle evening time.
 * 5. Optionally extend the sleep start backward if the user enabled airplane mode
 *    shortly before the detected bedtime ([refineWithAirplaneMode]).
 * 6. Validate with ambient light data ([refineWithLightData]).
 *
 * This approach does NOT require paired OFF/LOCKED events — it only needs
 * the ON/UNLOCKED "wake" anchors. This makes it robust against service
 * restarts, missed broadcasts, and any gaps in the OFF record stream.
 *
 * @property recordRepository Source of raw screen-state records.
 * @property logger Logger for diagnostics.
 */
class DetectSleepSessionsUseCase(
    private val recordRepository: RecordRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Detects the sleep session for the night ending on [morningDate].
     *
     * The detection window spans from the previous day at 20:00 to
     * [morningDate] at 12:00, giving a 16-hour window that covers any
     * reasonable bedtime and wake-up combination.
     *
     * @param morningDate "yyyy-MM-dd" of the day the user woke up.
     * @return [Result] containing a [SleepSession] if one was detected, or null.
     */
    suspend operator fun invoke(morningDate: String): Result<SleepSession?> = runCatching {
        val tz = TimeZone.currentSystemDefault()
        val morning = LocalDate.parse(morningDate)
        val prevDay = morning.minus(1, DateTimeUnit.DAY)

        val nowMs = Clock.System.now().toEpochMilliseconds()

        // Window: prev day 20:00 → morning 12:00 (noon).
        val windowStartMs = prevDay.atStartOfDayIn(tz).toEpochMilliseconds() + HOUR_MS * 20
        val windowEndMs = morning.atStartOfDayIn(tz).toEpochMilliseconds() + HOUR_MS * 12
        val queryEndMs = minOf(windowEndMs, nowMs)

        val records = recordRepository.getRecordsByTypeInRange(
            CollectorType.SCREEN_STATE, windowStartMs, queryEndMs,
        )

        logger.d(TAG, "Detecting sleep for $morningDate — ${records.size} screen records in window")

        val activeTimes = extractActiveTimes(records, windowEndMs, nowMs)

        // Fetch additional signals for refinement
        val connectivityRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.CONNECTIVITY, windowStartMs, queryEndMs,
        )
        val lightRecords = recordRepository.getRecordsByTypeInRange(
            CollectorType.LIGHT, windowStartMs, queryEndMs,
        )

        val baseline = findSleepSession(activeTimes, windowEndMs)
        val refined = refineWithAirplaneMode(baseline, connectivityRecords, activeTimes)
        val finalSession = refineWithLightData(refined, lightRecords)
        finalSession?.copy(date = morningDate)
    }

    /**
     * Returns detected sleep sessions for the last [days] nights ending on [todayStr].
     *
     * Results are returned in chronological order (oldest first).
     * Nights with no detected session are returned as null entries so the list
     * always has exactly [days] elements — preserving positional alignment for charts.
     *
     * @param todayStr "yyyy-MM-dd" of today (the most recent morning).
     * @param days Number of nights to look back (default 7).
     */
    suspend fun getWeekTrend(todayStr: String, days: Int = 7): List<SleepSession?> {
        val today = LocalDate.parse(todayStr)
        return (days - 1 downTo 0).map { offset ->
            val date = today.minus(offset, DateTimeUnit.DAY).toString()
            invoke(date).getOrNull()
        }
    }

    // ── Core detection ────────────────────────────────────────────────────────

    /**
     * Extracts sorted screen-ON/UNLOCKED timestamps from [records].
     *
     * If the detection window is still open (we haven't reached noon yet),
     * [nowMs] is appended so a currently-sleeping user still gets detected.
     */
    private fun extractActiveTimes(
        records: List<CollectedRecord>,
        windowEndMs: Long,
        nowMs: Long,
    ): MutableList<Long> {
        val activeTimes = records
            .filter { record ->
                val state = (record.data as? RecordData.ScreenState)?.screenStateData?.state
                state == ScreenState.ON || state == ScreenState.UNLOCKED
            }
            .map { it.timestamp }
            .sorted()
            .toMutableList()

        if (nowMs < windowEndMs) activeTimes.add(nowMs)
        return activeTimes
    }

    private fun findSleepSession(
        activeTimes: MutableList<Long>,
        windowEndMs: Long,
    ): SleepSession? {
        // Wake-up must happen after 04:00 to exclude idle evening screen-off time.
        // morningCutoff = windowEnd (noon) - 8h = 04:00.
        val morningCutoffMs = windowEndMs - HOUR_MS * 8

        // Phone sessions that START at or after 06:00 are never merged into adjacent
        // dark periods — even if they last < BRIEF_WAKE_MS.
        // Rationale: a 5-minute screen-on at 3am is an alarm glance and should be
        // merged; the same 5 minutes at 7:27am is real morning activity (checking
        // messages, opening an app) and must mark the genuine wake time.
        // noon - 6h = 06:00.
        val morningMergeCutoffMs = windowEndMs - HOUR_MS * 6

        if (activeTimes.size < 2) return null

        // ── Step 1: group consecutive ON events into "phone sessions" ──────────
        // Consecutive ON events within BRIEF_WAKE_MS of each other belong to the
        // same phone-use session (e.g. several taps while reading at 2am).
        // Each session is stored as (firstOnMs, lastOnMs).
        val sessions = mutableListOf<Pair<Long, Long>>()
        var sessStart = activeTimes[0]
        var sessEnd   = activeTimes[0]
        for (i in 1 until activeTimes.size) {
            if (activeTimes[i] - sessEnd <= BRIEF_WAKE_MS) {
                sessEnd = activeTimes[i]   // extend current session
            } else {
                sessions.add(sessStart to sessEnd)
                sessStart = activeTimes[i]
                sessEnd   = activeTimes[i]
            }
        }
        sessions.add(sessStart to sessEnd)

        if (sessions.size < 2) return null

        // ── Step 2: build raw dark periods between consecutive sessions ─────────
        val darkPeriods = (0 until sessions.size - 1).map { i ->
            sessions[i].second to sessions[i + 1].first
        }

        // ── Step 3: merge consecutive dark periods across brief sessions ────────
        // If the phone session between two dark periods lasted < BRIEF_WAKE_MS AND
        // it started before 06:00, the user barely woke up (a quick night-time
        // alarm check, time check, etc.).  Sessions starting at/after 06:00 are
        // NEVER merged — even a 1-minute session at 7:27am counts as real waking up.
        val mergedDark = mutableListOf<Pair<Long, Long>>()
        var currentStart = darkPeriods[0].first
        var currentEnd   = darkPeriods[0].second

        for (i in 1 until darkPeriods.size) {
            val bridgeDuration    = sessions[i].second - sessions[i].first
            val isMorningActivity = sessions[i].first >= morningMergeCutoffMs
            if (bridgeDuration < BRIEF_WAKE_MS && !isMorningActivity) {
                currentEnd = darkPeriods[i].second
            } else {
                mergedDark.add(currentStart to currentEnd)
                currentStart = darkPeriods[i].first
                currentEnd   = darkPeriods[i].second
            }
        }
        mergedDark.add(currentStart to currentEnd)

        // ── Step 4: find the best qualifying merged dark period ─────────────────
        var bestSession: SleepSession? = null

        for ((start, end) in mergedDark) {
            val gapMs = end - start
            if (gapMs >= MIN_SLEEP_MS && end >= morningCutoffMs) {
                val effectiveStart  = maxOf(start, end - MAX_SLEEP_MS)
                val durationMs      = end - effectiveStart
                val durationMinutes = (durationMs / 60_000L).toInt()
                if (bestSession == null || durationMs > bestSession.durationMs) {
                    bestSession = SleepSession(
                        date = "",  // caller fills this in
                        sleepStart = effectiveStart,
                        wakeTime = end,
                        durationMs = durationMs,
                        durationMinutes = durationMinutes,
                        quality = qualityFrom(durationMinutes),
                    )
                }
            }
        }

        return bestSession
    }

    /**
     * Optionally extends the detected sleep start backward to when airplane mode
     * was enabled, if the user put the phone in airplane mode shortly before the
     * detected bedtime.
     *
     * Only fires on airplaneMode == true records — networkType == NONE alone
     * (e.g. dead zone) does NOT trigger this refinement to avoid false positives.
     *
     * @param baseline The baseline sleep session, or null if none was detected.
     * @param connectivityRecords Connectivity records for the detection window.
     * @param activeTimes Screen-ON timestamps used to verify no wake activity in the gap.
     * @return The refined session (extended start) or the unchanged baseline.
     */
    private fun refineWithAirplaneMode(
        baseline: SleepSession?,
        connectivityRecords: List<CollectedRecord>,
        activeTimes: List<Long>,
    ): SleepSession? {
        if (baseline == null) return null

        // Find the earliest connectivity record where airplaneMode == true
        // that falls within AIRPLANE_LOOKBACK_MS before the detected sleep start.
        val airplaneOnMs = connectivityRecords
            .mapNotNull { record ->
                val data = (record.data as? RecordData.Connectivity)?.connectivityData
                if (data?.airplaneMode == true) record.timestamp else null
            }
            .filter { ts ->
                ts < baseline.sleepStart && ts >= baseline.sleepStart - AIRPLANE_LOOKBACK_MS
            }
            .minOrNull() ?: return baseline

        // Discard the candidate if the user was still active after enabling airplane mode
        // (they put it on for focus/DND while still awake, not to sleep).
        val hasActiveEventInGap = activeTimes.any { ts ->
            ts > airplaneOnMs && ts < baseline.sleepStart
        }
        if (hasActiveEventInGap) return baseline

        // Extend sleep start to the airplane-on timestamp.
        // Guard against unreasonably long sessions.
        val newDurationMs = baseline.wakeTime - airplaneOnMs
        if (newDurationMs > MAX_SLEEP_MS) return baseline

        return baseline.copy(
            sleepStart = airplaneOnMs,
            durationMs = newDurationMs,
            durationMinutes = (newDurationMs / 60_000L).toInt(),
            quality = qualityFrom((newDurationMs / 60_000L).toInt()),
        )
    }

    /**
     * Sets [SleepSession.lightValidated] based on the fraction of DARK/DIM
     * ambient-light readings during the detected sleep window.
     *
     * Does NOT change sleep times or quality — purely a validation signal.
     *
     * @param session The session to annotate, or null.
     * @param lightRecords Light sensor records for the detection window.
     * @return The session with [SleepSession.lightValidated] set.
     */
    private fun refineWithLightData(
        session: SleepSession?,
        lightRecords: List<CollectedRecord>,
    ): SleepSession? {
        if (session == null) return null

        val windowRecords = lightRecords.filter {
            it.timestamp in session.sleepStart..session.wakeTime
        }
        if (windowRecords.size < LIGHT_MIN_RECORDS) return session

        val darkDimCount = windowRecords.count { record ->
            val cls = (record.data as? RecordData.Light)?.lightData?.classification
            cls == LightClassification.DARK || cls == LightClassification.DIM
        }
        val fraction = darkDimCount.toFloat() / windowRecords.size

        val lightValidated = when {
            fraction >= LIGHT_DARK_FRACTION_HIGH -> true
            fraction <  LIGHT_DARK_FRACTION_LOW  -> false
            else                                 -> null
        }

        return session.copy(lightValidated = lightValidated)
    }

    private fun qualityFrom(minutes: Int): SleepQuality = when {
        minutes < 360  -> SleepQuality.POOR   // < 6h
        minutes < 420  -> SleepQuality.FAIR   // 6–7h
        minutes <= 540 -> SleepQuality.GOOD   // 7–9h
        else           -> SleepQuality.LONG   // > 9h
    }

    companion object {
        private const val TAG = "DetectSleepSessionsUseCase"

        private const val HOUR_MS = 60 * 60 * 1_000L

        /** Minimum dark period to count as sleep (3 hours). */
        private const val MIN_SLEEP_MS = 3 * HOUR_MS

        /** Maximum plausible sleep duration (9 hours). Gaps longer than this are
         *  trimmed from the start, cutting out evening idle time while keeping the
         *  wake time accurate. */
        private const val MAX_SLEEP_MS = 9 * HOUR_MS

        /** If a phone session between two dark periods lasted less than this,
         *  it is treated as a brief night check and the dark periods are merged. */
        private const val BRIEF_WAKE_MS = 10 * 60_000L

        /** Wake-up must occur this many hours before windowEnd (noon) to qualify.
         *  noon - 8h = 04:00 — filters out idle evening screen-off time. */
        @Suppress("unused")
        private const val MORNING_CUTOFF_HOUR = 8

        /** How far before the detected sleep start to look for an airplane mode event. */
        private const val AIRPLANE_LOOKBACK_MS = 90 * 60_000L

        /** Minimum number of light sensor records needed for validation. */
        private const val LIGHT_MIN_RECORDS = 3

        /** If ≥ this fraction of readings are DARK/DIM → lightValidated = true. */
        private const val LIGHT_DARK_FRACTION_HIGH = 0.70f

        /** If < this fraction of readings are DARK/DIM → lightValidated = false. */
        private const val LIGHT_DARK_FRACTION_LOW  = 0.30f
    }
}
