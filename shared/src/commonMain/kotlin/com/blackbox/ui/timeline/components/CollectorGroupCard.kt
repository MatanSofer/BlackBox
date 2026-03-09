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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.timeline.CollectorGroup
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.accentLeftBar
import com.blackbox.ui.theme.obsidianCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An expandable card representing one data collector's records for the current day.
 *
 * Each card has a coloured left accent bar identifying the collector type,
 * a header with the collector name + record count, and an animated expand/collapse.
 *
 * @param group          The collector group to display.
 * @param onToggleExpand Callback when the user taps the header.
 * @param modifier       Optional [Modifier].
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
        label = "chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .accentLeftBar(color = accentColor),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(start = Dimens.SpacingLg, end = Dimens.PaddingCard, top = Dimens.SpacingMd, bottom = Dimens.SpacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Colour dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Spacer(modifier = Modifier.width(Dimens.SpacingSm))
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = BlackBoxColors.TextPrimary,
                )
                Spacer(modifier = Modifier.width(Dimens.SpacingSm))
                // Count badge
                Text(
                    text = "${group.recordCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(Dimens.RadiusFull))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (group.isExpanded) "Collapse" else "Expand",
                tint = BlackBoxColors.TextTertiary,
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
                // Top divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dimens.SpacingLg)
                        .height(1.dp)
                        .background(BlackBoxColors.BorderFaint),
                )
                Spacer(modifier = Modifier.height(Dimens.SpacingXs))

                if (group.rawRecords.isNotEmpty()) {
                    group.rawRecords.forEach { record ->
                        RawRecordRow(
                            record = record,
                            modifier = Modifier.padding(start = Dimens.SpacingLg),
                        )
                    }
                } else {
                    group.entries.forEach { entry ->
                        ProcessedEntryRow(
                            entry = entry,
                            accentColor = accentColor,
                            modifier = Modifier.padding(start = Dimens.SpacingLg),
                        )
                    }
                }
            }
        }
    }
}

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
            .padding(end = Dimens.PaddingCard, top = Dimens.SpacingXs, bottom = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        Text(
            text = timeFormat.format(Date(entry.startTimestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
            modifier = Modifier.width(40.dp),
        )
        // Small dot connector
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.6f)),
        )
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
                    color = BlackBoxColors.TextTertiary,
                )
            }
        }
    }
}

/** Maps a [CollectorType] to its Obsidian accent colour. */
private fun accentColorFor(collectorType: CollectorType?): Color = when (collectorType) {
    CollectorType.LOCATION    -> BlackBoxColors.AccentLocation
    CollectorType.ACTIVITY    -> BlackBoxColors.AccentActivity
    null                      -> BlackBoxColors.AccentDerived
    CollectorType.WIFI        -> BlackBoxColors.AccentWifi
    CollectorType.CONNECTIVITY -> BlackBoxColors.AccentConnectivity
    CollectorType.BATTERY     -> BlackBoxColors.AccentBattery
    CollectorType.SCREEN_STATE -> BlackBoxColors.AccentScreen
    CollectorType.APP_USAGE   -> BlackBoxColors.AccentAppUsage
    CollectorType.AUDIO_LEVEL -> BlackBoxColors.AccentAudio
    CollectorType.BAROMETER   -> BlackBoxColors.AccentBarometer
    CollectorType.LIGHT       -> BlackBoxColors.AccentLight
    CollectorType.CALL_LOG    -> BlackBoxColors.AccentCall
    CollectorType.MEDIA_PLAYBACK -> BlackBoxColors.AccentMedia
}
