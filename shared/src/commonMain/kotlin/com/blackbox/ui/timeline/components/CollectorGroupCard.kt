package com.blackbox.ui.timeline.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.timeline.CollectorGroup
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An expandable card representing one data collector's records for the current day.
 *
 * The card header always shows:
 * - A colour-coded dot identifying the collector type
 * - The collector display name (e.g. "LOCATION")
 * - A record count badge
 * - A chevron icon that rotates when the card is expanded
 *
 * When expanded, processed [TimelineEntry] rows are shown for the base collectors
 * (LOCATION, ACTIVITY, Derived Events); raw [RawRecordRow] rows are shown for
 * the eight non-base collectors.
 *
 * @param group The collector group to display.
 * @param onToggleExpand Callback invoked when the user taps the header.
 * @param modifier Optional [Modifier].
 */
@Composable
fun CollectorGroupCard(
    group: CollectorGroup,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = accentColorFor(group.collectorType)
    val chevronRotation by animateFloatAsState(
        targetValue = if (group.isExpanded) 180f else 0f,
        label = "chevron_rotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(color = accentColor.copy(alpha = 0.4f), cornerRadius = 4.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(Dimens.PaddingCard),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Colour-coded dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accentColor, CircleShape),
                )

                Spacer(modifier = Modifier.width(Dimens.SpacingSm))

                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlackBoxColors.TextPrimary,
                )

                Spacer(modifier = Modifier.width(Dimens.SpacingSm))

                // Record count badge
                Text(
                    text = "[${group.recordCount}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (group.isExpanded) "Collapse" else "Expand",
                tint = accentColor,
                modifier = Modifier
                    .size(Dimens.IconMd)
                    .rotate(chevronRotation),
            )
        }

        // Expanded body
        AnimatedVisibility(
            visible = group.isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpacingSm),
            ) {
                if (group.rawRecords.isNotEmpty()) {
                    // Raw records for the eight non-base collectors
                    group.rawRecords.forEach { record ->
                        RawRecordRow(record = record)
                    }
                } else {
                    // Processed timeline entries for Location / Activity / Derived Events
                    group.entries.forEach { entry ->
                        ProcessedEntryRow(entry = entry, accentColor = accentColor)
                    }
                }
            }
        }
    }
}

/**
 * A compact row for a processed [TimelineEntry] within an expanded group card.
 */
@Composable
private fun ProcessedEntryRow(
    entry: TimelineEntry,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(Date(entry.startTimestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
        )

        Spacer(modifier = Modifier.width(Dimens.SpacingSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextPrimary,
            )
            entry.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Returns the cyberpunk accent colour assigned to the given [collectorType].
 *
 * `null` maps to the Derived Events group which uses NeonMagenta.
 */
private fun accentColorFor(collectorType: CollectorType?): Color = when (collectorType) {
    CollectorType.LOCATION -> BlackBoxColors.NeonGreen
    CollectorType.ACTIVITY -> BlackBoxColors.ElectricCyan
    null -> BlackBoxColors.NeonMagenta                    // Derived Events
    CollectorType.WIFI -> Color(0xFFFFB300)               // Amber
    CollectorType.CONNECTIVITY -> Color(0xFFFF6D00)       // Orange
    CollectorType.BATTERY -> Color(0xFFFFE500)            // Yellow
    CollectorType.SCREEN_STATE -> Color(0xFFCE93D8)       // Purple
    CollectorType.APP_USAGE -> Color(0xFFFF80AB)          // Pink
    CollectorType.AUDIO_LEVEL -> Color(0xFFFF1744)        // Red
    CollectorType.BAROMETER -> Color(0xFF00E5FF)          // Teal
    CollectorType.LIGHT -> Color(0xFFB0BEC5)              // Silver
}
