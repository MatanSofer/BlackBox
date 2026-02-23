package com.blackbox.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.insights_avg_screen
import blackbox.shared.generated.resources.insights_avg_steps
import blackbox.shared.generated.resources.insights_period_30d
import blackbox.shared.generated.resources.insights_period_7d
import blackbox.shared.generated.resources.insights_period_90d
import blackbox.shared.generated.resources.insights_screen_time
import blackbox.shared.generated.resources.insights_steps
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Insights screen.
 *
 * Renders period selector chips, summary cards with averages,
 * and trend data for steps and screen time.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun InsightsContent(
    state: InsightsContract.State,
    onAction: (InsightsContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        // Period selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        ) {
            FilterChip(
                selected = state.selectedPeriodDays == 7,
                onClick = { onAction(InsightsContract.Action.PeriodSelected(7)) },
                label = { Text(stringResource(Res.string.insights_period_7d)) },
            )
            FilterChip(
                selected = state.selectedPeriodDays == 30,
                onClick = { onAction(InsightsContract.Action.PeriodSelected(30)) },
                label = { Text(stringResource(Res.string.insights_period_30d)) },
            )
            FilterChip(
                selected = state.selectedPeriodDays == 90,
                onClick = { onAction(InsightsContract.Action.PeriodSelected(90)) },
                label = { Text(stringResource(Res.string.insights_period_90d)) },
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        when {
            state.isLoading -> {
                LoadingIndicator()
            }

            state.error != null -> {
                ErrorView(
                    message = state.error,
                    onRetry = { onAction(InsightsContract.Action.Refresh) },
                )
            }

            state.stepTrend.isEmpty() && state.screenTimeTrend.isEmpty() -> {
                EmptyStateView(
                    title = "No Insights Yet",
                    message = "Insights will appear after a few days of data collection.",
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingLg),
                ) {
                    // Steps summary card
                    InsightSummaryCard(
                        title = stringResource(Res.string.insights_steps),
                        value = "${state.averageSteps}",
                        subtitle = stringResource(Res.string.insights_avg_steps),
                        dataPoints = state.stepTrend.map { it.steps },
                    )

                    // Screen time summary card
                    InsightSummaryCard(
                        title = stringResource(Res.string.insights_screen_time),
                        value = formatMinutes(state.averageScreenMinutes),
                        subtitle = stringResource(Res.string.insights_avg_screen),
                        dataPoints = state.screenTimeTrend.map { it.totalMinutes },
                    )
                }
            }
        }
    }
}

/**
 * Summary card displaying a metric with a simple bar trend.
 *
 * @param title The metric name.
 * @param value The primary display value.
 * @param subtitle Description below the value.
 * @param dataPoints Numeric data for the mini trend visualization.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun InsightSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    dataPoints: List<Int>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.PaddingCard)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (dataPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.SpacingMd))
                Text(
                    text = "${dataPoints.size} days of data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Formats minutes into "Xh Ym" display format.
 */
private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentEmptyPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentWithDataPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                stepTrend = listOf(
                    DailyStepCount("2026-02-17", 8500),
                    DailyStepCount("2026-02-18", 6200),
                    DailyStepCount("2026-02-19", 12000),
                    DailyStepCount("2026-02-20", 9800),
                    DailyStepCount("2026-02-21", 7400),
                    DailyStepCount("2026-02-22", 5100),
                    DailyStepCount("2026-02-23", 10200),
                ),
                screenTimeTrend = listOf(
                    DailyScreenTime("2026-02-17", 180, 45),
                    DailyScreenTime("2026-02-18", 210, 52),
                    DailyScreenTime("2026-02-19", 150, 38),
                    DailyScreenTime("2026-02-20", 195, 48),
                    DailyScreenTime("2026-02-21", 240, 60),
                    DailyScreenTime("2026-02-22", 165, 41),
                    DailyScreenTime("2026-02-23", 200, 50),
                ),
                averageSteps = 8457,
                averageScreenMinutes = 191,
            ),
            onAction = {},
        )
    }
}
