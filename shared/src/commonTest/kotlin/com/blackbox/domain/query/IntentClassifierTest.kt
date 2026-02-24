package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.QueryIntent
import kotlin.test.Test
import kotlin.test.assertEquals

class IntentClassifierTest {

    private val classifier = IntentClassifier()

    // ── English intent classification ──

    @Test
    fun `where was I yesterday — LOCATION_QUERY`() {
        assertEquals(
            QueryIntent.LOCATION_QUERY,
            classifier.classify("where was i yesterday", Language.ENGLISH),
        )
    }

    @Test
    fun `which places did I visit — LOCATION_QUERY`() {
        assertEquals(
            QueryIntent.LOCATION_QUERY,
            classifier.classify("which places did i visit last week", Language.ENGLISH),
        )
    }

    @Test
    fun `show me the map — LOCATION_QUERY`() {
        assertEquals(
            QueryIntent.LOCATION_QUERY,
            classifier.classify("show me the map of today", Language.ENGLISH),
        )
    }

    @Test
    fun `what was I doing — ACTIVITY_QUERY`() {
        assertEquals(
            QueryIntent.ACTIVITY_QUERY,
            classifier.classify("what was i doing at 3pm", Language.ENGLISH),
        )
    }

    @Test
    fun `which app did I use — ACTIVITY_QUERY`() {
        assertEquals(
            QueryIntent.ACTIVITY_QUERY,
            classifier.classify("which app did i use most", Language.ENGLISH),
        )
    }

    @Test
    fun `was I walking or driving — ACTIVITY_QUERY`() {
        assertEquals(
            QueryIntent.ACTIVITY_QUERY,
            classifier.classify("was i walking or driving", Language.ENGLISH),
        )
    }

    @Test
    fun `when did I leave — TEMPORAL_QUERY`() {
        assertEquals(
            QueryIntent.TEMPORAL_QUERY,
            classifier.classify("when did i leave the office", Language.ENGLISH),
        )
    }

    @Test
    fun `what time did I wake up — TEMPORAL_QUERY`() {
        assertEquals(
            QueryIntent.TEMPORAL_QUERY,
            classifier.classify("what time did i wake up", Language.ENGLISH),
        )
    }

    @Test
    fun `what is my usual pattern — PATTERN_QUERY`() {
        assertEquals(
            QueryIntent.PATTERN_QUERY,
            classifier.classify("what is my usual pattern normally", Language.ENGLISH),
        )
    }

    @Test
    fun `what is my routine — PATTERN_QUERY`() {
        assertEquals(
            QueryIntent.PATTERN_QUERY,
            classifier.classify("what is my typical routine", Language.ENGLISH),
        )
    }

    @Test
    fun `prove and verify — PROOF_QUERY`() {
        assertEquals(
            QueryIntent.PROOF_QUERY,
            classifier.classify("prove and verify my alibi", Language.ENGLISH),
        )
    }

    @Test
    fun `show evidence and confirm — PROOF_QUERY`() {
        assertEquals(
            QueryIntent.PROOF_QUERY,
            classifier.classify("show evidence to confirm", Language.ENGLISH),
        )
    }

    @Test
    fun `summarize my day — SUMMARY_QUERY`() {
        assertEquals(
            QueryIntent.SUMMARY_QUERY,
            classifier.classify("summarize my day", Language.ENGLISH),
        )
    }

    @Test
    fun `how was my week — SUMMARY_QUERY`() {
        assertEquals(
            QueryIntent.SUMMARY_QUERY,
            classifier.classify("how was my week", Language.ENGLISH),
        )
    }

    @Test
    fun `how long duration — DURATION_QUERY`() {
        assertEquals(
            QueryIntent.DURATION_QUERY,
            classifier.classify("how long was the duration of my commute", Language.ENGLISH),
        )
    }

    @Test
    fun `how much time spent — DURATION_QUERY`() {
        assertEquals(
            QueryIntent.DURATION_QUERY,
            classifier.classify("how much time did i spend on this", Language.ENGLISH),
        )
    }

    @Test
    fun `how many times — COUNT_QUERY`() {
        assertEquals(
            QueryIntent.COUNT_QUERY,
            classifier.classify("how many times did i go to the gym", Language.ENGLISH),
        )
    }

    @Test
    fun `how often frequency — COUNT_QUERY`() {
        assertEquals(
            QueryIntent.COUNT_QUERY,
            classifier.classify("how often is the frequency of this", Language.ENGLISH),
        )
    }

    @Test
    fun `no keywords — defaults to SUMMARY_QUERY`() {
        assertEquals(
            QueryIntent.SUMMARY_QUERY,
            classifier.classify("hello there", Language.ENGLISH),
        )
    }

    // ── Hebrew intent classification ──

    @Test
    fun `Hebrew where was I — LOCATION_QUERY`() {
        assertEquals(
            QueryIntent.LOCATION_QUERY,
            classifier.classify("איפה הייתי אתמול", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew what was I doing — ACTIVITY_QUERY`() {
        assertEquals(
            QueryIntent.ACTIVITY_QUERY,
            classifier.classify("מה עשיתי אתמול בערב", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew when — TEMPORAL_QUERY`() {
        assertEquals(
            QueryIntent.TEMPORAL_QUERY,
            classifier.classify("מתי הגעתי הביתה", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew usually pattern — PATTERN_QUERY`() {
        assertEquals(
            QueryIntent.PATTERN_QUERY,
            classifier.classify("בדרך כלל מה השגרה שלי", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew prove alibi — PROOF_QUERY`() {
        assertEquals(
            QueryIntent.PROOF_QUERY,
            classifier.classify("תוכיח את האליבי שלי", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew summary — SUMMARY_QUERY`() {
        assertEquals(
            QueryIntent.SUMMARY_QUERY,
            classifier.classify("סיכום של היום", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew how long — DURATION_QUERY`() {
        assertEquals(
            QueryIntent.DURATION_QUERY,
            classifier.classify("כמה זמן הייתי במשרד", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew how many times — COUNT_QUERY`() {
        assertEquals(
            QueryIntent.COUNT_QUERY,
            classifier.classify("כמה פעמים הייתי בחדר כושר", Language.HEBREW),
        )
    }

    @Test
    fun `Hebrew no keywords — defaults to SUMMARY_QUERY`() {
        assertEquals(
            QueryIntent.SUMMARY_QUERY,
            classifier.classify("שלום", Language.HEBREW),
        )
    }
}
