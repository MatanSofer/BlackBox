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
import com.blackbox.ui.theme.PulseDotsIndicator

/**
 * Centred loading indicator using three pulsing dots.
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
            PulseDotsIndicator(color = BlackBoxColors.Indigo)

            Spacer(modifier = Modifier.height(Dimens.SpacingMd))

            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodySmall,
                color = BlackBoxColors.TextTertiary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorPreview() {
    BlackBoxTheme { LoadingIndicator() }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorDarkPreview() {
    BlackBoxTheme(darkTheme = false) { LoadingIndicator() }
}
