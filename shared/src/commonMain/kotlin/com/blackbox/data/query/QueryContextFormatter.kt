package com.blackbox.data.query

import com.blackbox.domain.model.query.QueryResult
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.RecordData
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a [QueryResult] (plus an optional broader record set) into a compact,
 * LLM-readable context block that is appended to the user message.
 *
 * The formatter is responsible for:
 * - Including the current date/time so the AI can reason about recency.
 * - Summarising the query intent, entities, and detected time range.
 * - Including the local rule-based engine's plain-text response as a hint.
 * - Converting up to [MAX_RECORDS] raw records into one-line summaries
 *   spread proportionally across all collector types present.
 *
 * Passing [allRecords] separately (rather than relying on [QueryResult.data])
 * is important because the local query engine fetches only the record types
 * relevant to the intent. [ProcessQueryWithAiUseCase] fetches all types
 * independently and passes them here so the AI gets the full picture.
 */
class QueryContextFormatter {

    companion object {
        /** Hard cap on raw records sent to the model. */
        private const val MAX_RECORDS = 120
    }

    /**
     * Formats [result] into a context string ready to be appended to the user message.
     *
     * @param result The [QueryResult] produced by the local query engine.
     * @param allRecords All records in the query's time range (across all collector types).
     *   Defaults to [result]'s own data for backward compatibility.
     * @return Multi-line context block starting with `[CONTEXT]`.
     */
    fun format(
        result: QueryResult,
        allRecords: List<CollectedRecord> = result.data,
    ): String {
        val sb = StringBuilder()
        val tz = TimeZone.currentSystemDefault()

        // ── Current date/time ──────────────────────────────────────────────────
        val nowLocal = Clock.System.now().toLocalDateTime(tz)
        sb.appendLine("[CONTEXT]")
        sb.appendLine("Current date/time: ${fmtLocal(nowLocal)}")

        // ── Query meta ─────────────────────────────────────────────────────────
        sb.appendLine("Query intent: ${result.parsedQuery.intent}")
        val rangeStart = fmtMs(result.parsedQuery.timeRange.startEpochMs, tz)
        val rangeEnd = fmtMs(result.parsedQuery.timeRange.endEpochMs, tz)
        sb.appendLine("Queried period: $rangeStart → $rangeEnd")

        if (result.parsedQuery.entities.isNotEmpty()) {
            sb.appendLine("Entities in query: ${result.parsedQuery.entities.joinToString { "${it.type}=${it.value}" }}")
        }

        sb.appendLine("Local engine says: ${result.responseText}")

        if (allRecords.isEmpty()) {
            sb.appendLine("Records: (none found in this time window)")
            return sb.toString()
        }

        // ── Records block ──────────────────────────────────────────────────────
        // Group by collector type, budget evenly so no single type dominates.
        val grouped = allRecords.groupBy { it.collectorType }
        val budgetPerType = (MAX_RECORDS / grouped.size.coerceAtLeast(1)).coerceAtLeast(3)
        val totalShown = grouped.values.sumOf { it.size.coerceAtMost(budgetPerType) }

        sb.appendLine("Sensor records (${allRecords.size} total across ${grouped.size} types, showing $totalShown):")
        grouped.forEach { (type, records) ->
            val slice = records.take(budgetPerType)
            sb.appendLine("  -- $type (${records.size} records) --")
            slice.forEach { record ->
                sb.appendLine("  ${fmtMs(record.timestamp, tz)}  ${summarise(record.data)}")
            }
            if (records.size > budgetPerType) {
                sb.appendLine("  ... and ${records.size - budgetPerType} more $type records")
            }
        }
        return sb.toString()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun fmtMs(epochMs: Long, tz: TimeZone): String {
        val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        return fmtLocal(local)
    }

    private fun fmtLocal(local: kotlinx.datetime.LocalDateTime): String =
        "%04d-%02d-%02d %02d:%02d".format(
            local.year, local.monthNumber, local.dayOfMonth,
            local.hour, local.minute,
        )

    private fun summarise(data: RecordData): String = when (data) {
        is RecordData.Location -> {
            val d = data.locationData
            val pos = d.address ?: "unknown location"
            val acc = d.accuracyMeters?.let { " acc=%.0fm".format(it) } ?: ""
            val spd = d.speed?.takeIf { it > 0.5f }?.let { " spd=%.1fm/s".format(it) } ?: ""
            "$pos$acc$spd"
        }
        is RecordData.Activity -> {
            val d = data.activityData
            "${d.detectedActivity} conf=${d.confidence}% steps_delta=${d.stepCountDelta}"
        }
        is RecordData.Wifi -> {
            val d = data.wifiData
            val ssid = if (d.connectedSsid != null) "connected=${d.connectedSsid}" else "no_connection"
            "$ssid nearby=${d.networkCount}"
        }
        is RecordData.AppUsage -> {
            val d = data.appUsageData
            "${d.foregroundApp} (${d.displayName}) dur=${d.sessionDurationMs / 1000}s"
        }
        is RecordData.ScreenState -> {
            val d = data.screenStateData
            "${d.state} bright=${d.brightness ?: "?"}%"
        }
        is RecordData.AudioLevel -> {
            val d = data.audioLevelData
            "db=${d.dbLevel} ${d.classification} micAvail=${d.micAvailable}"
        }
        is RecordData.Battery -> {
            val d = data.batteryData
            "${d.levelPercent}% ${d.status} temp=${d.temperatureCelsius}°C"
        }
        is RecordData.Connectivity -> {
            val d = data.connectivityData
            "net=${d.networkType} bt=${d.bluetoothEnabled} wifi=${d.wifiConnected}"
        }
        is RecordData.Barometer -> {
            "%.1f hPa".format(data.barometerData.pressureHpa)
        }
        is RecordData.Light -> {
            "%.0f lux ${data.lightData.classification}".format(data.lightData.lux)
        }
        is RecordData.CallLog -> {
            val d = data.callLogData
            "${d.callType} dur=${d.durationSeconds}s hash=${d.numberHash}"
        }
        is RecordData.MediaPlayback -> {
            val d = data.mediaPlaybackData
            "${if (d.isPlaying) "PLAYING" else "STOPPED"} vol=${d.volumePercent}% ${d.outputType}"
        }
    }
}
