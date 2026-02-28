package com.blackbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.error_title
import blackbox.shared.generated.resources.retry
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.neonBorder
import org.jetbrains.compose.resources.stringResource

/**
 * Terminal-style full-screen error view with neon magenta border.
 *
 * Displays a [ERROR] prefix, warning icon, error title, descriptive
 * message, and an optional retry button inside a neon-bordered card.
 *
 * @param message The human-readable error message to display.
 * @param modifier Optional [Modifier] for the container.
 * @param onRetry Optional callback for the retry button. If null, the button is hidden.
 */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.PaddingScreen),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(color = BlackBoxColors.NeonMagenta, cornerRadius = 4.dp)
                .padding(Dimens.PaddingCard),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconXl),
                tint = BlackBoxColors.NeonMagenta,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLg))

            Text(
                text = "[ERROR]",
                style = MaterialTheme.typography.labelLarge,
                color = BlackBoxColors.NeonMagenta,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingXs))

            Text(
                text = stringResource(Res.string.error_title),
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextMuted,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(Dimens.SpacingXl))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.NeonMagenta,
                        contentColor = BlackBoxColors.TextOnNeon,
                    ),
                ) {
                    Text(text = stringResource(Res.string.retry))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorViewWithRetryPreview() {
    BlackBoxTheme {
        ErrorView(
            message = "Unable to load data. Please check your connection.",
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorViewNoRetryPreview() {
    BlackBoxTheme {
        ErrorView(
            message = "An unexpected error occurred.",
        )
    }
}
