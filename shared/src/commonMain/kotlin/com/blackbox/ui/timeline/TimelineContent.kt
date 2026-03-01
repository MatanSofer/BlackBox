package com.blackbox.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.timeline.CollectorGroup
import com.blackbox.domain.model.timeline.TimelineEntry
import com.blackbox.domain.model.timeline.TimelineEntryType
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import com.blackbox.ui.timeline.components.CollectorGroupCard

/**
 * Pure UI content for the Timeline screen — grouped-by-collector card layout.
 *
 * Renders a day navigation header and a vertical list of [CollectorGroupCard]
 * instances, each representing one data collector's records for the selected day.
 * Cards are expandable to reveal individual entries or raw records.
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

            state.groups.isEmpty() -> {
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
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                    ) {
                        items(
                            items = state.groups,
                            key = { group -> group.collectorType?.name ?: "DERIVED_EVENTS" },
                        ) { group ->
                            CollectorGroupCard(
                                group = group,
                                onToggleExpand = {
                                    onAction(TimelineContract.Action.GroupToggled(group.collectorType))
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(Dimens.SpacingLg)) }
                    }
                }
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
private fun TimelineContentWithGroupsPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(
                selectedDate = "2026-02-23",
                groups = listOf(
                    CollectorGroup(
                        collectorType = CollectorType.LOCATION,
                        displayName = "LOCATION",
                        recordCount = 3,
                        lastRecordTime = 1740340000000L,
                        isExpanded = true,
                        entries = listOf(
                            TimelineEntry(
                                startTimestamp = 1740300000000L,
                                endTimestamp = 1740310000000L,
                                type = TimelineEntryType.LOCATION_STAY,
                                title = "Home",
                                subtitle = "6h 30m",
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
                    CollectorGroup(
                        collectorType = CollectorType.ACTIVITY,
                        displayName = "ACTIVITY",
                        recordCount = 2,
                        lastRecordTime = 1740320000000L,
                        isExpanded = false,
                        entries = listOf(
                            TimelineEntry(
                                startTimestamp = 1740310000000L,
                                type = TimelineEntryType.ACTIVITY,
                                title = "WALKING",
                                subtitle = "Confidence: 85%",
                            ),
                        ),
                    ),
                    CollectorGroup(
                        collectorType = null,
                        displayName = "DERIVED EVENTS",
                        recordCount = 1,
                        lastRecordTime = 1740315000000L,
                        isExpanded = false,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
