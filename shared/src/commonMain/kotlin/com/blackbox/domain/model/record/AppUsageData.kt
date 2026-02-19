package com.blackbox.domain.model.record

/**
 * App usage data captured by the app usage collector.
 *
 * Records which app is in the foreground and for how long.
 * Never captures screen content, typed text, or in-app actions —
 * only the app identity and usage duration.
 *
 * @property foregroundApp Android package name (e.g., "com.whatsapp").
 * @property displayName Human-readable app name (e.g., "WhatsApp").
 * @property category App category for grouping.
 * @property sessionStart Epoch ms when this app came to the foreground.
 * @property sessionDurationMs How long the app was in the foreground.
 * @property isSystemApp Whether this is a pre-installed system app.
 */
data class AppUsageData(
    val foregroundApp: String,
    val displayName: String,
    val category: AppCategory = AppCategory.OTHER,
    val sessionStart: Long = 0,
    val sessionDurationMs: Long = 0,
    val isSystemApp: Boolean = false,
)

/**
 * Category classification for apps.
 */
enum class AppCategory {
    SOCIAL,
    COMMUNICATION,
    PRODUCTIVITY,
    ENTERTAINMENT,
    NEWS,
    FINANCE,
    HEALTH,
    EDUCATION,
    SHOPPING,
    TRAVEL,
    FOOD,
    PHOTOGRAPHY,
    TOOLS,
    GAMES,
    OTHER,
}
