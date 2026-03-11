package com.blackbox.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific mic button that launches voice recognition and delivers
 * the transcribed text via [onResult].
 *
 * Android: uses Android's built-in speech-recognition activity.
 * iOS: not yet implemented — renders nothing.
 *
 * @param onResult Called with the first recognition result when speech ends.
 * @param modifier Modifier applied to the button.
 */
@Composable
expect fun VoiceInputButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
)
