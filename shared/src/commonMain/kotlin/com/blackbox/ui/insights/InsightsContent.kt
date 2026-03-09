package com.blackbox.ui.insights

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.usecase.insight.AppUsageStat
import com.blackbox.domain.usecase.insight.InsightsBrief
import com.blackbox.domain.usecase.insight.PlaceVisit
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Insights screen — "Intel Briefing".
 *
 * A scrollable briefing card feed structured in four sections:
 * 1. **TODAY** — live stats for the current day.
 * 2. **THIS WEEK** — 7-day step/screen charts + top places & apps.
 * 3. **OBSERVATIONS** — LLM-generated or rule-based insight sentences.
 * 4. **RECORDS** — personal bests and weekly averages.
 *
 * @param state Current UI state from the ViewModel.
 * @param onAction Callback to dispatch user actions.
 * @param modifier Optional [Modifier] for the root container.
 */
@Composable
fun InsightsContent(
    state: InsightsContract.State,
    onAction: (InsightsContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoadingData -> LoadingBriefing()
        state.error != null -> ErrorView(
            message = state.error,
            onRetry = { onAction(InsightsContract.Action.Refresh) },
            modifier = modifier,
        )
        else -> BriefingFeed(state = state, onAction = onAction, modifier = modifier)
    }
}

// ── Loading state ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingBriefing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "briefing_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "LOADING INTEL...",
            style = MaterialTheme.typography.labelLarge,
            color = BlackBoxColors.NeonGreen.copy(alpha = alpha),
        )
    }
}

// ── Main feed ─────────────────────────────────────────────────────────────────

@Composable
private fun BriefingFeed(
    state: InsightsContract.State,
    onAction: (InsightsContract.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brief = state.brief ?: return

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.PaddingScreen),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(Dimens.SpacingXs))
            BriefingHeader()
        }

        // ── Section 1: Today ─────────────────────────────────────────────────
        item { SectionDivider(label = "TODAY") }
        item { TodayCard(brief = brief) }

        // ── Section 2: This Week ──────────────────────────────────────────────
        item { SectionDivider(label = "THIS WEEK") }
        item { StepTrendCard(trend = brief.weekStepTrend, avgSteps = brief.avgDailySteps) }
        item { ScreenTrendCard(trend = brief.weekScreenTrend, avgMinutes = brief.avgDailyScreenMinutes) }

        if (brief.weekTopPlaces.isNotEmpty() || brief.weekTopApps.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                ) {
                    if (brief.weekTopPlaces.isNotEmpty()) {
                        TopPlacesCard(
                            places = brief.weekTopPlaces,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (brief.weekTopApps.isNotEmpty()) {
                        TopAppsCard(
                            apps = brief.weekTopApps,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── Section 3: Observations ───────────────────────────────────────────
        item { SectionDivider(label = "OBSERVATIONS") }

        when {
            state.isLoadingObservations -> item { ObservationsLoadingCard() }
            state.observations.isEmpty() -> item {
                ObservationCard(text = "No observations yet — check back after a few days of data.")
            }
            else -> items(state.observations) { obs ->
                ObservationCard(text = obs)
            }
        }

        // ── Section 4: Records ────────────────────────────────────────────────
        item { SectionDivider(label = "RECORDS") }
        item { RecordsCard(brief = brief) }

        item { Spacer(modifier = Modifier.height(Dimens.SpacingXl)) }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun BriefingHeader(modifier: Modifier = Modifier) {
    val dateLabel = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        .format(Date()).uppercase(Locale.getDefault())

    Column(modifier = modifier) {
        Text(
            text = "INTEL BRIEFING",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = BlackBoxColors.NeonGreen,
        )
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelMedium,
            color = BlackBoxColors.TextMuted,
        )
    }
}

// ── Section divider ───────────────────────────────────────────────────────────

@Composable
private fun SectionDivider(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = BlackBoxColors.TextMuted,
        )
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(
                color = BlackBoxColors.OutlineNeon,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

// ── Today card ────────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(brief: InsightsBrief, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        // Steps row
        TodayStatRow(
            label = "STEPS",
            value = brief.todaySteps.formatK(),
            valueColor = BlackBoxColors.NeonGreen,
            badge = stepsComparisonBadge(brief.todayStepsVsAvg),
            badgeColor = if (brief.todayStepsVsAvg >= 1f) BlackBoxColors.NeonGreen else BlackBoxColors.NeonMagenta,
        )
        // Screen time row
        TodayStatRow(
            label = "SCREEN",
            value = formatMinutes(brief.todayScreenMinutes),
            valueColor = BlackBoxColors.ElectricCyan,
        )
        // Places row
        TodayStatRow(
            label = "PLACES",
            value = "${brief.todayPlacesCount} visited",
            valueColor = BlackBoxColors.NeonMagenta,
        )
        // Top app row
        brief.todayTopApp?.let { app ->
            TodayStatRow(
                label = "TOP APP",
                value = app.displayName,
                valueColor = BlackBoxColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun TodayStatRow(
    label: String,
    value: String,
    valueColor: Color,
    badge: String? = null,
    badgeColor: Color = BlackBoxColors.NeonGreen,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
        badge?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
            )
        }
    }
}

private fun stepsComparisonBadge(ratio: Float): String? {
    val pct = ((ratio - 1f) * 100).toInt()
    return when {
        pct >= 5 -> "▲ +${pct}%"
        pct <= -5 -> "▼ ${pct}%"
        else -> null
    }
}

// ── Bar trend cards ───────────────────────────────────────────────────────────

@Composable
private fun StepTrendCard(
    trend: List<DailyStepCount>,
    avgSteps: Int,
    modifier: Modifier = Modifier,
) {
    TrendCard(
        title = "STEPS / DAY",
        subtitle = "avg ${avgSteps.formatK()}/day",
        subtitleColor = BlackBoxColors.NeonGreen,
        barColor = BlackBoxColors.NeonGreen,
        barValues = trend.map { it.steps.toFloat() },
        barLabels = trend.map { it.date.dayAbbrev() },
        modifier = modifier,
    )
}

@Composable
private fun ScreenTrendCard(
    trend: List<DailyScreenTime>,
    avgMinutes: Int,
    modifier: Modifier = Modifier,
) {
    TrendCard(
        title = "SCREEN / DAY",
        subtitle = "avg ${formatMinutes(avgMinutes)}/day",
        subtitleColor = BlackBoxColors.ElectricCyan,
        barColor = BlackBoxColors.ElectricCyan,
        barValues = trend.map { it.totalMinutes.toFloat() },
        barLabels = trend.map { it.date.dayAbbrev() },
        modifier = modifier,
    )
}

@Composable
private fun TrendCard(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    barColor: Color,
    barValues: List<Float>,
    barLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = BlackBoxColors.TextMuted,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor,
            )
        }

        if (barValues.isEmpty()) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.TextMuted,
                modifier = Modifier.padding(vertical = Dimens.SpacingXs),
            )
        } else {
            MiniBarChart(
                values = barValues,
                labels = barLabels,
                barColor = barColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}

@Composable
private fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier) {
        // Bars
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (values.isEmpty()) return@Canvas
            val count = values.size
            val totalGap = 4.dp.toPx() * (count - 1)
            val barWidth = (size.width - totalGap) / count
            val corner = CornerRadius(2.dp.toPx())

            values.forEachIndexed { i, value ->
                val ratio = value / max
                val barHeight = (size.height * ratio).coerceAtLeast(2.dp.toPx())
                val x = i * (barWidth + 4.dp.toPx())
                val y = size.height - barHeight

                // Dim background track
                drawRoundRect(
                    color = BlackBoxColors.SurfaceVariant,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = corner,
                )
                // Foreground value bar
                drawRoundRect(
                    color = barColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}

// ── Top places card ───────────────────────────────────────────────────────────

@Composable
private fun TopPlacesCard(places: List<PlaceVisit>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = "TOP PLACES",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = BlackBoxColors.TextMuted,
        )
        places.take(4).forEachIndexed { index, place ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = place.address.trim().split(",").first().trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.NeonMagenta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${place.visitCount}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}

// ── Top apps card ─────────────────────────────────────────────────────────────

@Composable
private fun TopAppsCard(apps: List<AppUsageStat>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = "TOP APPS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = BlackBoxColors.TextMuted,
        )
        apps.take(4).forEachIndexed { index, app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatMinutesShort(app.totalMinutes.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}

// ── Observation cards ─────────────────────────────────────────────────────────

@Composable
private fun ObservationCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(BlackBoxColors.NeonGreen.copy(alpha = 0.4f), 1.dp)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "⚡",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextPrimary,
        )
    }
}

@Composable
private fun ObservationsLoadingCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "obs_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ANALYSING PATTERNS...",
            style = MaterialTheme.typography.labelMedium,
            color = BlackBoxColors.NeonGreen.copy(alpha = alpha),
        )
    }
}

// ── Records card ──────────────────────────────────────────────────────────────

@Composable
private fun RecordsCard(brief: InsightsBrief, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neonBorder(BlackBoxColors.OutlineNeon, 1.dp)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        brief.bestStepDay?.let { best ->
            RecordRow(
                label = "BEST DAY",
                value = "${best.date.dayAbbrev()} · ${best.steps.formatK()} steps",
                valueColor = BlackBoxColors.NeonGreen,
            )
        }
        RecordRow(
            label = "AVG STEPS",
            value = "${brief.avgDailySteps.formatK()} / day",
            valueColor = BlackBoxColors.NeonGreen,
        )
        RecordRow(
            label = "AVG SCREEN",
            value = "${formatMinutes(brief.avgDailyScreenMinutes)} / day",
            valueColor = BlackBoxColors.ElectricCyan,
        )
        if (brief.weekTopPlaces.isNotEmpty()) {
            RecordRow(
                label = "PLACES",
                value = "${brief.weekTopPlaces.size} unique this week",
                valueColor = BlackBoxColors.NeonMagenta,
            )
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextMuted,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Int.formatK(): String = if (this >= 1000) "${this / 1000}k" else this.toString()

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinutesShort(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h" else "${m}m"
}

private fun String.dayAbbrev(): String = try {
    val cal = Calendar.getInstance()
    cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this) ?: return this.takeLast(2)
    arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[cal.get(Calendar.DAY_OF_WEEK) - 1]
} catch (_: Exception) {
    this.takeLast(2)
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewBrief = InsightsBrief(
    todaySteps = 4_250,
    todayScreenMinutes = 87,
    todayPlacesCount = 2,
    todayTopApp = AppUsageStat("YouTube", 45),
    todayStepsVsAvg = 0.68f,
    weekStepTrend = listOf(8500, 6200, 12000, 9800, 7400, 5100, 4250)
        .mapIndexed { i, s -> DailyStepCount("2026-03-${3 + i}", s) },
    weekScreenTrend = listOf(180 to 45, 210 to 52, 150 to 38, 195 to 48, 240 to 60, 165 to 41, 87 to 22)
        .mapIndexed { i, (m, p) -> DailyScreenTime("2026-03-${3 + i}", m, p) },
    weekTopPlaces = listOf(
        PlaceVisit("Dizengoff St, Tel Aviv", 5),
        PlaceVisit("Rothschild Blvd", 3),
        PlaceVisit("Carmel Market", 1),
    ),
    weekTopApps = listOf(
        AppUsageStat("YouTube", 225),
        AppUsageStat("Chrome", 130),
        AppUsageStat("WhatsApp", 90),
    ),
    bestStepDay = DailyStepCount("2026-03-05", 12000),
    avgDailySteps = 7_607,
    avgDailyScreenMinutes = 175,
)

@Preview(showBackground = true)
@Composable
private fun InsightsContentLoadedPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                isLoadingData = false,
                brief = previewBrief,
                observations = listOf(
                    "You walk 40% more on weekdays than weekends.",
                    "Screen time peaks on Thursday — 4h vs your 2h 55m avg.",
                    "Most visited place this week: Dizengoff St, 5 days.",
                    "YouTube takes up 3h 45m of your weekly screen time.",
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentObservationsLoadingPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                isLoadingData = false,
                brief = previewBrief,
                isLoadingObservations = true,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentLoadingPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(isLoadingData = true),
            onAction = {},
        )
    }
}
