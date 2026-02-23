package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.QueryIntent

/**
 * Classifies a normalized query into one of the [QueryIntent] types.
 *
 * Uses keyword matching against predefined word lists for both
 * English and Hebrew. When multiple intents match, the one with
 * the highest keyword hit count wins. Falls back to [QueryIntent.SUMMARY_QUERY]
 * if no keywords match.
 */
class IntentClassifier {

    /**
     * Classifies the intent of a normalized query.
     *
     * @param normalizedText The preprocessed, lowercased query text.
     * @param language The detected query language.
     * @return The most likely [QueryIntent] for this query.
     */
    fun classify(normalizedText: String, language: Language): QueryIntent {
        val keywords = when (language) {
            Language.ENGLISH -> EN_KEYWORDS
            Language.HEBREW -> HE_KEYWORDS
        }

        val scores = mutableMapOf<QueryIntent, Int>()
        for ((intent, words) in keywords) {
            val score = words.count { normalizedText.contains(it) }
            if (score > 0) {
                scores[intent] = score
            }
        }

        return scores.maxByOrNull { it.value }?.key ?: QueryIntent.SUMMARY_QUERY
    }

    companion object {
        private val EN_KEYWORDS: Map<QueryIntent, List<String>> = mapOf(
            QueryIntent.LOCATION_QUERY to listOf(
                "where", "location", "place", "been", "go", "went", "visit",
                "near", "at the", "which place", "map",
            ),
            QueryIntent.ACTIVITY_QUERY to listOf(
                "what was i doing", "activity", "doing", "used", "app",
                "phone", "screen", "walking", "running", "driving",
            ),
            QueryIntent.TEMPORAL_QUERY to listOf(
                "when", "what time", "at what", "which day",
                "which date", "first time", "last time",
            ),
            QueryIntent.PATTERN_QUERY to listOf(
                "usually", "pattern", "habit", "often", "trend",
                "average", "typical", "normally", "routine",
            ),
            QueryIntent.PROOF_QUERY to listOf(
                "prove", "proof", "evidence", "alibi", "verify",
                "confirm", "show that", "demonstrate",
            ),
            QueryIntent.SUMMARY_QUERY to listOf(
                "summary", "summarize", "overview", "recap",
                "how was", "tell me about",
            ),
            QueryIntent.DURATION_QUERY to listOf(
                "how long", "duration", "time spent", "hours at",
                "minutes", "how much time",
            ),
            QueryIntent.COUNT_QUERY to listOf(
                "how many", "how often", "count", "times",
                "frequency", "number of",
            ),
        )

        private val HE_KEYWORDS: Map<QueryIntent, List<String>> = mapOf(
            QueryIntent.LOCATION_QUERY to listOf(
                "איפה", "מיקום", "מקום", "הייתי", "הלכתי", "ביקרתי",
                "ליד", "מפה",
            ),
            QueryIntent.ACTIVITY_QUERY to listOf(
                "מה עשיתי", "פעילות", "עשיתי", "השתמשתי", "אפליקציה",
                "טלפון", "מסך", "הליכה", "ריצה", "נהיגה",
            ),
            QueryIntent.TEMPORAL_QUERY to listOf(
                "מתי", "באיזה שעה", "באיזה יום", "באיזה תאריך",
                "פעם ראשונה", "פעם אחרונה",
            ),
            QueryIntent.PATTERN_QUERY to listOf(
                "בדרך כלל", "תבנית", "הרגל", "לעיתים", "מגמה",
                "ממוצע", "שגרה",
            ),
            QueryIntent.PROOF_QUERY to listOf(
                "הוכח", "הוכחה", "אליבי", "אמת", "אשר",
                "הראה", "תוכיח",
            ),
            QueryIntent.SUMMARY_QUERY to listOf(
                "סיכום", "סכם", "תקציר", "איך היה",
                "ספר לי על",
            ),
            QueryIntent.DURATION_QUERY to listOf(
                "כמה זמן", "משך", "זמן ש", "שעות ב",
                "דקות", "כמה זמן",
            ),
            QueryIntent.COUNT_QUERY to listOf(
                "כמה פעמים", "כמה", "ספירה", "תדירות",
                "מספר",
            ),
        )
    }
}
