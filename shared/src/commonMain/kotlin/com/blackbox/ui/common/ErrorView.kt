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
import blackbox.shared.generated.resources.Res
import blackbox.shared.generated.resources.error_title
import blackbox.shared.generated.resources.retry
import com.blackbox.ui.theme.BlackBoxColors
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import com.blackbox.ui.theme.obsidianCard
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen error view with a warning icon, message, and optional retry button.
 *
 * @param message  Human-readable error description.
 * @param modifier Optional [Modifier] for the container.
 * @param onRetry  Optional retry callback. If null the button is hidden.
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
                .obsidianCard(cornerRadius = Dimens.RadiusMd, borderColor = BlackBoxColors.Error.copy(alpha = 0.4f))
                .padding(Dimens.PaddingCard),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconXl),
                tint = BlackBoxColors.Error,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLg))

            Text(
                text = stringResource(Res.string.error_title),
                style = MaterialTheme.typography.titleMedium,
                color = BlackBoxColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSm))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BlackBoxColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(Dimens.SpacingXl))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlackBoxColors.Indigo,
                        contentColor = BlackBoxColors.OnAccent,
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
            message = "Unable to load data. Please try again.",
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorViewNoRetryPreview() {
    BlackBoxTheme {
        ErrorView(message = "An unexpected error occurred.")
    }
}
