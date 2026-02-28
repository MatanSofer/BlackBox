package com.blackbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BlackBox cyberpunk color palette.
 *
 * Near-black backgrounds with neon green primary, electric cyan secondary,
 * and neon magenta tertiary/error. Dark-only design language.
 */
object BlackBoxColors {

    // ── Backgrounds & Surfaces ──
    val Background = Color(0xFF050510)
    val Surface = Color(0xFF0D0D20)
    val SurfaceVariant = Color(0xFF141428)

    // ── Primary: Neon Green ──
    val NeonGreen = Color(0xFF00FF41)
    val NeonGreenFaint = Color(0x1A00FF41)   // 10% — glow card fill

    // ── Secondary: Electric Cyan ──
    val ElectricCyan = Color(0xFF00D4FF)
    val ElectricCyanFaint = Color(0x1A00D4FF) // 10% — secondary card fill

    // ── Tertiary / Error: Neon Magenta ──
    val NeonMagenta = Color(0xFFFF0064)
    val NeonMagentaFaint = Color(0x1AFF0064)

    // ── Text ──
    val TextPrimary = Color(0xFFE0FFE8)       // green-tinted white
    val TextMuted = Color(0xFF808099)
    val TextOnNeon = Color(0xFF050510)         // dark text on neon buttons

    // ── Borders / Outlines ──
    val OutlineNeon = Color(0x3300FF41)        // 20% neon green
    val OutlineFaint = Color(0xFF1A1A35)

    // ── Semantic (kept for UiError compatibility) ──
    val Success = NeonGreen
    val Error = NeonMagenta
    val Warning = ElectricCyan
    val OnError = TextOnNeon
}
