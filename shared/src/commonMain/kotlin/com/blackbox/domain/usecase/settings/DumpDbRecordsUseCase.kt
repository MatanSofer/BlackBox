package com.blackbox.domain.usecase.settings

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.record.PlugType
import com.blackbox.domain.model.record.RecordData
import com.blackbox.domain.repository.RecordRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Fetches all records from all collectors and formats them as a human-readable
 * text block divided by collector type.
 *
 * Shows the [MAX_PER_COLLECTOR] most recent records per collector type
 * (ordered oldest → newest so they read chronologically).
 * Also dumps the full output to logcat under the [TAG] tag for
 * inspection via Android Studio's Logcat.
 *
 * @property recordRepository Source of raw collected records.
 * @property logger Logger for the logcat dump.
 */
class DumpDbRecordsUseCase(
    private val recordRepository: RecordRepository,
    private val logger: BlackBoxLogger,
) {

    /**
     * Runs the dump and returns the formatted text.
     *
     * @return [Result] containing the formatted multi-line dump string on success.
     */
    suspend operator fun invoke(): Result<String> {
        return runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            val sb = StringBuilder()

            sb.appendLine("╔══════════════════════════════════════╗")
            sb.appendLine("║         BB_DB_DUMP — ALL RECORDS     ║")
            sb.appendLine("║  (last $MAX_PER_COLLECTOR per collector)                 ║")
            sb.appendLine("╚══════════════════════════════════════╝")
            sb.appendLine("Generated: ${fmtTs(now)}")
            sb.appendLine()

            CollectorType.entries.forEach { type ->
                val records = recordRepository.getRecordsByTypeInRange(type, 0L, now)
                    .takeLast(MAX_PER_COLLECTOR)

                sb.appendLine("=== ${type.name} (${records.size} records) ===")
                if (records.isEmpty()) {
                    sb.appendLine("  (no records)")
                } else {
                    records.forEach { record ->
                        sb.append("  [${fmtTs(record.timestamp)}] ")
                        sb.appendLine(fmtData(record.data))
                    }
                }
                sb.appendLine()
            }

            val dump = sb.toString()
            logger.d(TAG, "\n$dump")
            dump
        }
    }

    // ── Timestamp ─────────────────────────────────────────────────────────────

    private fun fmtTs(ms: Long): String =
        Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toString()
            .replace('T', ' ')
            .take(16) // "2026-03-02 14:23"

    // ── Per-collector data formatting ─────────────────────────────────────────

    private fun fmtData(data: RecordData): String = when (data) {
        is RecordData.Location -> {
            val d = data.locationData
            buildString {
                append("lat=${d.latitude.f(5)} lon=${d.longitude.f(5)}")
                d.accuracyMeters?.let { append(" acc=${it.toInt()}m") }
                d.speed?.let { if (it > 0f) append(" spd=${it.f(1)}m/s") }
                d.altitude?.let { append(" alt=${it.f(1)}m") }
                append(" [${d.source}]")
            }
        }

        is RecordData.Activity -> {
            val d = data.activityData
            buildString {
                append("${d.detectedActivity} conf=${d.confidence}%")
                if (d.stepCountDelta > 0) append(" steps=+${d.stepCountDelta}")
                if (d.movementIntensity > 0f) append(" intensity=${d.movementIntensity.f(2)}")
            }
        }

        is RecordData.Wifi -> {
            val d = data.wifiData
            buildString {
                d.connectedSsid?.let { append("ssid=\"$it\"") } ?: append("disconnected")
                d.signalStrengthDbm?.let { append(" rssi=${it}dBm") }
                d.frequencyMhz?.let { append(" ${it}MHz") }
                append(" nets=${d.networkCount}")
            }
        }

        is RecordData.AppUsage -> {
            val d = data.appUsageData
            buildString {
                append("\"${d.displayName}\"")
                if (d.sessionDurationMs > 0) append(" ${d.sessionDurationMs / 1000}s")
                append(" [${d.category}]")
            }
        }

        is RecordData.ScreenState -> {
            val d = data.screenStateData
            buildString {
                append(d.state)
                d.brightness?.let { append(" bright=$it") }
                d.orientation?.let { append(" $it") }
            }
        }

        is RecordData.AudioLevel -> {
            val d = data.audioLevelData
            if (!d.micAvailable) "MIC HELD BY OTHER APP" else "${d.dbLevel.f(1)}dB ${d.classification}"
        }

        is RecordData.Battery -> {
            val d = data.batteryData
            buildString {
                append("${d.levelPercent}% ${d.status}")
                if (d.plugType != PlugType.NONE) append(" via ${d.plugType}")
                d.temperatureCelsius?.let { append(" ${it.f(1)}°C") }
                d.voltageMv?.let { append(" ${it}mV") }
            }
        }

        is RecordData.Connectivity -> {
            val d = data.connectivityData
            buildString {
                append(d.networkType)
                d.cellularType?.let { append(" ($it)") }
                d.carrierName?.let { append(" carrier=$it") }
                if (d.bluetoothEnabled) append(" bt(${d.connectedBtDevices.size}dev)")
                if (d.isVpnActive) append(" VPN")
                if (d.airplaneMode) append(" AIRPLANE")
            }
        }

        is RecordData.Barometer -> {
            val d = data.barometerData
            buildString {
                append("${d.pressureHpa.f(1)}hPa")
                d.relativeAltitudeMeters?.let { append(" alt=${it.f(1)}m") }
                d.altitudeChangeSinceLast?.let { if (abs(it) > 0.1f) append(" Δ${it.f(1)}m") }
                if (d.estimatedFloorChange != 0) append(" floors=${d.estimatedFloorChange}")
            }
        }

        is RecordData.Light -> {
            val d = data.lightData
            "${d.lux.f(0)}lux ${d.classification}"
        }

        is RecordData.CallLog -> {
            val d = data.callLogData
            "${d.callType} ${d.durationSeconds}s hash=${d.numberHash}"
        }

        is RecordData.MediaPlayback -> {
            val d = data.mediaPlaybackData
            "${if (d.isPlaying) "PLAYING" else "STOPPED"} vol=${d.volumePercent}% ${d.outputType}"
        }
    }

    // ── Float/Double formatting helpers (no String.format needed) ─────────────

    /** Formats a [Double] to [decimals] decimal places without String.format. */
    private fun Double.f(decimals: Int): String {
        if (decimals == 0) return "${this.roundToLong()}"
        val factor = tenPow(decimals)
        val scaled = (this * factor).roundToLong()
        val intPart = scaled / factor
        val fracPart = abs(scaled % factor).toString().padStart(decimals, '0')
        return "$intPart.$fracPart"
    }

    private fun Float.f(decimals: Int): String = this.toDouble().f(decimals)

    private fun tenPow(n: Int): Long {
        var result = 1L
        repeat(n) { result *= 10L }
        return result
    }

    companion object {
        private const val TAG = "BB_DB_DUMP"

        /** Maximum number of most-recent records shown per collector type. */
        const val MAX_PER_COLLECTOR = 50
    }
}
