package com.blackbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import com.blackbox.ui.theme.BlackBoxTheme
import com.blackbox.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen error view with icon, message, and optional retry button.
 *
 * Displays a warning icon, error title, descriptive message,
 * and a retry button when [onRetry] is provided.
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
        modifier = modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconXl),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLg))

        Text(
            text = stringResource(Res.string.error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(Dimens.SpacingXl))

            Button(onClick = onRetry) {
                Text(text = stringResource(Res.string.retry))
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
