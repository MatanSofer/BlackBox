package com.blackbox.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized dimension values for consistent spacing and sizing.
 *
 * All dp/sp values used in the app should come from this object
 * to maintain design consistency and enable easy adjustments.
 */
object Dimens {

    // ── Spacing ──
    val SpacingXxs = 2.dp
    val SpacingXs = 4.dp
    val SpacingSm = 8.dp
    val SpacingMd = 12.dp
    val SpacingLg = 16.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp
    val SpacingXxxl = 48.dp

    // ── Padding ──
    val PaddingScreen = 16.dp
    val PaddingCard = 16.dp
    val PaddingSection = 24.dp

    // ── Icon sizes ──
    val IconSm = 16.dp
    val IconMd = 24.dp
    val IconLg = 32.dp
    val IconXl = 48.dp

    // ── Component sizes ──
    val BottomBarHeight = 80.dp
    val TopBarHeight = 64.dp
    val SearchBarHeight = 56.dp
    val ButtonHeight = 48.dp
    val ChipHeight = 36.dp

    // ── Corner radii ──
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 24.dp
    val RadiusFull = 50.dp

    // ── Elevation ──
    val ElevationNone = 0.dp
    val ElevationSm = 1.dp
    val ElevationMd = 4.dp
    val ElevationLg = 8.dp

    // ── Text sizes ──
    val TextXs = 10.sp
    val TextSm = 12.sp
    val TextMd = 14.sp
    val TextLg = 16.sp
    val TextXl = 20.sp

    // ── Divider ──
    val DividerThickness = 1.dp

    // ── Timeline specific ──
    val TimelineLineWidth = 2.dp
    val TimelineMarkerSize = 12.dp
    val TimelineCardMinHeight = 64.dp

    // ── Loading indicator ──
    val LoadingSize = 48.dp
    val LoadingSm = 24.dp

    // ── Cyberpunk neon effects ──
    val NeonBorderWidth = 1.dp
    val NeonGlowBlur = 8.dp
    val ScanLineHeight = 2.dp
    val TimelineNeonWidth = 2.dp
    val TimelineMarkerNeon = 16.dp
    val InsightBarMaxHeight = 80.dp
    val BottomBarNeonIndicatorHeight = 3.dp
}
