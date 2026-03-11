package com.blackbox.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blackbox.ui.theme.BlackBoxColors

/**
 * Android actual for [VoiceInputButton].
 *
 * Launches Android's built-in speech-recognition activity. While the system
 * UI is active the icon switches to [Icons.Default.MicOff] to give feedback.
 * On success the first recognised string is passed to [onResult]; if the user
 * cancels or no result is returned the callback is not invoked.
 */
@Composable
actual fun VoiceInputButton(
    onResult: (String) -> Unit,
    modifier: Modifier,
) {
    var isListening by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onResult(text)
        }
    }

    IconButton(
        onClick = {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask BlackBox anything…")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            launcher.launch(intent)
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "Listening…" else "Voice search",
            tint = if (isListening) BlackBoxColors.Rose else BlackBoxColors.TextTertiary,
        )
    }
}
