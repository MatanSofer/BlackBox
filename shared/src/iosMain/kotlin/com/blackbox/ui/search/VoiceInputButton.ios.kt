package com.blackbox.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS stub — voice input not yet implemented on iOS. */
@Composable
actual fun VoiceInputButton(
    onResult: (String) -> Unit,
    modifier: Modifier,
) {
    // No-op: iOS voice input is a future implementation.
}
