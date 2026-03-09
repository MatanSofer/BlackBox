package com.blackbox.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.record.CollectedRecord
import com.blackbox.domain.model.record.RecordData
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single row showing the timestamp and key fields for one raw collector record.
 *
 * Used inside an expanded [CollectorGroupCard] for the eight non-base collectors
 * when the raw data view is enabled. The layout is intentionally compact and
 * muted so it reads as technical/inspection data rather than primary content.
 *
 * @param record The raw record to display.
 * @param modifier Optional [Modifier].
 */
@Composable
fun RawRecordRow(
    record: CollectedRecord,
    modifier: Modifier = Modifier,
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(Date(record.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
        )

        Spacer(modifier = Modifier.width(Dimens.SpacingMd))

        Text(
            text = summarise(record.data),
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Converts a raw package name to a readable label; leaves real app names unchanged. */
private fun cleanPackageName(name: String): String {
    val looksLikePackage = name.contains('.') &&
        name.none { it == ' ' } &&
        name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' } &&
        name.first().isLowerCase()
    return if (looksLikePackage) name.substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
    else name
}

/**
 * Extracts a short human-readable summary from a [RecordData] payload.
 *
 * Returns the 1–2 most meaningful fields for each collector type so the
 * developer can quickly assess whether data collection is working.
 */
private fun summarise(data: RecordData): String = when (data) {
    is RecordData.Wifi -> {
        val ssid = data.wifiData.connectedSsid ?: "No network"
        val dbm = data.wifiData.signalStrengthDbm?.let { " · ${it} dBm" } ?: ""
        "$ssid$dbm"
    }
    is RecordData.Connectivity -> {
        val net = data.connectivityData.networkType.name
        val bt = "bt=${data.connectivityData.bluetoothEnabled}"
        "$net · $bt"
    }
    is RecordData.Battery -> {
        val level = "${data.batteryData.levelPercent}%"
        val charging = if (data.batteryData.status.name == "CHARGING") "charging" else "not charging"
        "$level · $charging"
    }
    is RecordData.ScreenState -> {
        val brightness = data.screenStateData.brightness?.let { " · brightness=$it" } ?: ""
        "${data.screenStateData.state.name}$brightness"
    }
    is RecordData.AppUsage -> {
        val name = data.appUsageData.displayName.ifBlank { data.appUsageData.foregroundApp }
        cleanPackageName(name)
    }
    is RecordData.AudioLevel -> {
        "${"%.1f".format(data.audioLevelData.dbLevel)} dB"
    }
    is RecordData.Barometer -> {
        "${"%.1f".format(data.barometerData.pressureHpa)} hPa"
    }
    is RecordData.Light -> {
        "${"%.0f".format(data.lightData.lux)} lx"
    }
    is RecordData.CallLog -> {
        "${data.callLogData.callType} · ${data.callLogData.durationSeconds}s"
    }
    is RecordData.MediaPlayback -> {
        if (data.mediaPlaybackData.isPlaying) {
            "PLAYING · ${data.mediaPlaybackData.outputType}"
        } else {
            "STOPPED"
        }
    }
    // Base collectors — raw rows are not shown for these, but handle gracefully
    is RecordData.Location -> {
        data.locationData.address ?: "Unknown location"
    }
    is RecordData.Activity -> {
        data.activityData.detectedActivity.name
    }
}
