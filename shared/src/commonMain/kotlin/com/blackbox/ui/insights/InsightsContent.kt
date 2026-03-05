package com.blackbox.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.insights_period_30d
import blackbox.shared.generated.resources.insights_period_7d
import blackbox.shared.generated.resources.insights_period_90d
import com.blackbox.domain.repository.DailyScreenTime
import com.blackbox.domain.repository.DailyStepCount
import com.blackbox.ui.common.EmptyStateView
import com.blackbox.ui.common.ErrorView
import com.blackbox.ui.common.LoadingIndicator
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure UI content for the Insights screen — cyberpunk metrics terminal.
 *
 * Offers four switchable visualizations via [InsightTab] chips:
 * - **DNA**: 12-cell strand per day encoding active and screen time.
 * - **LIFE CLOCK**: 24-hour concentric-ring radial chart.
 * - **MORNING**: Wake/sleep fingerprint with badge and pickup dots.
 * - **MOVEMENT**: Bidirectional daily bars (steps vs screen time).
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
        // Period selector chips
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)) {
            listOf(
                7 to Res.string.insights_period_7d,
                30 to Res.string.insights_period_30d,
                90 to Res.string.insights_period_90d,
            ).forEach { (days, res) ->
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

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        // Visualization tab switcher
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
        ) {
            InsightTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.selectedTab == tab,
                    onClick = { onAction(InsightsContract.Action.TabSelected(tab)) },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BlackBoxColors.ElectricCyanFaint,
                        selectedLabelColor = BlackBoxColors.ElectricCyan,
                        labelColor = BlackBoxColors.TextMuted,
                        containerColor = BlackBoxColors.SurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.selectedTab == tab,
                        selectedBorderColor = BlackBoxColors.ElectricCyan,
                        borderColor = BlackBoxColors.OutlineNeon,
                        selectedBorderWidth = 1.dp,
                        borderWidth = 1.dp,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // Content area — fills remaining space
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorView(
                message = state.error,
                onRetry = { onAction(InsightsContract.Action.Refresh) },
            )
            else -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.selectedTab) {
                    InsightTab.DNA -> DnaStripsView(state)
                    InsightTab.LIFE_CLOCK -> LifeClockView(state)
                    InsightTab.MORNING -> MorningFingerprintView(state)
                    InsightTab.MOVEMENT -> MovementVsScreenView(state)
                }
            }
        }
    }
}

// ── DNA Strips ────────────────────────────────────────────────────────────────

/**
 * Renders each day as a 12-cell horizontal strip (2 h/cell).
 *
 * Night cells are near-black, morning cells glow green proportionally to the
 * day's step count, afternoon/evening cells glow cyan proportionally to screen
 * time — creating a unique "DNA strand" fingerprint for each day.
 */
@Composable
private fun DnaStripsView(state: InsightsContract.State) {
    if (state.stepTrend.isEmpty()) {
        EmptyStateView(
            title = "NO DATA YET",
            message = "DNA profile appears after a few days of step collection.",
        )
        return
    }

    val maxSteps = state.stepTrend.maxOf { it.steps }.toFloat().coerceAtLeast(1f)
    val maxScreen = state.screenTimeTrend.maxOfOrNull { it.totalMinutes }
        ?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        // Column headers
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = Dimens.SpacingXs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "SLEEP  ·  ACTIVE  ·  SCREEN  ·  SLEEP",
                style = MaterialTheme.typography.labelSmall,
                color = BlackBoxColors.TextMuted,
            )
        }

        state.stepTrend.forEach { daySteps ->
            val screen = state.screenTimeTrend.find { it.date == daySteps.date }
            val stepsRatio = daySteps.steps / maxSteps
            val screenRatio = (screen?.totalMinutes ?: 0) / maxScreen

            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                Text(
                    text = dayAbbrev(daySteps.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                    modifier = Modifier.width(32.dp),
                )

                // 12-cell DNA strand canvas
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val gap = 2.dp.toPx()
                    val cellW = (size.width - gap * 11) / 12f
                    val corner = CornerRadius(1.5.dp.toPx())
                    for (seg in 0 until 12) {
                        drawRoundRect(
                            color = dnaSegmentColor(seg, stepsRatio, screenRatio),
                            topLeft = Offset(seg * (cellW + gap), 0f),
                            size = Size(cellW, size.height),
                            cornerRadius = corner,
                        )
                    }
                }

                Text(
                    text = if (daySteps.steps >= 1000) "${daySteps.steps / 1000}k" else "${daySteps.steps}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.NeonGreen,
                    modifier = Modifier.width(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMd)) {
            DnaLegend(BlackBoxColors.Background, "SLEEP (0–6 / 22–24)")
            DnaLegend(BlackBoxColors.NeonGreen, "ACTIVE (6–12)")
            DnaLegend(BlackBoxColors.ElectricCyan, "SCREEN (12–22)")
        }
    }
}

@Composable
private fun DnaLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (color == BlackBoxColors.Background) BlackBoxColors.SurfaceVariant else color),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
    }
}

/**
 * Returns the display color for a 2-hour DNA segment.
 *
 * @param segment Index 0–11 representing 2-hour blocks (0 = 0–2 am, 11 = 22–24).
 * @param stepsRatio Day's steps normalized to the period maximum (0–1).
 * @param screenRatio Day's screen minutes normalized to the period maximum (0–1).
 */
private fun dnaSegmentColor(segment: Int, stepsRatio: Float, screenRatio: Float): Color {
    val isNight = segment < 3 || segment == 11
    val isMorning = segment in 3..5
    val isAfternoon = segment in 6..8
    val isEvening = segment in 9..10
    return when {
        isNight -> BlackBoxColors.Background
        isMorning -> BlackBoxColors.NeonGreen.copy(alpha = 0.12f + stepsRatio * 0.88f)
        isAfternoon -> BlackBoxColors.ElectricCyan.copy(alpha = 0.10f + screenRatio * 0.80f)
        isEvening -> BlackBoxColors.ElectricCyan.copy(alpha = 0.25f + screenRatio * 0.65f)
        else -> BlackBoxColors.SurfaceVariant
    }
}

// ── Life Clock ────────────────────────────────────────────────────────────────

/**
 * 24-hour radial clock with three concentric data rings:
 * - Outer ring: awake window (wake → sleep).
 * - Middle ring: screen time arc starting from wake time.
 * - Inner ring: steps arc proportional to 10 000-step goal.
 *
 * Hour notches are cut at 0, 6, 12, and 18.
 */
@Composable
private fun LifeClockView(state: InsightsContract.State) {
    if (state.averageWakeTimeMs == null || state.averageSleepTimeMs == null) {
        EmptyStateView(
            title = "INSUFFICIENT DATA",
            message = "Life Clock needs several days of screen-state data to compute wake and sleep times.",
        )
        return
    }

    val wakeMs = state.averageWakeTimeMs
    val sleepMs = state.averageSleepTimeMs

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.PaddingScreen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "24H LIFE CLOCK",
            style = MaterialTheme.typography.labelMedium,
            color = BlackBoxColors.TextMuted,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerR = size.minDimension / 2f - 16.dp.toPx()
                val w1 = 22.dp.toPx()
                val w2 = 16.dp.toPx()
                val w3 = 12.dp.toPx()
                val gap = 8.dp.toPx()
                val r1 = outerR
                val r2 = r1 - w1 / 2f - w2 / 2f - gap
                val r3 = r2 - w2 / 2f - w3 / 2f - gap

                val wakeAngle = timeOfDayToAngle(wakeMs)
                val sleepAngle = timeOfDayToAngle(sleepMs)
                val awakeSweep = ((sleepAngle - wakeAngle + 360f) % 360f)

                // Ring 1 — sleep/wake cycle
                drawRing(center, r1, w1, BlackBoxColors.SurfaceVariant)
                drawRing(center, r1, w1, BlackBoxColors.Surface, wakeAngle, awakeSweep)

                // Ring 2 — screen time
                drawRing(center, r2, w2, BlackBoxColors.SurfaceVariant)
                val screenSweep = (state.averageScreenMinutes / 60f / 24f * 360f)
                    .coerceIn(0f, awakeSweep)
                if (screenSweep > 1f) {
                    drawRing(center, r2, w2, BlackBoxColors.ElectricCyan.copy(alpha = 0.85f), wakeAngle, screenSweep)
                }

                // Ring 3 — steps / movement
                drawRing(center, r3, w3, BlackBoxColors.SurfaceVariant)
                val stepsRatio = (state.averageSteps / 10_000f).coerceIn(0.05f, 1f)
                val stepsSweep = (stepsRatio * awakeSweep).coerceAtMost(awakeSweep)
                if (stepsSweep > 1f) {
                    drawRing(center, r3, w3, BlackBoxColors.NeonGreen.copy(alpha = 0.9f), wakeAngle, stepsSweep)
                }

                // Hour notch marks — dark cut through ring 1 at 0, 6, 12, 18
                listOf(0, 6, 12, 18).forEach { hour ->
                    val rad = (-90.0 + hour / 24.0 * 360.0) * PI / 180.0
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val inner = r1 - w1 / 2f - 3.dp.toPx()
                    val outer = r1 + w1 / 2f + 3.dp.toPx()
                    drawLine(
                        color = BlackBoxColors.Background,
                        start = Offset(center.x + cosA * inner, center.y + sinA * inner),
                        end = Offset(center.x + cosA * outer, center.y + sinA * outer),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            // Centre text overlay
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = fmtTime(wakeMs),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.NeonGreen,
                )
                Text(
                    text = "WAKE",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        // Ring legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ClockLegendItem(BlackBoxColors.Surface, "AWAKE")
            ClockLegendItem(BlackBoxColors.ElectricCyan, "SCREEN")
            ClockLegendItem(BlackBoxColors.NeonGreen, "ACTIVE")
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXl))

        // Stats strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(BlackBoxColors.OutlineNeon, 2.dp)
                .padding(Dimens.SpacingMd),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LifeClockStat("SLEEP", fmtTime(sleepMs), BlackBoxColors.TextMuted)
            LifeClockStat("SCREEN/DAY", formatMinutes(state.averageScreenMinutes), BlackBoxColors.ElectricCyan)
            LifeClockStat("AVG STEPS", "${state.averageSteps}", BlackBoxColors.NeonGreen)
        }
    }
}

@Composable
private fun ClockLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
    }
}

@Composable
private fun LifeClockStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
    }
}

// ── Morning Fingerprint ───────────────────────────────────────────────────────

/**
 * Displays wake/sleep times, a personality badge based on average wake hour,
 * and a row of phone-pickup dots (sized by daily pickup count) for the last 7 days.
 */
@Composable
private fun MorningFingerprintView(state: InsightsContract.State) {
    if (state.averageWakeTimeMs == null) {
        EmptyStateView(
            title = "NO SLEEP DATA",
            message = "Enable Screen State collector to generate Morning Fingerprint data.",
        )
        return
    }

    val wakeMs = state.averageWakeTimeMs
    val badge = wakeTimeBadge(wakeMs)
    val recentDays = state.screenTimeTrend.takeLast(7)
    val maxPickup = recentDays.maxOfOrNull { it.pickupCount }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingLg),
    ) {
        // Wake / Sleep time card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(BlackBoxColors.OutlineNeon, 2.dp)
                .padding(Dimens.PaddingCard),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "☀", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = fmtTime(wakeMs),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.NeonGreen,
                )
                Text("AVG WAKE", style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🌙", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (state.averageSleepTimeMs != null) fmtTime(state.averageSleepTimeMs) else "--:--",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.ElectricCyan,
                )
                Text("AVG SLEEP", style = MaterialTheme.typography.labelSmall, color = BlackBoxColors.TextMuted)
            }
        }

        // Personality badge
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .neonBorder(BlackBoxColors.NeonGreen, 2.dp)
                    .padding(horizontal = Dimens.SpacingMd, vertical = Dimens.SpacingXs),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.NeonGreen,
                )
            }
        }

        // Phone pickup dots
        if (recentDays.isNotEmpty()) {
            Text(
                text = "PHONE PICKUP ACTIVITY",
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.TextMuted,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs, Alignment.CenterHorizontally),
            ) {
                recentDays.forEach { day ->
                    val ratio = day.pickupCount / maxPickup
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                    ) {
                        Canvas(modifier = Modifier.size(38.dp)) {
                            val maxR = size.minDimension / 2f
                            val dotR = 4.dp.toPx() + ratio * (maxR - 4.dp.toPx())
                            // glow halo
                            drawCircle(
                                color = BlackBoxColors.ElectricCyan.copy(alpha = 0.15f + ratio * 0.20f),
                                radius = dotR * 1.35f,
                            )
                            // filled dot
                            drawCircle(
                                color = BlackBoxColors.ElectricCyan.copy(alpha = 0.25f + ratio * 0.75f),
                                radius = dotR,
                            )
                            // bright core
                            drawCircle(color = BlackBoxColors.ElectricCyan, radius = 3.dp.toPx())
                        }
                        Text(
                            text = dayAbbrev(day.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = BlackBoxColors.TextMuted,
                        )
                        Text(
                            text = "${day.pickupCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BlackBoxColors.ElectricCyan,
                        )
                    }
                }
            }

            val avgPickups = recentDays.map { it.pickupCount }.average().toInt()
            Text(
                text = "~$avgPickups PICKUPS / DAY",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextMuted,
            )
        }
    }
}

// ── Movement vs Screen ────────────────────────────────────────────────────────

/**
 * Bidirectional bar chart — one row per day.
 *
 * Steps (green) bars push rightward from the left; screen time (cyan) bars push
 * leftward from the right. The two bars "tug" from the centre divider, making
 * imbalanced days immediately obvious.
 */
@Composable
private fun MovementVsScreenView(state: InsightsContract.State) {
    if (state.stepTrend.isEmpty()) {
        EmptyStateView(
            title = "NO DATA YET",
            message = "Movement vs Screen requires several days of step and screen data.",
        )
        return
    }

    val maxSteps = state.stepTrend.maxOf { it.steps }.toFloat().coerceAtLeast(1f)
    val maxScreen = state.screenTimeTrend.maxOfOrNull { it.totalMinutes }
        ?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        // Column headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = "STEPS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = BlackBoxColors.NeonGreen,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "SCREEN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = BlackBoxColors.ElectricCyan,
                modifier = Modifier.weight(1f).padding(start = Dimens.SpacingXs),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXs))

        state.stepTrend.forEach { daySteps ->
            val screen = state.screenTimeTrend.find { it.date == daySteps.date }
            val stepsRatio = daySteps.steps / maxSteps
            val screenRatio = (screen?.totalMinutes ?: 0) / maxScreen

            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXxs),
            ) {
                Text(
                    text = dayAbbrev(daySteps.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                    modifier = Modifier.width(28.dp),
                )

                // Steps bar — fills rightward (bar aligned to divider, growing left)
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val barW = size.width * stepsRatio
                    if (barW > 0f) {
                        drawRoundRect(
                            color = BlackBoxColors.NeonGreen.copy(alpha = 0.85f),
                            topLeft = Offset(size.width - barW, 4.dp.toPx()),
                            size = Size(barW, size.height - 8.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }

                // Centre divider
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .padding(vertical = Dimens.SpacingXxs)
                        .background(BlackBoxColors.SurfaceVariant),
                )

                // Screen bar — fills leftward from divider
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val barW = size.width * screenRatio
                    if (barW > 0f) {
                        drawRoundRect(
                            color = BlackBoxColors.ElectricCyan.copy(alpha = 0.75f),
                            topLeft = Offset(0f, 4.dp.toPx()),
                            size = Size(barW, size.height - 8.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        // Summary averages strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(BlackBoxColors.OutlineNeon, 2.dp)
                .padding(Dimens.SpacingMd),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.averageSteps}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.NeonGreen,
                )
                Text(
                    text = "AVG STEPS/DAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatMinutes(state.averageScreenMinutes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = BlackBoxColors.ElectricCyan,
                )
                Text(
                    text = "AVG SCREEN/DAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlackBoxColors.TextMuted,
                )
            }
        }
    }
}

// ── DrawScope helpers ─────────────────────────────────────────────────────────

/**
 * Draws an arc (or full circle) ring centred at [center] with the given
 * [radius] and [strokeWidth].
 *
 * @param center Centre point of the ring.
 * @param radius Distance from centre to the arc mid-line.
 * @param strokeWidth Thickness of the ring stroke.
 * @param color Fill colour of the arc.
 * @param startAngle Start angle in degrees (0° = 3 o'clock, −90° = 12 o'clock).
 * @param sweepAngle Arc length in degrees, clockwise.
 */
private fun DrawScope.drawRing(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    startAngle: Float = -90f,
    sweepAngle: Float = 360f,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

// ── Formatting helpers ────────────────────────────────────────────────────────

/**
 * Converts an epoch-ms timestamp to a clock angle where midnight sits at
 * −90° (top of circle) and angles increase clockwise.
 */
private fun timeOfDayToAngle(epochMs: Long): Float {
    val dayMs = 24L * 3600 * 1000
    val timeOfDay = ((epochMs % dayMs) + dayMs) % dayMs
    return -90f + (timeOfDay.toFloat() / dayMs.toFloat()) * 360f
}

private fun fmtTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun dayAbbrev(dateStr: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.time = sdf.parse(dateStr) ?: return dateStr.takeLast(2)
    arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[cal.get(Calendar.DAY_OF_WEEK) - 1]
} catch (_: Exception) {
    dateStr.takeLast(2)
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Returns a personality badge string based on the average wake-hour extracted
 * from [wakeTimeMs].
 */
private fun wakeTimeBadge(wakeTimeMs: Long): String {
    val dayMs = 24L * 3600 * 1000
    val timeOfDay = ((wakeTimeMs % dayMs) + dayMs) % dayMs
    val hours = timeOfDay / 3_600_000L
    return when {
        hours < 6L -> "GHOST WORKER"
        hours < 7L -> "EARLY BIRD"
        hours < 9L -> "ROUTINE MASTER"
        hours < 11L -> "NIGHT OWL"
        else -> "LATE RISER"
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun InsightsContentEmptyPreview() {
    BlackBoxTheme {
        InsightsContent(state = InsightsContract.State(), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentMovementPreview() {
    val steps = listOf(8500, 6200, 12000, 9800, 7400, 5100, 10200)
    val screen = listOf(180 to 45, 210 to 52, 150 to 38, 195 to 48, 240 to 60, 165 to 41, 200 to 50)
    val dates = (0..6).map { "2026-02-${17 + it}" }
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                selectedTab = InsightTab.MOVEMENT,
                stepTrend = dates.mapIndexed { i, d -> DailyStepCount(d, steps[i]) },
                screenTimeTrend = dates.mapIndexed { i, d -> DailyScreenTime(d, screen[i].first, screen[i].second) },
                averageSteps = 8457,
                averageScreenMinutes = 191,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentDnaPreview() {
    val steps = listOf(8500, 6200, 12000, 9800, 7400, 5100, 10200)
    val screen = listOf(180 to 45, 210 to 52, 150 to 38, 195 to 48, 240 to 60, 165 to 41, 200 to 50)
    val dates = (0..6).map { "2026-02-${17 + it}" }
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                selectedTab = InsightTab.DNA,
                stepTrend = dates.mapIndexed { i, d -> DailyStepCount(d, steps[i]) },
                screenTimeTrend = dates.mapIndexed { i, d -> DailyScreenTime(d, screen[i].first, screen[i].second) },
                averageSteps = 8457,
                averageScreenMinutes = 191,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentLifeClockPreview() {
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                selectedTab = InsightTab.LIFE_CLOCK,
                averageWakeTimeMs = 7 * 3_600_000L + 23 * 60_000L,  // 07:23
                averageSleepTimeMs = 23 * 3_600_000L + 15 * 60_000L, // 23:15
                averageSteps = 8457,
                averageScreenMinutes = 191,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentMorningPreview() {
    val dates = (0..6).map { "2026-02-${17 + it}" }
    val pickups = listOf(45, 52, 38, 48, 60, 41, 50)
    BlackBoxTheme {
        InsightsContent(
            state = InsightsContract.State(
                selectedTab = InsightTab.MORNING,
                averageWakeTimeMs = 7 * 3_600_000L + 23 * 60_000L,
                averageSleepTimeMs = 23 * 3_600_000L + 15 * 60_000L,
                screenTimeTrend = dates.mapIndexed { i, d -> DailyScreenTime(d, 180, pickups[i]) },
            ),
            onAction = {},
        )
    }
}
