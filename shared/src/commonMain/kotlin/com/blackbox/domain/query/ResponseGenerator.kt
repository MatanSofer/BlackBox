package com.blackbox.domain.query

import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.ParsedQuery
import com.blackbox.domain.model.query.QueryIntent
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.repository.LocationEntry

/**
 * Generates human-readable response text from raw query results.
 *
 * Formats the [QueryData] into a natural language response based on
 * the query intent and language. Also generates suggested follow-up
 * queries to guide the user toward deeper exploration.
 */
class ResponseGenerator {

    /**
     * Generates a response for the given query and data.
     *
     * @param parsedQuery The parsed user query.
     * @param data The raw data retrieved by the [QueryBuilder].
     * @return A [GeneratedResponse] with formatted text and suggestions.
     */
    fun generate(parsedQuery: ParsedQuery, data: QueryData): GeneratedResponse {
        if (data.records.isEmpty() && data.locations.isEmpty()) {
            return noDataResponse(parsedQuery.language)
        }

        val responseText = when (parsedQuery.intent) {
            QueryIntent.LOCATION_QUERY -> generateLocationResponse(data, parsedQuery.language)
            QueryIntent.ACTIVITY_QUERY -> generateActivityResponse(data, parsedQuery.language)
            QueryIntent.TEMPORAL_QUERY -> generateTemporalResponse(data, parsedQuery.language)
            QueryIntent.DURATION_QUERY -> generateDurationResponse(data, parsedQuery.language)
            QueryIntent.COUNT_QUERY -> generateCountResponse(data, parsedQuery.language)
            QueryIntent.PROOF_QUERY -> generateProofResponse(data, parsedQuery.language)
            QueryIntent.SUMMARY_QUERY -> generateSummaryResponse(data, parsedQuery.language)
            QueryIntent.PATTERN_QUERY -> generatePatternResponse(data, parsedQuery.language)
        }

        val sourceCount = data.records
            .map { it.collectorType }
            .distinct()
            .count()

        val confidence = computeConfidence(data, sourceCount)
        val followUps = generateFollowUps(parsedQuery)

        return GeneratedResponse(
            text = responseText,
            suggestedFollowUps = followUps,
            confidence = confidence,
            sourceCount = sourceCount,
        )
    }

    private fun generateLocationResponse(data: QueryData, language: Language): String {
        val locations = data.locations
        if (locations.isEmpty()) {
            return if (language == Language.HEBREW) {
                "לא נמצאו נתוני מיקום בטווח הזמן המבוקש."
            } else {
                "No location data found for the requested time range."
            }
        }

        val uniqueLocations = locations.distinctBy {
            "${roundCoord(it.latitude)},${roundCoord(it.longitude)}"
        }

        return if (language == Language.HEBREW) {
            buildString {
                append("נמצאו ${locations.size} נקודות מיקום")
                append(" ב-${uniqueLocations.size} מקומות שונים.")
                val first = locations.first()
                append("\nמיקום ראשון: (${formatCoord(first.latitude)}, ${formatCoord(first.longitude)})")
                if (locations.size > 1) {
                    val last = locations.last()
                    append("\nמיקום אחרון: (${formatCoord(last.latitude)}, ${formatCoord(last.longitude)})")
                }
            }
        } else {
            buildString {
                append("Found ${locations.size} location points")
                append(" across ${uniqueLocations.size} distinct places.")
                val first = locations.first()
                append("\nFirst location: (${formatCoord(first.latitude)}, ${formatCoord(first.longitude)})")
                if (locations.size > 1) {
                    val last = locations.last()
                    append("\nLast location: (${formatCoord(last.latitude)}, ${formatCoord(last.longitude)})")
                }
            }
        }
    }

    private fun generateActivityResponse(data: QueryData, language: Language): String {
        val activityRecords = data.records.filter { it.collectorType == CollectorType.ACTIVITY }
        val appRecords = data.records.filter { it.collectorType == CollectorType.APP_USAGE }

        return if (language == Language.HEBREW) {
            buildString {
                if (activityRecords.isNotEmpty()) {
                    append("נמצאו ${activityRecords.size} רשומות פעילות.")
                    val activities = activityRecords.mapNotNull {
                        (it.data as? RecordData.Activity)?.activityData?.detectedActivity?.name
                    }.groupingBy { it }.eachCount()
                    activities.forEach { (activity, count) ->
                        append("\n  $activity: $count פעמים")
                    }
                }
                if (appRecords.isNotEmpty()) {
                    if (activityRecords.isNotEmpty()) append("\n")
                    append("נמצאו ${appRecords.size} רשומות שימוש באפליקציות.")
                }
            }
        } else {
            buildString {
                if (activityRecords.isNotEmpty()) {
                    append("Found ${activityRecords.size} activity records.")
                    val activities = activityRecords.mapNotNull {
                        (it.data as? RecordData.Activity)?.activityData?.detectedActivity?.name
                    }.groupingBy { it }.eachCount()
                    activities.forEach { (activity, count) ->
                        append("\n  $activity: $count times")
                    }
                }
                if (appRecords.isNotEmpty()) {
                    if (activityRecords.isNotEmpty()) append("\n")
                    append("Found ${appRecords.size} app usage records.")
                }
            }
        }
    }

    private fun generateTemporalResponse(data: QueryData, language: Language): String {
        val count = data.records.size
        return if (language == Language.HEBREW) {
            "נמצאו $count רשומות בטווח הזמן המבוקש."
        } else {
            "Found $count records in the requested time range."
        }
    }

    private fun generateDurationResponse(data: QueryData, language: Language): String {
        val records = data.records
        if (records.isEmpty()) return noDataText(language)

        val durationMs = if (records.size >= 2) {
            records.last().timestamp - records.first().timestamp
        } else {
            0L
        }

        val hours = durationMs / 3_600_000
        val minutes = (durationMs % 3_600_000) / 60_000

        return if (language == Language.HEBREW) {
            "משך הזמן: ${hours} שעות ו-${minutes} דקות (${records.size} רשומות)."
        } else {
            "Duration: ${hours}h ${minutes}m (based on ${records.size} records)."
        }
    }

    private fun generateCountResponse(data: QueryData, language: Language): String {
        val count = data.records.size
        val byType = data.records.groupingBy { it.collectorType }.eachCount()

        return if (language == Language.HEBREW) {
            buildString {
                append("נמצאו $count רשומות.")
                byType.forEach { (type, c) ->
                    append("\n  ${type.name}: $c")
                }
            }
        } else {
            buildString {
                append("Found $count records total.")
                byType.forEach { (type, c) ->
                    append("\n  ${type.name}: $c")
                }
            }
        }
    }

    private fun generateProofResponse(data: QueryData, language: Language): String {
        val sourceTypes = data.records.map { it.collectorType }.distinct()
        val locationCount = data.locations.size

        return if (language == Language.HEBREW) {
            buildString {
                append("נמצאו ראיות מ-${sourceTypes.size} מקורות נתונים.")
                if (locationCount > 0) append("\n  מיקום: $locationCount נקודות")
                sourceTypes.forEach { type ->
                    val count = data.records.count { it.collectorType == type }
                    append("\n  ${type.name}: $count רשומות")
                }
            }
        } else {
            buildString {
                append("Found evidence from ${sourceTypes.size} data sources.")
                if (locationCount > 0) append("\n  Location: $locationCount points")
                sourceTypes.forEach { type ->
                    val count = data.records.count { it.collectorType == type }
                    append("\n  ${type.name}: $count records")
                }
            }
        }
    }

    private fun generateSummaryResponse(data: QueryData, language: Language): String {
        val byType = data.records.groupingBy { it.collectorType }.eachCount()
        val locationCount = data.locations.size

        return if (language == Language.HEBREW) {
            buildString {
                append("סיכום: ${data.records.size} רשומות מ-${byType.size} סוגי אוספים.")
                if (locationCount > 0) append("\n  מיקום: $locationCount נקודות")
                byType.forEach { (type, count) ->
                    append("\n  ${type.name}: $count")
                }
            }
        } else {
            buildString {
                append("Summary: ${data.records.size} records from ${byType.size} collector types.")
                if (locationCount > 0) append("\n  Location: $locationCount points")
                byType.forEach { (type, count) ->
                    append("\n  ${type.name}: $count")
                }
            }
        }
    }

    private fun generatePatternResponse(data: QueryData, language: Language): String {
        return if (language == Language.HEBREW) {
            "ניתוח דפוסים: נמצאו ${data.records.size} רשומות לניתוח."
        } else {
            "Pattern analysis: Found ${data.records.size} records to analyze."
        }
    }

    private fun generateFollowUps(parsedQuery: ParsedQuery): List<String> {
        val isHebrew = parsedQuery.language == Language.HEBREW
        return when (parsedQuery.intent) {
            QueryIntent.LOCATION_QUERY -> if (isHebrew) {
                listOf("כמה זמן הייתי שם?", "מה עשיתי שם?", "הראה על המפה")
            } else {
                listOf("How long was I there?", "What was I doing there?", "Show on map")
            }
            QueryIntent.ACTIVITY_QUERY -> if (isHebrew) {
                listOf("איפה הייתי בזמן הזה?", "כמה זמן?", "סכם את היום")
            } else {
                listOf("Where was I during this?", "How long?", "Summarize the day")
            }
            QueryIntent.SUMMARY_QUERY -> if (isHebrew) {
                listOf("איפה הייתי?", "מה עשיתי?", "הראה על המפה")
            } else {
                listOf("Where was I?", "What was I doing?", "Show on map")
            }
            else -> if (isHebrew) {
                listOf("סכם את היום", "איפה הייתי?")
            } else {
                listOf("Summarize the day", "Where was I?")
            }
        }
    }

    private fun computeConfidence(data: QueryData, sourceCount: Int): Float {
        if (data.records.isEmpty()) return 0f
        val baseConfidence = data.records.map { it.accuracyScore }.average().toFloat()
        val sourceBonus = (sourceCount - 1).coerceAtMost(3) * 0.05f
        return (baseConfidence + sourceBonus).coerceIn(0f, 1f)
    }

    private fun noDataResponse(language: Language): GeneratedResponse {
        return GeneratedResponse(
            text = noDataText(language),
            suggestedFollowUps = if (language == Language.HEBREW) {
                listOf("נסה טווח זמן אחר", "מה נאסף היום?")
            } else {
                listOf("Try a different time range", "What was collected today?")
            },
            confidence = 0f,
            sourceCount = 0,
        )
    }

    private fun noDataText(language: Language): String {
        return if (language == Language.HEBREW) {
            "לא נמצאו נתונים בטווח הזמן המבוקש."
        } else {
            "No data found for the requested time range."
        }
    }

    private fun roundCoord(value: Double): String = "%.3f".format(value)
    private fun formatCoord(value: Double): String = "%.5f".format(value)
}

/**
 * A generated response from the [ResponseGenerator].
 *
 * @property text The human-readable response text.
 * @property suggestedFollowUps Suggested follow-up queries.
 * @property confidence Overall confidence score (0.0 to 1.0).
 * @property sourceCount Number of independent data sources.
 */
data class GeneratedResponse(
    val text: String,
    val suggestedFollowUps: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    val sourceCount: Int = 1,
)
