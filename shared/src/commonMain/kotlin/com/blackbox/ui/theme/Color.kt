package com.blackbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BlackBox "Obsidian" color palette.
 *
 * Deep near-black backgrounds with soft indigo as primary, teal as secondary,
 * and rose as accent. Premium dark-only design language.
 */
object BlackBoxColors {

    // ── Backgrounds & Surfaces ──────────────────────────────────────────────
    val Background      = Color(0xFF0A0A0F)
    val Surface         = Color(0xFF111118)
    val SurfaceVariant  = Color(0xFF1C1C27)
    val SurfaceElevated = Color(0xFF232332)

    // ── Borders ─────────────────────────────────────────────────────────────
    val Border      = Color(0xFF252537)
    val BorderFaint = Color(0xFF1B1B28)

    // ── Primary: Soft Indigo ─────────────────────────────────────────────────
    val Indigo      = Color(0xFF6366F1)
    val IndigoLight = Color(0xFF818CF8)   // lighter variant for text on dark
    val IndigoDim   = Color(0x1F6366F1)   // ~12% — container fills

    // ── Secondary: Teal ──────────────────────────────────────────────────────
    val Teal      = Color(0xFF14B8A6)
    val TealLight = Color(0xFF2DD4BF)
    val TealDim   = Color(0x1F14B8A6)     // ~12%

    // ── Accent: Rose ─────────────────────────────────────────────────────────
    val Rose    = Color(0xFFF472B6)
    val RoseDim = Color(0x1FF472B6)       // ~12%

    // ── Text ─────────────────────────────────────────────────────────────────
    val TextPrimary   = Color(0xFFF0F0F4)
    val TextSecondary = Color(0xFF9394A5)
    val TextTertiary  = Color(0xFF52526A)

    // ── On-accent (text on colored buttons/chips) ────────────────────────────
    val OnAccent = Color(0xFFFFFFFF)

    // ── Semantic ─────────────────────────────────────────────────────────────
    val Success = Color(0xFF34D399)  // emerald
    val Error   = Color(0xFFF87171)  // soft red
    val Warning = Color(0xFFFBBF24)  // amber

    // ── Collector accent colors ───────────────────────────────────────────────
    // (used in timeline collector group cards — kept vibrant for data viz)
    val AccentLocation    = Color(0xFF6366F1)  // indigo
    val AccentActivity    = Color(0xFF14B8A6)  // teal
    val AccentWifi        = Color(0xFFFBBF24)  // amber
    val AccentConnectivity = Color(0xFFF97316) // orange
    val AccentBattery     = Color(0xFFEAB308)  // yellow
    val AccentScreen      = Color(0xFFA78BFA)  // violet
    val AccentAppUsage    = Color(0xFFF472B6)  // rose
    val AccentAudio       = Color(0xFFF87171)  // red
    val AccentBarometer   = Color(0xFF22D3EE)  // cyan
    val AccentLight       = Color(0xFF94A3B8)  // slate
    val AccentCall        = Color(0xFF4ADE80)  // green
    val AccentMedia       = Color(0xFFC084FC)  // purple
    val AccentDerived     = Color(0xFFEF4444)  // red (derived events)
}
