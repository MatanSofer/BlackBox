package com.blackbox.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.map.DayLocationSummary
import com.blackbox.domain.model.map.LocationStay
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Map screen.
 *
 * Uses a full-screen [Box] so the OSMDroid map fills the entire content area
 * and the date bar + bottom panel are declared *after* the map in the
 * composition. In Compose 1.5+, [AndroidView] is composited into the Compose
 * GraphicsLayer tree, so content declared later in the same [Box] draws on top
 * of the Android View — this is the same pattern used by the Maps Compose SDK.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun MapContent(
    state: MapContract.State,
    onAction: (MapContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBoxColors.Background),
    ) {
        // ── Layer 1: map or placeholder (fills entire box) ─────────────────
        when {
            state.isLoading -> LoadingIndicator()

            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(MapContract.Action.Refresh) },
            )

            state.summary == null || state.summary.stays.isEmpty() -> EmptyStateView(
                title = "No Location Data",
                message = "No location data for this day.\nMake sure Location collection is enabled.",
            )

            else -> OsmMapView(
                stays = state.summary.stays,
                selectedStay = state.selectedStay,
                onStayTapped = { stay -> onAction(MapContract.Action.StayTapped(stay)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Layer 2: controls overlay (declared after map → drawn on top) ──
        // MapDateBar at the top, MapBottomPanel pinned to the bottom.
        // Both have opaque Surface backgrounds so they're readable over tiles.
        Column(modifier = Modifier.fillMaxSize()) {
            MapDateBar(
                selectedDate = state.selectedDate,
                onPrevious = { onAction(MapContract.Action.PreviousDay) },
                onNext = { onAction(MapContract.Action.NextDay) },
            )
            Spacer(modifier = Modifier.weight(1f))
            MapBottomPanel(
                summary = state.summary,
                selectedStay = state.selectedStay,
                onDismiss = { onAction(MapContract.Action.StayTapped(null)) },
            )
        }
    }
}

// ── Date navigator bar ────────────────────────────────────────────────────────

@Composable
private fun MapDateBar(
    selectedDate: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface)
            .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = BlackBoxColors.NeonGreen,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .neonBorder(color = BlackBoxColors.OutlineNeon, cornerRadius = 2.dp)
                .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSm),
                tint = BlackBoxColors.NeonGreen,
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            Text(
                text = formatDateLabel(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.NeonGreen,
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = BlackBoxColors.NeonGreen,
            )
        }
    }
}

// ── Bottom panel ──────────────────────────────────────────────────────────────

@Composable
private fun MapBottomPanel(
    summary: DayLocationSummary?,
    selectedStay: LocationStay?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.Surface),
    ) {
        AnimatedVisibility(
            visible = selectedStay != null && summary != null,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            if (selectedStay != null && summary != null) {
                StayDetailCard(
                    stay = selectedStay,
                    stopIndex = summary.stays.indexOf(selectedStay) + 1,
                    totalStops = summary.stays.size,
                    onDismiss = onDismiss,
                )
            }
        }

        SummaryStrip(summary = summary)
    }
}

/**
 * Always-visible one-line strip: stops count, total distance, time range.
 */
@Composable
private fun SummaryStrip(
    summary: DayLocationSummary?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryChip(
            label = if (summary != null) "${summary.stays.size}" else "—",
            sublabel = "STOPS",
            color = BlackBoxColors.NeonGreen,
        )
        SummaryChip(
            label = if (summary != null) formatDistance(summary.totalDistanceMeters) else "—",
            sublabel = "DISTANCE",
            color = BlackBoxColors.ElectricCyan,
        )
        SummaryChip(
            label = if (summary?.firstFixTime != null && summary.lastFixTime != null)
                "${fmtTime(summary.firstFixTime)} – ${fmtTime(summary.lastFixTime)}"
            else "—",
            sublabel = "TIME RANGE",
            color = BlackBoxColors.TextMuted,
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    sublabel: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
        )
    }
}

/**
 * Expanded card for a selected stay dot, showing all available data:
 * visit order, arrival/departure times, duration, fix count, accuracy,
 * coordinates, and known place category if matched.
 *
 * @param stopIndex  1-based position of this stay in the day's chronological sequence.
 * @param totalStops Total number of stays for the selected day.
 */
@Composable
private fun StayDetailCard(
    stay: LocationStay,
    stopIndex: Int,
    totalStops: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(color = BlackBoxColors.NeonMagenta, cornerRadius = 0.dp)
            .padding(Dimens.PaddingCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = BlackBoxColors.NeonMagenta,
                modifier = Modifier.size(Dimens.IconMd),
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            Text(
                text = stay.knownPlace?.name
                    ?: "(%.5f,  %.5f)".format(stay.latitude, stay.longitude),
                style = MaterialTheme.typography.titleSmall,
                color = BlackBoxColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // Stop badge: "2 / 5"
            Text(
                text = "$stopIndex / $totalStops",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.NeonGreen,
                modifier = Modifier
                    .neonBorder(color = BlackBoxColors.OutlineNeon, cornerRadius = 2.dp)
                    .padding(horizontal = Dimens.SpacingXs, vertical = 2.dp),
            )
            Spacer(Modifier.width(Dimens.SpacingXs))
            IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.IconMd)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = BlackBoxColors.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(Dimens.SpacingXs))

        DetailRow(
            left = "ARRIVED", leftValue = fmtTime(stay.arrivalTime),
            right = "DEPARTED", rightValue = fmtTime(stay.departureTime),
        )
        DetailRow(
            left = "DURATION", leftValue = formatDuration(stay.durationMs),
            right = "GPS FIXES", rightValue = "${stay.pointCount}",
        )
        stay.averageAccuracyMeters?.let { acc ->
            DetailRow(
                left = "AVG ACCURACY", leftValue = "±${acc.toInt()}m",
                right = "CATEGORY", rightValue = stay.knownPlace?.category?.name ?: "UNKNOWN",
            )
        }
        DetailRow(
            left = "LAT", leftValue = "%.6f".format(stay.latitude),
            right = "LNG", rightValue = "%.6f".format(stay.longitude),
        )
    }
}

@Composable
private fun DetailRow(
    left: String,
    leftValue: String,
    right: String,
    rightValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingXxs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DetailCell(label = left, value = leftValue, modifier = Modifier.weight(1f))
        DetailCell(label = right, value = rightValue, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = BlackBoxColors.ElectricCyan)
    }
}

// ── Formatting ────────────────────────────────────────────────────────────────

private fun formatDateLabel(date: String): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val output = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    output.format(input.parse(date) ?: Date())
} catch (_: Exception) { date }

private fun fmtTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${meters.toInt()}m"
    else -> "${"%.1f".format(meters / 1_000)}km"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun MapContentEmptyPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(
                selectedDate = "2026-03-04",
                summary = DayLocationSummary("2026-03-04", emptyList(), 0.0, null, null),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapContentLoadingPreview() {
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(selectedDate = "2026-03-04", isLoading = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapContentStaySelectedPreview() {
    val stay = LocationStay(
        latitude = 32.0853,
        longitude = 34.7818,
        arrivalTime = System.currentTimeMillis() - 5_400_000L,
        departureTime = System.currentTimeMillis(),
        pointCount = 47,
        averageAccuracyMeters = 12f,
        knownPlace = null,
    )
    BlackBoxTheme {
        MapContent(
            state = MapContract.State(
                selectedDate = "2026-03-04",
                summary = DayLocationSummary(
                    date = "2026-03-04",
                    stays = listOf(stay),
                    totalDistanceMeters = 3_420.0,
                    firstFixTime = System.currentTimeMillis() - 28_800_000L,
                    lastFixTime = System.currentTimeMillis(),
                ),
                selectedStay = stay,
            ),
            onAction = {},
        )
    }
}
