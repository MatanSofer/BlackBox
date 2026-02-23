package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.TimeRange

/**
 * Parses natural language time expressions into [TimeRange] instances.
 *
 * Supports both English and Hebrew expressions including:
 * - Relative: "yesterday", "last week", "3 days ago", "אתמול", "לפני שבוע"
 * - Named periods: "this morning", "last night", "הבוקר", "אמש"
 * - Day names: "on Monday", "last Tuesday", "ביום שני"
 *
 * All computations are relative to [currentTimeMs], which defaults to
 * the system clock but can be overridden for testing.
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

        // "today"
        if (text.contains("today")) {
            return TimeRange(todayStart, todayEnd)
        }

        // "yesterday"
        if (text.contains("yesterday")) {
            return TimeRange(todayStart - DAY_MS, todayStart - 1)
        }

        // "N days ago" / "N hours ago" / "N minutes ago"
        RELATIVE_PATTERN_EN.find(text)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let
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
            val yesterdayStart = todayStart - DAY_MS
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

        // "היום" (today)
        if (text.contains("היום")) {
            return TimeRange(todayStart, todayEnd)
        }

        // "אתמול" (yesterday)
        if (text.contains("אתמול")) {
            return TimeRange(todayStart - DAY_MS, todayStart - 1)
        }

        // "אמש" (last night)
        if (text.contains("אמש")) {
            val yesterdayStart = todayStart - DAY_MS
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

    /** Returns the start of day (00:00:00.000) for the given epoch ms. */
    private fun startOfDay(epochMs: Long): Long {
        return epochMs - (epochMs % DAY_MS)
    }

    /** Returns 0=Sunday through 6=Saturday for the given epoch ms. */
    private fun dayOfWeek(epochMs: Long): Int {
        // Jan 1 1970 was a Thursday (4)
        return ((epochMs / DAY_MS + 4) % 7).toInt()
    }

    /** Returns 0-based day of month (approximate). */
    private fun dayOfMonth(epochMs: Long): Long {
        val daysSinceEpoch = epochMs / DAY_MS
        return daysSinceEpoch % 30
    }

    /** Finds the start-of-day for the most recent occurrence of a day-of-week. */
    private fun findPreviousDay(now: Long, targetDayOfWeek: Int): Long {
        val todayStart = startOfDay(now)
        val currentDow = dayOfWeek(now)
        val daysBack = if (currentDow >= targetDayOfWeek) {
            currentDow - targetDayOfWeek
        } else {
            7 - (targetDayOfWeek - currentDow)
        }
        // If daysBack is 0 (same day), go back 7 days
        val actualDaysBack = if (daysBack == 0) 7 else daysBack
        return todayStart - (actualDaysBack * DAY_MS)
    }

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val FIFTEEN_MIN_MS = 15 * MINUTE_MS
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L
        private const val WEEK_MS = 7 * DAY_MS

        private val RELATIVE_PATTERN_EN = Regex("""(\d+)\s+(minute|hour|day|week|month)s?\s+ago""")
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
