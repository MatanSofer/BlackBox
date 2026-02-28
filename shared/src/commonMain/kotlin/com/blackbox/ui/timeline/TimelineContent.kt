package com.blackbox.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.domain.model.timeline.TimelineEntryType
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.NeonPulseIndicator
import com.blackbox.ui.theme.neonBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Timeline screen — cyberpunk vertical timeline.
 *
 * Renders a day navigation header with neon accents and a vertical
 * neon-line timeline of entries with pulse markers.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun TimelineContent(
    state: TimelineContract.State,
    onAction: (TimelineContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Day navigation header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onAction(TimelineContract.Action.PreviousDay) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
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
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconSm),
                    tint = BlackBoxColors.NeonGreen,
                )
                Spacer(modifier = Modifier.width(Dimens.SpacingXs))
                Text(
                    text = state.selectedDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = BlackBoxColors.NeonGreen,
                )
            }

            IconButton(onClick = { onAction(TimelineContract.Action.NextDay) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = BlackBoxColors.NeonGreen,
                )
            }
        }

        when {
            state.isLoading -> {
                LoadingIndicator()
            }

            state.error != null -> {
                ErrorView(
                    message = state.error,
                    onRetry = { onAction(TimelineContract.Action.Refresh) },
                )
            }

            state.entries.isEmpty() -> {
                EmptyStateView(
                    title = "NO ACTIVITY",
                    message = "No recorded events for this day.",
                )
            }

            else -> {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.PaddingScreen),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                    ) {
                        items(state.entries, key = { it.startTimestamp }) { entry ->
                            TimelineEntryRow(
                                entry = entry,
                                onClick = { onAction(TimelineContract.Action.EntryClicked(entry)) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(Dimens.SpacingLg)) }
                    }
                }
            }
        }
    }
}

/**
 * A single timeline row: neon vertical line + pulse marker + entry card.
 */
@Composable
private fun TimelineEntryRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Left column: neon line + pulse marker
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(Dimens.TimelineMarkerNeon)
                .fillMaxHeight(),
        ) {
            // Neon vertical line
            Canvas(
                modifier = Modifier
                    .width(Dimens.TimelineNeonWidth)
                    .weight(1f),
            ) {
                drawLine(
                    color = BlackBoxColors.NeonGreen.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = Dimens.TimelineNeonWidth.toPx(),
                )
            }
            NeonPulseIndicator(
                color = BlackBoxColors.NeonGreen,
                size = Dimens.TimelineMarkerNeon,
            )
        }

        Spacer(modifier = Modifier.width(Dimens.SpacingSm))

        // Entry card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Dimens.SpacingSm)
                .neonBorder(color = BlackBoxColors.OutlineNeon, cornerRadius = 4.dp)
                .padding(Dimens.SpacingMd)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = when (entry.type) {
                            TimelineEntryType.LOCATION_STAY -> Icons.Default.LocationOn
                            TimelineEntryType.TRANSIT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                            TimelineEntryType.EVENT -> Icons.Default.Star
                            TimelineEntryType.ACTIVITY -> Icons.Default.Star
                            TimelineEntryType.SLEEP -> Icons.Default.DateRange
                        },
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.IconSm),
                        tint = BlackBoxColors.ElectricCyan,
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpacingXs))
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BlackBoxColors.TextPrimary,
                    )
                }

                Text(
                    text = timeFormat.format(Date(entry.startTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }

            entry.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextMuted,
                    modifier = Modifier.padding(top = Dimens.SpacingXxs),
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun TimelineContentEmptyPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(
                selectedDate = "2026-02-23",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineContentWithEntriesPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(
                selectedDate = "2026-02-23",
                entries = listOf(
                    TimelineEntry(
                        startTimestamp = 1740300000000L,
                        endTimestamp = 1740310000000L,
                        type = TimelineEntryType.LOCATION_STAY,
                        title = "Home",
                        subtitle = "6h 30m",
                    ),
                    TimelineEntry(
                        startTimestamp = 1740310000000L,
                        type = TimelineEntryType.TRANSIT,
                        title = "Driving",
                        subtitle = "25m",
                    ),
                    TimelineEntry(
                        startTimestamp = 1740315000000L,
                        endTimestamp = 1740340000000L,
                        type = TimelineEntryType.LOCATION_STAY,
                        title = "Office",
                        subtitle = "7h",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
