package com.blackbox.ui.insights

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackbox.domain.model.sleep.SleepQuality
import com.blackbox.domain.model.sleep.SleepSession
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.domain.usecase.insight.AppUsageStat
import com.blackbox.domain.usecase.insight.ContactCallStat
import com.blackbox.domain.usecase.insight.InsightsBrief
import com.blackbox.domain.usecase.insight.PlaceVisit
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.PulseDotsIndicator
import com.blackbox.ui.theme.barGradient
import com.blackbox.ui.theme.indigoTealGradient
import com.blackbox.ui.theme.obsidianCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure UI content for the Insights screen — personal data dashboard.
 *
 * Sections: TODAY hero stats · WEEK charts · TOP PLACES & APPS · AI OBSERVATIONS.
 *
 * @param state    Current UI state from the ViewModel.
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
        state.isLoadingData -> LoadingInsights(modifier = modifier)
        state.error != null -> ErrorView(
            message = state.error,
            onRetry = { onAction(InsightsContract.Action.Refresh) },
            modifier = modifier,
        )
        else -> InsightsFeed(state = state, modifier = modifier)
    }
}

// ── Loading state ──────────────────────────────────────────────────────────────

@Composable
private fun LoadingInsights(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulseDotsIndicator(color = BlackBoxColors.Indigo)
            Spacer(modifier = Modifier.height(Dimens.SpacingMd))
            Text(
                text = "Loading insights...",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }
    }
}

// ── Main feed ─────────────────────────────────────────────────────────────────

@Composable
private fun InsightsFeed(
    state: InsightsContract.State,
    modifier: Modifier = Modifier,
) {
    val brief = state.brief ?: return
    val dateLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.PaddingScreen),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMd),
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(Dimens.SpacingLg))
            Text(
                text = "Insights",
                style = MaterialTheme.typography.headlineSmall,
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Today hero
        item { TodayHeroCard(brief = brief) }

        // Today's app breakdown
        if (brief.todayTopApps.isNotEmpty()) {
            item { AppBreakdownCard(apps = brief.todayTopApps) }
        }

        // Week charts
        item {
            SectionLabel(text = "This week")
        }
        item { StepTrendCard(trend = brief.weekStepTrend, avg = brief.avgDailySteps) }
        item { ScreenTrendCard(trend = brief.weekScreenTrend, avg = brief.avgDailyScreenMinutes) }
        item { SleepTrendCard(trend = brief.weekSleepTrend, lastNight = brief.lastNightSleep, avg = brief.avgSleepMinutes) }

        // Top places + apps
        if (brief.weekTopPlaces.isNotEmpty() || brief.weekTopApps.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                ) {
                    if (brief.weekTopPlaces.isNotEmpty()) {
                        TopListCard(
                            title = "Top places",
                            accentColor = BlackBoxColors.Rose,
                            items = brief.weekTopPlaces.take(4).map {
                                it.address.trim().split(",").first().trim() to "${it.visitCount}d"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (brief.weekTopApps.isNotEmpty()) {
                        TopListCard(
                            title = "Top apps",
                            accentColor = BlackBoxColors.Teal,
                            items = brief.weekTopApps.take(4).map {
                                it.displayName to formatMinutesShort(it.totalMinutes.toInt())
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Top contacts
        if (brief.weekTopContacts.isNotEmpty()) {
            item { TopContactsCard(contacts = brief.weekTopContacts) }
        }

        // Observations
        item { SectionLabel(text = "Observations") }

        when {
            state.isLoadingObservations -> item { ObservationsLoadingCard() }
            state.observations.isEmpty() -> item {
                Text(
                    text = "No observations yet — check back after a few days of data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextTertiary,
                    modifier = Modifier.padding(vertical = Dimens.SpacingXs),
                )
            }
            else -> items(state.observations) { obs ->
                ObservationCard(text = obs)
            }
        }

        // Averages footer
        item {
            SectionLabel(text = "Averages")
            AveragesCard(brief = brief)
        }

        item { Spacer(modifier = Modifier.height(Dimens.SpacingXxl)) }
    }
}

// ── Today hero card ───────────────────────────────────────────────────────────

@Composable
private fun TodayHeroCard(brief: InsightsBrief, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd, bgColor = BlackBoxColors.SurfaceVariant)
            .padding(Dimens.PaddingCard),
    ) {
        // Steps — hero number with gradient
        val stepBadge = stepsComparisonBadge(brief.todayStepsVsAvg)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = brief.todaySteps.formatK(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        brush = indigoTealGradient(),
                    ),
                )
                Text(
                    text = "steps today",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextTertiary,
                )
            }
            if (stepBadge != null) {
                val isPositive = brief.todayStepsVsAvg >= 1f
                Text(
                    text = stepBadge,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPositive) BlackBoxColors.Success else BlackBoxColors.Error,
                    modifier = Modifier
                        .background(
                            if (isPositive) BlackBoxColors.Success.copy(alpha = 0.12f)
                            else BlackBoxColors.Error.copy(alpha = 0.12f),
                            RoundedCornerShape(Dimens.RadiusFull),
                        )
                        .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXxs),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // Three small stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        ) {
            SmallStatCard(
                label = "Screen",
                value = formatMinutes(brief.todayScreenMinutes),
                color = BlackBoxColors.Teal,
                modifier = Modifier.weight(1f),
            )
            SmallStatCard(
                label = "Places",
                value = "${brief.todayPlacesCount}",
                color = BlackBoxColors.Rose,
                modifier = Modifier.weight(1f),
            )
            brief.todayTopApp?.let { app ->
                SmallStatCard(
                    label = "Top app",
                    value = app.displayName,
                    color = BlackBoxColors.IndigoLight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SmallStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(BlackBoxColors.SurfaceElevated, RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
    }
}

// ── App breakdown card ────────────────────────────────────────────────────────

@Composable
private fun AppBreakdownCard(apps: List<AppUsageStat>, modifier: Modifier = Modifier) {
    val totalMinutes = apps.sumOf { it.totalMinutes }.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = "Screen time today",
            style = MaterialTheme.typography.titleSmall,
            color = BlackBoxColors.TextPrimary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        apps.forEach { app ->
            val fraction = app.totalMinutes.toFloat() / totalMinutes
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = BlackBoxColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatMinutesShort(app.totalMinutes.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = BlackBoxColors.Teal,
                    )
                }
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(BlackBoxColors.SurfaceElevated, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(BlackBoxColors.Teal.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

// ── Trend cards ───────────────────────────────────────────────────────────────

@Composable
private fun StepTrendCard(trend: List<DailyStepCount>, avg: Int, modifier: Modifier = Modifier) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedDay = selectedIndex?.let { trend.getOrNull(it) }

    val subtitle = if (selectedDay != null)
        "${selectedDay.date.dayAbbrev()} · ${selectedDay.steps.formatK()}"
    else
        "avg ${avg.formatK()}/day"

    TrendCard(
        title = "Steps",
        subtitle = subtitle,
        barValues = trend.map { it.steps.toFloat() },
        barLabels = trend.map { it.date.dayAbbrev() },
        barColor = BlackBoxColors.Indigo,
        selectedBarIndex = selectedIndex,
        onBarTapped = { index -> selectedIndex = if (selectedIndex == index) null else index },
        detailContent = selectedDay?.let { day -> { StepDayDetail(day = day, avg = avg) } },
        modifier = modifier,
    )
}

@Composable
private fun ScreenTrendCard(trend: List<DailyScreenTime>, avg: Int, modifier: Modifier = Modifier) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedDay = selectedIndex?.let { trend.getOrNull(it) }

    val subtitle = if (selectedDay != null)
        "${selectedDay.date.dayAbbrev()} · ${formatMinutes(selectedDay.totalMinutes)}"
    else
        "avg ${formatMinutes(avg)}/day"

    TrendCard(
        title = "Screen time",
        subtitle = subtitle,
        barValues = trend.map { it.totalMinutes.toFloat() },
        barLabels = trend.map { it.date.dayAbbrev() },
        barColor = BlackBoxColors.Teal,
        selectedBarIndex = selectedIndex,
        onBarTapped = { index -> selectedIndex = if (selectedIndex == index) null else index },
        detailContent = selectedDay?.let { day -> { ScreenDayDetail(day = day) } },
        modifier = modifier,
    )
}

@Composable
private fun SleepTrendCard(
    trend: List<SleepSession?>,
    lastNight: SleepSession?,
    avg: Int,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedSession = selectedIndex?.let { trend.getOrNull(it) }

    val qualityLabel = lastNight?.let {
        when (it.quality) {
            SleepQuality.POOR -> "Poor"
            SleepQuality.FAIR -> "Fair"
            SleepQuality.GOOD -> "Good"
            SleepQuality.LONG -> "Long"
        }
    }
    val subtitle = when {
        selectedSession != null -> "${selectedSession.date.dayAbbrev()}  ${formatMinutes(selectedSession.durationMinutes)}"
        lastNight != null       -> "last night ${formatMinutes(lastNight.durationMinutes)} · ${qualityLabel!!}"
        avg > 0                 -> "avg ${formatMinutes(avg)}/night"
        else                    -> "no data yet"
    }

    // Compute bar labels: use the session's date if available; otherwise derive from today.
    val barLabels = trend.mapIndexed { i, session ->
        session?.date?.dayAbbrev() ?: run {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(trend.size - 1 - i))
            arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }

    val noDataIndices = trend.mapIndexedNotNull { i, s -> if (s == null) i else null }.toSet()

    TrendCard(
        title = "Sleep",
        subtitle = subtitle,
        barValues = trend.map { it?.durationMinutes?.toFloat() ?: 0f },
        barLabels = barLabels,
        barColor = BlackBoxColors.AccentScreen,
        noDataIndices = noDataIndices,
        selectedBarIndex = selectedIndex,
        onBarTapped = { index ->
            // Null-session bars are not interactive
            if (trend.getOrNull(index) != null) {
                selectedIndex = if (selectedIndex == index) null else index
            }
        },
        detailContent = selectedSession?.let { session ->
            { SleepSessionDetail(session = session) }
        },
        modifier = modifier,
    )
}

@Composable
private fun SleepSessionDetail(session: SleepSession, modifier: Modifier = Modifier) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.SurfaceElevated, RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXxs),
    ) {
        SleepDetailRow(
            label = "Bedtime",
            value = if (session.sleepStart > 0L) timeFormat.format(Date(session.sleepStart)) else "—",
        )
        SleepDetailRow(
            label = "Wake time",
            value = if (session.wakeTime > 0L) timeFormat.format(Date(session.wakeTime)) else "—",
        )
        SleepDetailRow(
            label = "Duration",
            value = formatMinutes(session.durationMinutes),
            valueColor = BlackBoxColors.TextPrimary,
        )
    }
}

@Composable
private fun SleepDetailRow(
    label: String,
    value: String,
    valueColor: Color = BlackBoxColors.AccentScreen,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

@Composable
private fun StepDayDetail(day: DailyStepCount, avg: Int, modifier: Modifier = Modifier) {
    val dateLabel = remember(day.date) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)!!
            SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(parsed)
        } catch (_: Exception) { day.date }
    }
    val vsAvgPct = if (avg > 0) ((day.steps.toFloat() / avg - 1f) * 100).toInt() else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.SurfaceElevated, RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXxs),
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
        SleepDetailRow(
            label = "Steps",
            value = day.steps.formatK(),
            valueColor = BlackBoxColors.Indigo,
        )
        if (vsAvgPct != null) {
            val sign = if (vsAvgPct >= 0) "+" else ""
            SleepDetailRow(
                label = "vs 7-day avg",
                value = "$sign${vsAvgPct}%",
                valueColor = if (vsAvgPct >= 0) BlackBoxColors.Success else BlackBoxColors.Error,
            )
        }
    }
}

@Composable
private fun ScreenDayDetail(day: DailyScreenTime, modifier: Modifier = Modifier) {
    val dateLabel = remember(day.date) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)!!
            SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(parsed)
        } catch (_: Exception) { day.date }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BlackBoxColors.SurfaceElevated, RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXxs),
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = BlackBoxColors.TextTertiary,
        )
        SleepDetailRow(
            label = "Screen time",
            value = formatMinutes(day.totalMinutes),
            valueColor = BlackBoxColors.Teal,
        )
        if (day.pickupCount > 0) {
            SleepDetailRow(
                label = "Pickups",
                value = "${day.pickupCount}",
                valueColor = BlackBoxColors.TealLight,
            )
        }
    }
}

@Composable
private fun TrendCard(
    title: String,
    subtitle: String,
    barValues: List<Float>,
    barLabels: List<String>,
    barColor: Color,
    modifier: Modifier = Modifier,
    noDataIndices: Set<Int> = emptySet(),
    selectedBarIndex: Int? = null,
    onBarTapped: ((Int) -> Unit)? = null,
    detailContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
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
                style = MaterialTheme.typography.titleSmall,
                color = BlackBoxColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = barColor,
            )
        }

        if (barValues.isEmpty()) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        } else {
            GradientBarChart(
                values = barValues,
                labels = barLabels,
                barColor = barColor,
                noDataIndices = noDataIndices,
                selectedBarIndex = selectedBarIndex,
                onBarTapped = onBarTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }

        // Optional expandable detail panel (used by SleepTrendCard)
        AnimatedVisibility(
            visible = detailContent != null,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            detailContent?.invoke()
        }
    }
}

@Composable
private fun GradientBarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    modifier: Modifier = Modifier,
    noDataIndices: Set<Int> = emptySet(),
    selectedBarIndex: Int? = null,
    onBarTapped: ((Int) -> Unit)? = null,
) {
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val gradient = barGradient(barColor)
    val dimColor = barColor.copy(alpha = 0.2f)
    val noDataColor = Color(0x33808080)  // muted gray for "no data" bars

    val density = LocalDensity.current
    val gapPx = with(density) { 4.dp.toPx() }
    var canvasWidthPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { canvasWidthPx = it.width.toFloat() }
                .then(
                    if (onBarTapped != null) {
                        Modifier.pointerInput(values.size, canvasWidthPx) {
                            detectTapGestures { offset ->
                                val count = values.size
                                if (count == 0 || canvasWidthPx <= 0f) return@detectTapGestures
                                val barWidth = (canvasWidthPx - gapPx * (count - 1)) / count
                                val index = (offset.x / (barWidth + gapPx))
                                    .toInt()
                                    .coerceIn(0, count - 1)
                                onBarTapped(index)
                            }
                        }
                    } else Modifier,
                ),
        ) {
            if (values.isEmpty()) return@Canvas
            val count = values.size
            val barWidth = (size.width - gapPx * (count - 1)) / count
            val corner = CornerRadius(4.dp.toPx())

            values.forEachIndexed { i, value ->
                val isNoData   = noDataIndices.contains(i)
                val ratio      = if (isNoData) 0.2f else value / max
                val barHeight  = (size.height * ratio).coerceAtLeast(2.dp.toPx())
                val x          = i * (barWidth + gapPx)
                val y          = size.height - barHeight
                val isSelected = selectedBarIndex == null || selectedBarIndex == i

                // Track (background bar)
                drawRoundRect(
                    color = BlackBoxColors.SurfaceElevated,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = corner,
                )
                if (isNoData) {
                    // "No data" bar — fixed 20% height, muted gray, no selection dot
                    drawRoundRect(
                        color = noDataColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner,
                    )
                } else {
                    // Gradient fill — dim unselected bars
                    drawRoundRect(
                        brush = if (isSelected) gradient
                            else Brush.verticalGradient(listOf(dimColor, dimColor)),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner,
                    )
                    // Dot indicator on selected bar
                    if (selectedBarIndex == i) {
                        drawCircle(
                            color = barColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x + barWidth / 2, y - 6.dp.toPx()),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selectedBarIndex == i) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (selectedBarIndex == i) barColor else BlackBoxColors.TextTertiary,
                )
            }
        }
    }
}

// ── Top list card ─────────────────────────────────────────────────────────────

@Composable
private fun TopListCard(
    title: String,
    accentColor: Color,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = BlackBoxColors.TextPrimary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        items.forEachIndexed { index, (name, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextTertiary,
                    modifier = Modifier.width(12.dp),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextTertiary,
                )
            }
        }
    }
}

// ── Top contacts card ─────────────────────────────────────────────────────────

/**
 * Shows the top 5 contacts by call count this week.
 * Each row displays the contact name, total calls, and total connected duration.
 */
@Composable
private fun TopContactsCard(
    contacts: List<ContactCallStat>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Text(
            text = "Top contacts",
            style = MaterialTheme.typography.titleSmall,
            color = BlackBoxColors.TextPrimary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        contacts.forEachIndexed { index, contact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextTertiary,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = BlackBoxColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${contact.totalCalls} call${if (contact.totalCalls != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BlackBoxColors.Rose,
                    )
                    if (contact.totalDurationSeconds > 0) {
                        Text(
                            text = formatCallDuration(contact.totalDurationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = BlackBoxColors.TextTertiary,
                        )
                    } else if (contact.missedCount > 0) {
                        Text(
                            text = "${contact.missedCount} missed",
                            style = MaterialTheme.typography.labelSmall,
                            color = BlackBoxColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCallDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else  -> "${s}s"
    }
}

// ── Observations ──────────────────────────────────────────────────────────────

@Composable
private fun ObservationCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "✦",
            style = MaterialTheme.typography.bodySmall.copy(color = BlackBoxColors.IndigoLight),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BlackBoxColors.TextSecondary,
        )
    }
}

@Composable
private fun ObservationsLoadingCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "obs_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "obs_alpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDotsIndicator(color = BlackBoxColors.Indigo.copy(alpha = alpha), dotSize = 6.dp)
        Text(
            text = "Analysing patterns...",
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextTertiary.copy(alpha = alpha),
        )
    }
}

// ── Averages card ─────────────────────────────────────────────────────────────

@Composable
private fun AveragesCard(brief: InsightsBrief, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .obsidianCard(cornerRadius = Dimens.RadiusMd)
            .padding(Dimens.PaddingCard),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        brief.bestStepDay?.let { best ->
            AverageRow("Best day", "${best.date.dayAbbrev()} · ${best.steps.formatK()} steps", BlackBoxColors.IndigoLight)
        }
        AverageRow("Avg steps", "${brief.avgDailySteps.formatK()} / day", BlackBoxColors.IndigoLight)
        AverageRow("Avg screen", "${formatMinutes(brief.avgDailyScreenMinutes)} / day", BlackBoxColors.TealLight)
        if (brief.avgSleepMinutes > 0) {
            AverageRow("Avg sleep", "${formatMinutes(brief.avgSleepMinutes)} / night", BlackBoxColors.AccentScreen)
        }
        if (brief.weekTopPlaces.isNotEmpty()) {
            AverageRow("Places", "${brief.weekTopPlaces.size} unique this week", BlackBoxColors.Rose)
        }
    }
}

@Composable
private fun AverageRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BlackBoxColors.TextTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BlackBoxColors.TextTertiary,
        modifier = modifier.padding(top = Dimens.SpacingXs),
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Int.formatK(): String = if (this >= 1000) "${"%.1f".format(this / 1000f)}k" else toString()

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinutesShort(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return if (h > 0) "${h}h" else "${m}m"
}

private fun stepsComparisonBadge(ratio: Float): String? {
    val pct = ((ratio - 1f) * 100).toInt()
    return when {
        pct >= 5  -> "▲ +${pct}%"
        pct <= -5 -> "▼ ${pct}%"
        else      -> null
    }
}

private fun String.dayAbbrev(): String = try {
    val cal = Calendar.getInstance()
    cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this) ?: return takeLast(2)
    arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[cal.get(Calendar.DAY_OF_WEEK) - 1]
} catch (_: Exception) { takeLast(2) }

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewBrief = InsightsBrief(
    todaySteps = 4_250,
    todayScreenMinutes = 87,
    todayPlacesCount = 2,
    todayTopApp = AppUsageStat("YouTube", 45),
    todayStepsVsAvg = 0.68f,
    weekStepTrend = listOf(8500, 6200, 12000, 9800, 7400, 5100, 4250)
        .mapIndexed { i, s -> DailyStepCount("2026-03-${3 + i}", s) },
    weekScreenTrend = listOf(180, 210, 150, 195, 240, 165, 87)
        .mapIndexed { i, m -> DailyScreenTime("2026-03-${3 + i}", m, m / 4) },
    weekTopPlaces = listOf(PlaceVisit("Dizengoff St, Tel Aviv", 5), PlaceVisit("Rothschild Blvd", 3)),
    weekTopApps = listOf(AppUsageStat("YouTube", 225), AppUsageStat("Chrome", 130), AppUsageStat("WhatsApp", 90)),
    bestStepDay = DailyStepCount("2026-03-05", 12000),
    avgDailySteps = 7_607,
    avgDailyScreenMinutes = 175,
    weekSleepTrend = listOf(420, 390, 465, 445, 380, 510, 450)
        .mapIndexed { i, m ->
            SleepSession(
                date = "2026-03-${3 + i}",
                sleepStart = 0L, wakeTime = 0L,
                durationMs = m * 60_000L, durationMinutes = m,
                quality = when { m < 360 -> SleepQuality.POOR; m < 420 -> SleepQuality.FAIR; m <= 540 -> SleepQuality.GOOD; else -> SleepQuality.LONG },
            )
        },
    lastNightSleep = SleepSession("2026-03-09", 0L, 0L, 450 * 60_000L, 450, SleepQuality.GOOD),
    avgSleepMinutes = 437,
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
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentObsLoadingPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(isLoadingData = false, brief = previewBrief, isLoadingObservations = true),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentLoadingPreview() {
    BlackBoxTheme {
        InsightsContent(state = InsightsContract.State(isLoadingData = true), onAction = {})
    }
}
