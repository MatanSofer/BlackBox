package com.blackbox.domain.usecase.sleep

import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.CollectorType
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
 * 1. Build a 14-hour detection window: previous day 20:00 → morning 10:00.
 * 2. Fetch [CollectorType.SCREEN_STATE] records in that window.
 * 3. Find all screen-off gaps (SCREEN_OFF/LOCKED → SCREEN_ON/UNLOCKED).
 * 4. Merge adjacent off-periods separated by less than [MERGE_GAP_MS] (brief
 *    wake-ups that should not split a session).
 * 5. Keep only merged periods ≥ [MIN_SLEEP_MS] (3 hours).
 * 6. Return the longest qualifying period as the sleep session.
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
     * [morningDate] at 10:00, giving a 14-hour window that covers any
     * reasonable bedtime and wake-up combination.
     *
     * @param morningDate "yyyy-MM-dd" of the day the user woke up.
     * @return [Result] containing a [SleepSession] if one was detected, or null.
     */
    suspend operator fun invoke(morningDate: String): Result<SleepSession?> = runCatching {
        val tz = TimeZone.currentSystemDefault()
        val morning = LocalDate.parse(morningDate)
        val prevDay = morning.minus(1, DateTimeUnit.DAY)

        // Window: prev day 22:00 → morning 10:00 (12h window)
        // Starting at 22:00 avoids counting idle evening screen-off time as sleep.
        val windowStartMs = prevDay.atStartOfDayIn(tz).toEpochMilliseconds() + HOUR_MS * 22
        val windowEndMs = morning.atStartOfDayIn(tz).toEpochMilliseconds() + HOUR_MS * 10
        val nowMs = Clock.System.now().toEpochMilliseconds()

        val records = recordRepository.getRecordsByTypeInRange(
            CollectorType.SCREEN_STATE, windowStartMs, windowEndMs,
        )

        logger.d(TAG, "Detecting sleep for $morningDate — ${records.size} screen records in window")

        findSleepSession(records, windowStartMs, windowEndMs, nowMs)?.copy(date = morningDate)
    }

    /**
     * Returns detected sleep sessions for the last [days] nights ending on [todayStr].
     *
     * Results are returned in chronological order (oldest first).
     * Nights with no detected session are omitted from the list.
     *
     * @param todayStr "yyyy-MM-dd" of today (the most recent morning).
     * @param days Number of nights to look back (default 7).
     */
    suspend fun getWeekTrend(todayStr: String, days: Int = 7): List<SleepSession> {
        val today = LocalDate.parse(todayStr)
        return (days - 1 downTo 0).mapNotNull { offset ->
            val date = today.minus(offset, DateTimeUnit.DAY).toString()
            invoke(date).getOrNull()
        }
    }

    // ── Core detection ────────────────────────────────────────────────────────

    private fun findSleepSession(
        records: List<CollectedRecord>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long,
    ): SleepSession? {
        // Step 1: collect all screen-off intervals
        val offPeriods = mutableListOf<Pair<Long, Long>>()
        var offSinceMs: Long? = null

        records.sortedBy { it.timestamp }.forEach { record ->
            val state = (record.data as? RecordData.ScreenState)?.screenStateData?.state
            when (state) {
                ScreenState.OFF, ScreenState.LOCKED -> {
                    if (offSinceMs == null) offSinceMs = record.timestamp
                }
                ScreenState.ON, ScreenState.UNLOCKED -> {
                    offSinceMs?.let { startMs ->
                        if (record.timestamp > startMs) {
                            offPeriods.add(startMs to record.timestamp)
                        }
                        offSinceMs = null
                    }
                }
                else -> Unit
            }
        }

        // Screen still off at window boundary — close against now or window end
        offSinceMs?.let { startMs ->
            offPeriods.add(startMs to minOf(nowMs, windowEndMs))
        }

        if (offPeriods.isEmpty()) return null

        // Step 2: merge off-periods whose gap is less than MERGE_GAP_MS
        val merged = mutableListOf<Pair<Long, Long>>()
        var current = offPeriods.first()

        for (i in 1 until offPeriods.size) {
            val next = offPeriods[i]
            val gap = next.first - current.second
            current = if (gap <= MERGE_GAP_MS) {
                current.first to next.second  // extend
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)

        // Step 3: find the longest qualifying period
        val sleepPeriod = merged
            .filter { (start, end) -> end - start >= MIN_SLEEP_MS }
            .maxByOrNull { (start, end) -> end - start }
            ?: return null

        // Cap at MAX_SLEEP_MS by trimming the start, keeping the wake time accurate.
        // This prevents idle evening screen-off time from inflating the duration.
        val effectiveStart = maxOf(sleepPeriod.first, sleepPeriod.second - MAX_SLEEP_MS)
        val durationMs = sleepPeriod.second - effectiveStart
        val durationMinutes = (durationMs / 60_000L).toInt()

        return SleepSession(
            date = "",  // caller fills this in
            sleepStart = effectiveStart,
            wakeTime = sleepPeriod.second,
            durationMs = durationMs,
            durationMinutes = durationMinutes,
            quality = qualityFrom(durationMinutes),
        )
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

        /** Minimum continuous screen-off duration to count as sleep (3 hours). */
        private const val MIN_SLEEP_MS = 3 * HOUR_MS

        /** Maximum plausible sleep duration (10 hours). Longer sessions are trimmed
         *  from the start, keeping the wake time accurate. */
        private const val MAX_SLEEP_MS = 10 * HOUR_MS

        /** Max gap between off-periods to still be considered one session (30 min). */
        private const val MERGE_GAP_MS = 30 * 60 * 1_000L
    }
}
