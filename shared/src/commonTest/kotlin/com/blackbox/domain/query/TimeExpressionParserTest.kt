package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeExpressionParserTest {

    // Fixed "now" = 2026-02-24 12:00:00 UTC (a Tuesday)
    // Epoch ms for 2026-02-24 12:00:00 UTC = 1771934400000
    private val fixedNow = 1_771_934_400_000L
    private val parser = TimeExpressionParser(currentTimeMs = { fixedNow })

    private val dayMs = 86_400_000L
    private val hourMs = 3_600_000L
    private val minuteMs = 60_000L

    // Start of 2026-02-24 UTC
    private val todayStart = fixedNow - (fixedNow % dayMs)
    private val todayEnd = todayStart + dayMs - 1

    // ── English time expressions ──

    @Test
    fun `today — returns today range`() {
        val result = parser.parse("where was i today", Language.ENGLISH)
        assertEquals(todayStart, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }

    @Test
    fun `yesterday — returns yesterday range`() {
        val result = parser.parse("what did i do yesterday", Language.ENGLISH)
        assertEquals(todayStart - dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `right now — returns last 15 minutes`() {
        val result = parser.parse("where am i right now", Language.ENGLISH)
        assertEquals(fixedNow - 15 * minuteMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `3 days ago — returns last 3 days`() {
        val result = parser.parse("what happened 3 days ago", Language.ENGLISH)
        assertEquals(fixedNow - 3 * dayMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `2 hours ago — returns last 2 hours`() {
        val result = parser.parse("where was i 2 hours ago", Language.ENGLISH)
        assertEquals(fixedNow - 2 * hourMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `30 minutes ago — returns last 30 minutes`() {
        val result = parser.parse("what was happening 30 minutes ago", Language.ENGLISH)
        assertEquals(fixedNow - 30 * minuteMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `1 week ago — returns last week`() {
        val result = parser.parse("check 1 week ago", Language.ENGLISH)
        assertEquals(fixedNow - 7 * dayMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `this morning — returns 5am to noon today`() {
        val result = parser.parse("what happened this morning", Language.ENGLISH)
        assertEquals(todayStart + 5 * hourMs, result.startEpochMs)
        assertEquals(todayStart + 12 * hourMs, result.endEpochMs)
    }

    @Test
    fun `this afternoon — returns noon to 6pm today`() {
        val result = parser.parse("where was i this afternoon", Language.ENGLISH)
        assertEquals(todayStart + 12 * hourMs, result.startEpochMs)
        assertEquals(todayStart + 18 * hourMs, result.endEpochMs)
    }

    @Test
    fun `this evening — returns 6pm to end of day`() {
        val result = parser.parse("plans for this evening", Language.ENGLISH)
        assertEquals(todayStart + 18 * hourMs, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }

    @Test
    fun `last night — returns 8pm yesterday to midnight`() {
        val yesterdayStart = todayStart - dayMs
        val result = parser.parse("where was i last night", Language.ENGLISH)
        assertEquals(yesterdayStart + 20 * hourMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `last week — returns 7 days before today`() {
        val result = parser.parse("summary of last week", Language.ENGLISH)
        assertEquals(todayStart - 7 * dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `last month — returns 30 days before today`() {
        val result = parser.parse("what happened last month", Language.ENGLISH)
        assertEquals(todayStart - 30 * dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `no time expression — defaults to today`() {
        val result = parser.parse("hello world", Language.ENGLISH)
        assertEquals(todayStart, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }

    @Test
    fun `time range has positive duration`() {
        val result = parser.parse("yesterday", Language.ENGLISH)
        assertTrue(result.durationMs > 0)
    }

    // ── Hebrew time expressions ──

    @Test
    fun `Hebrew today — returns today range`() {
        val result = parser.parse("איפה הייתי היום", Language.HEBREW)
        assertEquals(todayStart, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }

    @Test
    fun `Hebrew yesterday — returns yesterday range`() {
        val result = parser.parse("מה עשיתי אתמול", Language.HEBREW)
        assertEquals(todayStart - dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `Hebrew now — returns last 15 minutes`() {
        val result = parser.parse("איפה אני עכשיו", Language.HEBREW)
        assertEquals(fixedNow - 15 * minuteMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `Hebrew last night — returns 8pm yesterday to midnight`() {
        val yesterdayStart = todayStart - dayMs
        val result = parser.parse("מה קרה אמש", Language.HEBREW)
        assertEquals(yesterdayStart + 20 * hourMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `Hebrew this morning — returns 5am to noon`() {
        val result = parser.parse("מה עשיתי הבוקר", Language.HEBREW)
        assertEquals(todayStart + 5 * hourMs, result.startEpochMs)
        assertEquals(todayStart + 12 * hourMs, result.endEpochMs)
    }

    @Test
    fun `Hebrew this evening — returns 6pm to end of day`() {
        val result = parser.parse("מה קורה הערב", Language.HEBREW)
        assertEquals(todayStart + 18 * hourMs, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }

    @Test
    fun `Hebrew 3 days ago — returns last 3 days`() {
        val result = parser.parse("מה קרה לפני 3 ימים", Language.HEBREW)
        assertEquals(fixedNow - 3 * dayMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `Hebrew 2 hours ago — returns last 2 hours`() {
        val result = parser.parse("איפה הייתי לפני 2 שעות", Language.HEBREW)
        assertEquals(fixedNow - 2 * hourMs, result.startEpochMs)
        assertEquals(fixedNow, result.endEpochMs)
    }

    @Test
    fun `Hebrew last week — returns 7 days before today`() {
        val result = parser.parse("סיכום שבוע שעבר", Language.HEBREW)
        assertEquals(todayStart - 7 * dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `Hebrew last month — returns 30 days before today`() {
        val result = parser.parse("מה קרה חודש שעבר", Language.HEBREW)
        assertEquals(todayStart - 30 * dayMs, result.startEpochMs)
        assertEquals(todayStart - 1, result.endEpochMs)
    }

    @Test
    fun `Hebrew no time expression — defaults to today`() {
        val result = parser.parse("שלום עולם", Language.HEBREW)
        assertEquals(todayStart, result.startEpochMs)
        assertEquals(todayEnd, result.endEpochMs)
    }
}
