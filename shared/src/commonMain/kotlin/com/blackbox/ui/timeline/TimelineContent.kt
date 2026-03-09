package com.blackbox.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.blackbox.ui.timeline.components.CollectorGroupCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Timeline screen.
 *
 * Shows a day navigation header and a scrollable list of [CollectorGroupCard]
 * instances, one per data collector, each expandable.
 *
 * @param state    Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier].
 */
@Composable
fun TimelineContent(
    state: TimelineContract.State,
    onAction: (TimelineContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Day navigation header
        DayNavigationHeader(
            selectedDate = state.selectedDate,
            onPrevious = { onAction(TimelineContract.Action.PreviousDay) },
            onNext = { onAction(TimelineContract.Action.NextDay) },
        )

        when {
            state.isLoading -> LoadingIndicator()

            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(TimelineContract.Action.Refresh) },
            )

            state.groups.isEmpty() -> EmptyStateView(
                title = "No activity",
                message = "No recorded events for this day.",
            )

            else -> AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.PaddingScreen),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
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
                    item { Spacer(modifier = Modifier.height(Dimens.SpacingXl)) }
                }
            }
        }
    }
}

// ── Day navigation header ─────────────────────────────────────────────────────

@Composable
private fun DayNavigationHeader(
    selectedDate: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dayName, dayNum, monthYear) = parseDateParts(selectedDate)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingScreen, vertical = Dimens.SpacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = BlackBoxColors.TextSecondary,
            )
        }

        // Date display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.TextTertiary,
            )
            Text(
                text = dayNum,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = monthYear,
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.TextTertiary,
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = BlackBoxColors.TextSecondary,
            )
        }
    }

    // Separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingScreen)
            .height(1.dp)
            .background(BlackBoxColors.BorderFaint),
    )
    Spacer(modifier = Modifier.height(Dimens.SpacingSm))
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class DateParts(val dayName: String, val dayNum: String, val monthYear: String)

private fun parseDateParts(date: String): DateParts = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val d = sdf.parse(date) ?: Date()
    DateParts(
        dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(d),
        dayNum = SimpleDateFormat("d", Locale.getDefault()).format(d),
        monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d),
    )
} catch (_: Exception) {
    DateParts(date, "", "")
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun TimelineContentEmptyPreview() {
    BlackBoxTheme {
        TimelineContent(
            state = TimelineContract.State(selectedDate = "2026-02-23"),
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
                        displayName = "Location",
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
                        ),
                    ),
                    CollectorGroup(
                        collectorType = CollectorType.ACTIVITY,
                        displayName = "Activity",
                        recordCount = 2,
                        lastRecordTime = 1740320000000L,
                        isExpanded = false,
                        entries = emptyList(),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
