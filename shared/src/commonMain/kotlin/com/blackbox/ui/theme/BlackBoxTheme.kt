package com.blackbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Obsidian color scheme for BlackBox.
 *
 * Deep near-black backgrounds with soft indigo primary, teal secondary,
 * and rose for accent/tertiary. Always dark.
 */
private val ObsidianColorScheme = darkColorScheme(
    primary              = BlackBoxColors.Indigo,
    onPrimary            = BlackBoxColors.OnAccent,
    primaryContainer     = BlackBoxColors.IndigoDim,
    onPrimaryContainer   = BlackBoxColors.IndigoLight,
    secondary            = BlackBoxColors.Teal,
    onSecondary          = BlackBoxColors.OnAccent,
    secondaryContainer   = BlackBoxColors.TealDim,
    onSecondaryContainer = BlackBoxColors.TealLight,
    tertiary             = BlackBoxColors.Rose,
    onTertiary           = BlackBoxColors.OnAccent,
    tertiaryContainer    = BlackBoxColors.RoseDim,
    onTertiaryContainer  = BlackBoxColors.Rose,
    background           = BlackBoxColors.Background,
    onBackground         = BlackBoxColors.TextPrimary,
    surface              = BlackBoxColors.Surface,
    onSurface            = BlackBoxColors.TextPrimary,
    surfaceVariant       = BlackBoxColors.SurfaceVariant,
    onSurfaceVariant     = BlackBoxColors.TextSecondary,
    error                = BlackBoxColors.Error,
    onError              = BlackBoxColors.OnAccent,
    errorContainer       = BlackBoxColors.RoseDim,
    onErrorContainer     = BlackBoxColors.Rose,
    outline              = BlackBoxColors.Border,
    outlineVariant       = BlackBoxColors.BorderFaint,
    scrim                = Color(0xFF000000),
)

/**
 * BlackBox application theme — Obsidian aesthetic.
 *
 * Wraps Material 3 [MaterialTheme] with the Obsidian color palette,
 * clean sans-serif typography, and modern rounded shape system. Always dark.
 *
 * @param darkTheme Kept for backward compatibility with existing @Preview annotations;
 *   the Obsidian scheme is always dark regardless of this value.
 * @param content The composable content to theme.
 */
@Composable
fun BlackBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = BlackBoxTypography,
        shapes = BlackBoxShapes,
        content = content,
    )
}
