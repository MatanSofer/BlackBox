package com.blackbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Cyberpunk color scheme for BlackBox.
 *
 * Near-black backgrounds with neon green primary, electric cyan secondary,
 * and neon magenta for tertiary/error states. Always dark.
 */
private val CyberpunkColorScheme = darkColorScheme(
    primary = BlackBoxColors.NeonGreen,
    onPrimary = BlackBoxColors.TextOnNeon,
    primaryContainer = BlackBoxColors.NeonGreenFaint,
    onPrimaryContainer = BlackBoxColors.NeonGreen,
    secondary = BlackBoxColors.ElectricCyan,
    onSecondary = BlackBoxColors.TextOnNeon,
    secondaryContainer = BlackBoxColors.ElectricCyanFaint,
    onSecondaryContainer = BlackBoxColors.ElectricCyan,
    tertiary = BlackBoxColors.NeonMagenta,
    onTertiary = BlackBoxColors.TextOnNeon,
    tertiaryContainer = BlackBoxColors.NeonMagentaFaint,
    onTertiaryContainer = BlackBoxColors.NeonMagenta,
    background = BlackBoxColors.Background,
    onBackground = BlackBoxColors.TextPrimary,
    surface = BlackBoxColors.Surface,
    onSurface = BlackBoxColors.TextPrimary,
    surfaceVariant = BlackBoxColors.SurfaceVariant,
    onSurfaceVariant = BlackBoxColors.TextMuted,
    error = BlackBoxColors.NeonMagenta,
    onError = BlackBoxColors.TextOnNeon,
    errorContainer = BlackBoxColors.NeonMagentaFaint,
    onErrorContainer = BlackBoxColors.NeonMagenta,
    outline = BlackBoxColors.OutlineNeon,
    outlineVariant = BlackBoxColors.OutlineFaint,
)

/**
 * BlackBox application theme — cyberpunk aesthetic.
 *
 * Wraps Material 3 [MaterialTheme] with the cyberpunk color palette,
 * monospace typography, and sharp shape system. Always renders dark.
 *
 * @param darkTheme Kept for backward compatibility with existing @Preview annotations;
 *   the cyberpunk scheme is always dark regardless of this value.
 * @param content The composable content to theme.
 */
@Composable
fun BlackBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = BlackBoxTypography,
        shapes = BlackBoxShapes,
        content = content,
    )
}
