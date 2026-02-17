package com.blackbox.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark color scheme — the default for BlackBox.
 *
 * Aviation/flight-recorder-inspired dark-first design with
 * deep blue primary and amber accent.
 */
private val DarkColorScheme = darkColorScheme(
    primary = BlackBoxColors.DeepBlue,
    onPrimary = BlackBoxColors.TextPrimaryDark,
    primaryContainer = BlackBoxColors.DeepBlueLight,
    onPrimaryContainer = BlackBoxColors.DeepBlueSurface,
    secondary = BlackBoxColors.Amber,
    onSecondary = BlackBoxColors.TextPrimaryLight,
    secondaryContainer = BlackBoxColors.AmberDark,
    onSecondaryContainer = BlackBoxColors.AmberSurface,
    tertiary = BlackBoxColors.Amber,
    onTertiary = BlackBoxColors.TextPrimaryLight,
    background = BlackBoxColors.DarkBackground,
    onBackground = BlackBoxColors.TextPrimaryDark,
    surface = BlackBoxColors.DarkSurface,
    onSurface = BlackBoxColors.TextPrimaryDark,
    surfaceVariant = BlackBoxColors.DarkSurfaceVariant,
    onSurfaceVariant = BlackBoxColors.TextSecondary,
    error = BlackBoxColors.Error,
    onError = BlackBoxColors.OnError,
    outline = BlackBoxColors.DarkOutline,
)

/**
 * Light color scheme for BlackBox.
 *
 * Maintains the deep blue + amber identity with lighter backgrounds.
 */
private val LightColorScheme = lightColorScheme(
    primary = BlackBoxColors.DeepBlue,
    onPrimary = BlackBoxColors.TextPrimaryDark,
    primaryContainer = BlackBoxColors.DeepBlueSurface,
    onPrimaryContainer = BlackBoxColors.DeepBlue,
    secondary = BlackBoxColors.Amber,
    onSecondary = BlackBoxColors.TextPrimaryLight,
    secondaryContainer = BlackBoxColors.AmberLight,
    onSecondaryContainer = BlackBoxColors.AmberDark,
    tertiary = BlackBoxColors.AmberDark,
    onTertiary = BlackBoxColors.TextPrimaryDark,
    background = BlackBoxColors.LightBackground,
    onBackground = BlackBoxColors.TextPrimaryLight,
    surface = BlackBoxColors.LightSurface,
    onSurface = BlackBoxColors.TextPrimaryLight,
    surfaceVariant = BlackBoxColors.LightSurfaceVariant,
    onSurfaceVariant = BlackBoxColors.TextSecondaryLight,
    error = BlackBoxColors.Error,
    onError = BlackBoxColors.OnError,
    outline = BlackBoxColors.LightOutline,
)

/**
 * BlackBox application theme.
 *
 * Wraps Material 3 [MaterialTheme] with the BlackBox color palette,
 * typography, and shape system. Defaults to dark theme.
 *
 * @param darkTheme Whether to use the dark color scheme (default: follows system).
 * @param content The composable content to theme.
 */
@Composable
fun BlackBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BlackBoxTypography,
        shapes = BlackBoxShapes,
        content = content,
    )
}
