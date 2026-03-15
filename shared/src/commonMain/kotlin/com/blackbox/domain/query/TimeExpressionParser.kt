package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.TimeRange
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Parses natural language time expressions into [TimeRange] instances.
 *
 * Supports both English and Hebrew expressions including:
 * - Relative: "yesterday", "last week", "3 days ago", "אתמול", "לפני שבוע"
 * - Compound: "yesterday morning/afternoon/evening", "אתמול בבוקר/אחה"צ/בערב"
 * - Named periods: "this morning", "last night", "הבוקר", "אמש"
 * - Day names: "on Monday", "last Tuesday", "ביום שני"
 *
 * All day boundaries are computed in the **device's local timezone** via
 * [kotlinx.datetime] so that "yesterday" means yesterday midnight–midnight
 * in the user's clock, not UTC midnight. This fixes off-by-offset bugs for
 * non-UTC locales (e.g. UTC+2 Israel would previously be 2 h off).
 *
 * @property currentTimeMs Supplier for the current time in epoch ms.
 */
class TimeExpressionParser(
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Parses a time expression from the given text.
     *
     * @param text The normalized query text to scan for time expressions.
     * @param language The detected language of the query.
     * @return A [TimeRange] if a time expression was found, or a default range (today).
     */
    fun parse(text: String, language: Language): TimeRange {
        val lower = text.lowercase()
        val now = currentTimeMs()
        val todayStart = startOfDay(now)
        val todayEnd = todayStart + DAY_MS - 1

        return when (language) {
            Language.HEBREW -> parseHebrew(lower, now, todayStart, todayEnd)
            Language.ENGLISH -> parseEnglish(lower, now, todayStart, todayEnd)
        } ?: TimeRange(todayStart, todayEnd)
    }

    private fun parseEnglish(
        text: String,
        now: Long,
        todayStart: Long,
        todayEnd: Long,
    ): TimeRange? {
        // "right now" / "currently"
        if (text.contains("right now") || text.contains("currently")) {
            return TimeRange(now - FIFTEEN_MIN_MS, now)
        }

        // "today [morning/afternoon/evening]"
        if (text.contains("today")) {
            return when {
                text.contains("morning") -> TimeRange(todayStart + 5 * HOUR_MS, todayStart + 12 * HOUR_MS)
                text.contains("afternoon") || text.contains("noon") -> TimeRange(todayStart + 12 * HOUR_MS, todayStart + 18 * HOUR_MS)
                text.contains("evening") || (text.contains("night") && !text.contains("last night")) -> TimeRange(todayStart + 18 * HOUR_MS, todayEnd)
                else -> TimeRange(todayStart, todayEnd)
            }
        }

        // "yesterday [morning/afternoon/evening/night]"
        // Compound expressions must be detected BEFORE the plain "yesterday" branch.
        if (text.contains("yesterday")) {
            val yesterdayStart = startOfDay(todayStart - HOUR_MS) // midpoint in local yesterday
            return when {
                text.contains("morning") -> TimeRange(yesterdayStart + 5 * HOUR_MS, yesterdayStart + 12 * HOUR_MS)
                text.contains("afternoon") || text.contains("noon") -> TimeRange(yesterdayStart + 12 * HOUR_MS, yesterdayStart + 18 * HOUR_MS)
                text.contains("evening") || text.contains("night") -> TimeRange(yesterdayStart + 18 * HOUR_MS, yesterdayStart + DAY_MS - 1)
                else -> TimeRange(yesterdayStart, yesterdayStart + DAY_MS - 1)
            }
        }

        // "last morning/afternoon/evening/night" — "last" without "yesterday" or "week/month"
        // Users say "last afternoon" to mean "yesterday afternoon"; must check BEFORE "last week/month".
        if (text.contains("last")) {
            val yesterdayStart = startOfDay(todayStart - HOUR_MS)
            when {
                text.contains("afternoon") || text.contains("noon") ->
                    return TimeRange(yesterdayStart + 12 * HOUR_MS, yesterdayStart + 18 * HOUR_MS)
                text.contains("morning") ->
                    return TimeRange(yesterdayStart + 5 * HOUR_MS, yesterdayStart + 12 * HOUR_MS)
                text.contains("evening") ->
                    return TimeRange(yesterdayStart + 18 * HOUR_MS, yesterdayStart + DAY_MS - 1)
                // "last night" is handled separately below — skip here
            }
        }

        // "in the last N / past N hours/days/minutes"
        LAST_N_PATTERN_EN.find(text)?.let { match ->
            val amount = parseWordOrDigit(match.groupValues[1]) ?: return@let
            val unit = match.groupValues[2]
            val ms = when {
                unit.startsWith("minute") -> amount * MINUTE_MS
                unit.startsWith("hour") -> amount * HOUR_MS
                unit.startsWith("day") -> amount * DAY_MS
                unit.startsWith("week") -> amount * WEEK_MS
                unit.startsWith("month") -> amount * 30 * DAY_MS
                else -> return@let
            }
            return TimeRange(now - ms, now)
        }

        // "N days/hours/minutes ago" (word numbers supported: "two days ago")
        RELATIVE_PATTERN_EN.find(text)?.let { match ->
            val amount = parseWordOrDigit(match.groupValues[1]) ?: return@let
            val unit = match.groupValues[2]
            val ms = when {
                unit.startsWith("minute") -> amount * MINUTE_MS
                unit.startsWith("hour") -> amount * HOUR_MS
                unit.startsWith("day") -> amount * DAY_MS
                unit.startsWith("week") -> amount * WEEK_MS
                unit.startsWith("month") -> amount * 30 * DAY_MS
                else -> return@let
            }
            return TimeRange(now - ms, now)
        }

        // "last night"
        if (text.contains("last night")) {
            val yesterdayStart = startOfDay(todayStart - HOUR_MS)
            return TimeRange(yesterdayStart + 20 * HOUR_MS, todayStart - 1)
        }

        // "this morning"
        if (text.contains("this morning")) {
            return TimeRange(todayStart + 5 * HOUR_MS, todayStart + 12 * HOUR_MS)
        }

        // "this afternoon"
        if (text.contains("this afternoon")) {
            return TimeRange(todayStart + 12 * HOUR_MS, todayStart + 18 * HOUR_MS)
        }

        // "this evening" / "tonight"
        if (text.contains("this evening") || text.contains("tonight")) {
            return TimeRange(todayStart + 18 * HOUR_MS, todayEnd)
        }

        // "last week"
        if (text.contains("last week")) {
            return TimeRange(todayStart - 7 * DAY_MS, todayStart - 1)
        }

        // "this week"
        if (text.contains("this week")) {
            val weekStart = todayStart - (dayOfWeek(now) * DAY_MS)
            return TimeRange(weekStart, todayEnd)
        }

        // "last month"
        if (text.contains("last month")) {
            return TimeRange(todayStart - 30 * DAY_MS, todayStart - 1)
        }

        // "this month"
        if (text.contains("this month")) {
            return TimeRange(todayStart - dayOfMonth(now) * DAY_MS, todayEnd)
        }

        // Day names: "on monday", "last tuesday", etc.
        for ((dayName, dayIndex) in EN_DAY_NAMES) {
            if (text.contains(dayName)) {
                val targetDay = findPreviousDay(now, dayIndex)
                return TimeRange(targetDay, targetDay + DAY_MS - 1)
            }
        }

        return null
    }

    private fun parseHebrew(
        text: String,
        now: Long,
        todayStart: Long,
        todayEnd: Long,
    ): TimeRange? {
        // "עכשיו" (now)
        if (text.contains("עכשיו")) {
            return TimeRange(now - FIFTEEN_MIN_MS, now)
        }

        // "היום" (today) — with optional time-of-day
        if (text.contains("היום")) {
            return when {
                text.contains("בבוקר") || text.contains("הבוקר") -> TimeRange(todayStart + 5 * HOUR_MS, todayStart + 12 * HOUR_MS)
                text.contains("אחר הצהריים") || text.contains("אחהצ") || text.contains("אחה\"צ") -> TimeRange(todayStart + 12 * HOUR_MS, todayStart + 18 * HOUR_MS)
                text.contains("בערב") || text.contains("הערב") -> TimeRange(todayStart + 18 * HOUR_MS, todayEnd)
                else -> TimeRange(todayStart, todayEnd)
            }
        }

        // "אתמול" (yesterday) — with optional time-of-day compound
        if (text.contains("אתמול")) {
            val yesterdayStart = startOfDay(todayStart - HOUR_MS)
            return when {
                text.contains("בבוקר") || text.contains("הבוקר") -> TimeRange(yesterdayStart + 5 * HOUR_MS, yesterdayStart + 12 * HOUR_MS)
                text.contains("אחר הצהריים") || text.contains("אחהצ") || text.contains("אחה\"צ") -> TimeRange(yesterdayStart + 12 * HOUR_MS, yesterdayStart + 18 * HOUR_MS)
                text.contains("בערב") || text.contains("בלילה") -> TimeRange(yesterdayStart + 18 * HOUR_MS, yesterdayStart + DAY_MS - 1)
                else -> TimeRange(yesterdayStart, yesterdayStart + DAY_MS - 1)
            }
        }

        // "אמש" (last night)
        if (text.contains("אמש")) {
            val yesterdayStart = startOfDay(todayStart - HOUR_MS)
            return TimeRange(yesterdayStart + 20 * HOUR_MS, todayStart - 1)
        }

        // "הבוקר" (this morning)
        if (text.contains("הבוקר")) {
            return TimeRange(todayStart + 5 * HOUR_MS, todayStart + 12 * HOUR_MS)
        }

        // "אחר הצהריים" / "אחה״צ" (this afternoon)
        if (text.contains("אחר הצהריים") || text.contains("אחה״צ") || text.contains("אחהצ")) {
            return TimeRange(todayStart + 12 * HOUR_MS, todayStart + 18 * HOUR_MS)
        }

        // "הערב" (this evening)
        if (text.contains("הערב")) {
            return TimeRange(todayStart + 18 * HOUR_MS, todayEnd)
        }

        // "לפני N ימים/שעות/דקות" (N days/hours/minutes ago)
        RELATIVE_PATTERN_HE.find(text)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let
            val unit = match.groupValues[2]
            val ms = when {
                unit.startsWith("דק") -> amount * MINUTE_MS
                unit.startsWith("שע") -> amount * HOUR_MS
                unit.startsWith("יו") || unit.startsWith("ימ") -> amount * DAY_MS
                unit.startsWith("שבוע") || unit.startsWith("שבועו") -> amount * WEEK_MS
                unit.startsWith("חודש") || unit.startsWith("חדש") -> amount * 30 * DAY_MS
                else -> return@let
            }
            return TimeRange(now - ms, now)
        }

        // "שבוע שעבר" (last week)
        if (text.contains("שבוע שעבר")) {
            return TimeRange(todayStart - 7 * DAY_MS, todayStart - 1)
        }

        // "השבוע" (this week)
        if (text.contains("השבוע")) {
            val weekStart = todayStart - (dayOfWeek(now) * DAY_MS)
            return TimeRange(weekStart, todayEnd)
        }

        // "חודש שעבר" (last month)
        if (text.contains("חודש שעבר")) {
            return TimeRange(todayStart - 30 * DAY_MS, todayStart - 1)
        }

        // Hebrew day names
        for ((dayName, dayIndex) in HE_DAY_NAMES) {
            if (text.contains(dayName)) {
                val targetDay = findPreviousDay(now, dayIndex)
                return TimeRange(targetDay, targetDay + DAY_MS - 1)
            }
        }

        return null
    }

    /**
     * Parses a number that may be written as a digit string or English word.
     * Returns null if unrecognized.
     */
    private fun parseWordOrDigit(token: String): Long? = when (token.trim()) {
        "a", "an", "one" -> 1L
        "two" -> 2L
        "three" -> 3L
        "four" -> 4L
        "five" -> 5L
        "six" -> 6L
        "seven" -> 7L
        "eight" -> 8L
        "nine" -> 9L
        "ten" -> 10L
        else -> token.toLongOrNull()
    }

    /**
     * Returns the start of the local calendar day (midnight in the device's timezone)
     * for the given epoch ms.
     *
     * Uses [kotlinx.datetime] rather than UTC modulo so the result is correct
     * for any UTC offset — avoids the UTC-midnight vs local-midnight mismatch.
     */
    private fun startOfDay(epochMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val localDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        return localDate.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /**
     * Returns 0 = Sunday through 6 = Saturday for the given epoch ms,
     * evaluated in the device's local timezone.
     */
    private fun dayOfWeek(epochMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val localDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        // kotlinx.datetime DayOfWeek ordinal: MONDAY=0 … SUNDAY=6
        // We want SUNDAY=0, MONDAY=1 … SATURDAY=6 → (ordinal + 1) % 7
        return ((localDate.dayOfWeek.ordinal + 1) % 7).toLong()
    }

    /** Returns 0-based day of month (0–30) in local timezone. */
    private fun dayOfMonth(epochMs: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val localDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        return (localDate.dayOfMonth - 1).toLong()
    }

    /** Finds the start-of-day for the most recent occurrence of a day-of-week (local tz). */
    private fun findPreviousDay(now: Long, targetDayOfWeek: Int): Long {
        val todayStart = startOfDay(now)
        val currentDow = dayOfWeek(now)
        val daysBack = if (currentDow >= targetDayOfWeek) {
            currentDow - targetDayOfWeek
        } else {
            7 - (targetDayOfWeek - currentDow)
        }
        val actualDaysBack = if (daysBack == 0L) 7L else daysBack
        return todayStart - (actualDaysBack * DAY_MS)
    }

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val FIFTEEN_MIN_MS = 15 * MINUTE_MS
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        private const val WEEK_MS = 7 * DAY_MS

        /** Matches "two days ago", "3 hours ago", "a week ago". */
        private val RELATIVE_PATTERN_EN = Regex("""(a\b|\d+|\b(?:one|two|three|four|five|six|seven|eight|nine|ten))\s+(minute|hour|day|week|month)s?\s+ago""")

        /** Matches "in the last 3 hours", "past two days", "last 24 hours". */
        private val LAST_N_PATTERN_EN = Regex("""(?:in\s+the\s+last|past|last)\s+(a\b|\d+|\b(?:one|two|three|four|five|six|seven|eight|nine|ten))\s+(minute|hour|day|week|month)s?\b""")
        private val RELATIVE_PATTERN_HE = Regex("""לפני\s+(\d+)\s+(דקות?|שעות?|ימים?|יום|שבועות?|חודשים?|חדשים?)""")

        /** English day names → day-of-week (0=Sun). */
        private val EN_DAY_NAMES = listOf(
            "sunday" to 0, "monday" to 1, "tuesday" to 2,
            "wednesday" to 3, "thursday" to 4, "friday" to 5, "saturday" to 6,
        )

        /** Hebrew day names → day-of-week (0=Sun). */
        private val HE_DAY_NAMES = listOf(
            "ראשון" to 0, "שני" to 1, "שלישי" to 2,
            "רביעי" to 3, "חמישי" to 4, "שישי" to 5, "שבת" to 6,
        )
    }
}
