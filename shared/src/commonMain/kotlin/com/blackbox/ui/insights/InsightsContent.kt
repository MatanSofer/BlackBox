package com.blackbox.ui.insights

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import com.blackbox.ui.theme.neonGlowBackground
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI content for the Insights screen — cyberpunk metrics terminal.
 *
 * Renders period selector chips with neon borders, metric cards with
 * animated count-up numbers, and canvas-drawn neon bar charts.
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
            listOf(7 to Res.string.insights_period_7d, 30 to Res.string.insights_period_30d, 90 to Res.string.insights_period_90d).forEach { (days, res) ->
                FilterChip(
                    selected = state.selectedPeriodDays == days,
                    onClick = { onAction(InsightsContract.Action.PeriodSelected(days)) },
                    label = { Text(stringResource(res)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BlackBoxColors.NeonGreenFaint,
                        selectedLabelColor = BlackBoxColors.NeonGreen,
                        labelColor = BlackBoxColors.TextMuted,
                        containerColor = BlackBoxColors.SurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.selectedPeriodDays == days,
                        selectedBorderColor = BlackBoxColors.NeonGreen,
                        borderColor = BlackBoxColors.OutlineNeon,
                        selectedBorderWidth = 1.dp,
                        borderWidth = 1.dp,
                    ),
                )
            }
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
                    title = "NO INSIGHTS YET",
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
                    InsightSummaryCard(
                        title = stringResource(Res.string.insights_steps),
                        value = state.averageSteps,
                        subtitle = stringResource(Res.string.insights_avg_steps),
                        dataPoints = state.stepTrend.map { it.steps },
                        accentColor = BlackBoxColors.NeonGreen,
                    )

                    InsightSummaryCard(
                        title = stringResource(Res.string.insights_screen_time),
                        value = state.averageScreenMinutes,
                        subtitle = stringResource(Res.string.insights_avg_screen),
                        dataPoints = state.screenTimeTrend.map { it.totalMinutes },
                        accentColor = BlackBoxColors.ElectricCyan,
                        formatValue = { formatMinutes(it) },
                    )
                }
            }
        }
    }
}

/**
 * Cyberpunk metric card with animated count-up value and canvas bar chart.
 *
 * @param title The metric name label.
 * @param value The numeric value to animate towards.
 * @param subtitle Description below the big number.
 * @param dataPoints Raw numbers for the mini bar chart.
 * @param accentColor Neon color for borders, bars, and accents.
 * @param formatValue Optional function to format the animated value into a string.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun InsightSummaryCard(
    title: String,
    value: Int,
    subtitle: String,
    dataPoints: List<Int>,
    accentColor: androidx.compose.ui.graphics.Color,
    formatValue: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    var animTarget by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { animTarget = value }
    val animatedValue by animateIntAsState(
        targetValue = animTarget,
        animationSpec = tween(durationMillis = 1000),
        label = "metric_count_up",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonGlowBackground(accentColor.copy(alpha = 0.05f))
            .neonBorder(color = accentColor, cornerRadius = 4.dp)
            .padding(Dimens.PaddingCard),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = formatValue(animatedValue),
            style = MaterialTheme.typography.displaySmall,
            color = accentColor,
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextMuted,
        )

        if (dataPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.SpacingMd))
            NeonBarChart(
                dataPoints = dataPoints,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.InsightBarMaxHeight),
            )
            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
            Text(
                text = "${dataPoints.size} DAYS",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.TextMuted,
            )
        }
    }
}

/**
 * Canvas-drawn neon bar chart.
 *
 * @param dataPoints The values to visualize as bars.
 * @param accentColor The neon color for the bars.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun NeonBarChart(
    dataPoints: List<Int>,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) return
    val maxVal = dataPoints.max().toFloat().coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val barCount = dataPoints.size
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount

        dataPoints.forEachIndexed { i, v ->
            val barHeight = (v / maxVal) * size.height
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            drawRoundRect(
                color = accentColor.copy(alpha = 0.7f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
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
