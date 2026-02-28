package com.blackbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.NeonPulseIndicator

/**
 * Centered neon pulse loading indicator with a "SCANNING..." label.
 *
 * Replaces the standard circular progress indicator with a cyberpunk-styled
 * [NeonPulseIndicator] and monospace terminal-style status text.
 *
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NeonPulseIndicator(
                color = BlackBoxColors.NeonGreen,
                size = Dimens.LoadingSize,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingMd))

            Text(
                text = "SCANNING...",
                style = MaterialTheme.typography.labelMedium,
                color = BlackBoxColors.TextMuted,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorPreview() {
    BlackBoxTheme {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorLightPreview() {
    BlackBoxTheme(darkTheme = false) {
        LoadingIndicator()
    }
}
