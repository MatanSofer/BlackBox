package com.blackbox.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BlackBox color palette.
 *
 * Aviation/flight-recorder-inspired design: deep blue primary with amber accent.
 * Dark-first design language that conveys trust, security, and depth.
 */
object BlackBoxColors {

    // ── Primary: Deep Blue ──
    val DeepBlue = Color(0xFF1A237E)
    val DeepBlueDark = Color(0xFF0D1259)
    val DeepBlueLight = Color(0xFF283593)
    val DeepBlueSurface = Color(0xFFC5CAE9)

    // ── Secondary / Accent: Amber ──
    val Amber = Color(0xFFFFB300)
    val AmberDark = Color(0xFFFF8F00)
    val AmberLight = Color(0xFFFFE082)
    val AmberSurface = Color(0xFFFFF8E1)

    // ── Backgrounds & Surfaces (Dark) ──
    val DarkBackground = Color(0xFF0D1117)
    val DarkSurface = Color(0xFF161B22)
    val DarkSurfaceVariant = Color(0xFF1E2530)
    val DarkOutline = Color(0xFF30363D)

    // ── Backgrounds & Surfaces (Light) ──
    val LightBackground = Color(0xFFFAFAFA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF0F0F0)
    val LightOutline = Color(0xFFD0D0D0)

    // ── Text ──
    val TextPrimaryDark = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8B949E)
    val TextPrimaryLight = Color(0xFF1A1A1A)
    val TextSecondaryLight = Color(0xFF666666)

    // ── Semantic ──
    val Success = Color(0xFF4CAF50)
    val Error = Color(0xFFF44336)
    val Warning = Color(0xFFFF9800)
    val OnError = Color(0xFFFFFFFF)
}
